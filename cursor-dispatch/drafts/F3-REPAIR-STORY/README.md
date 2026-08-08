# F3 — Dynamic Evidence Capture + Repair Story PDF

Capability #3 from the synthesis brief. Highest customer-facing leverage, smallest investment.
Techs capture before/during/after snapshots during diagnosis; one tap generates a
plain-language PDF the customer keeps.

---

## Files delivered

| File | Purpose |
|---|---|
| `evidence/Evidence.kt` | Room `@Entity` + `EvidenceDao` + `EvidenceType` enum |
| `evidence/EvidenceCapture.kt` | Core capture logic — `bookmarkLiveData`, `attachPhoto` |
| `evidence/PhotoCapture.kt` | Camera intent wrapper + `FileProvider` URI builder |
| `report/RepairStoryGenerator.kt` | Pulls evidence from DB, renders 3-page PDF via `PdfReportBuilder` |
| `overlay/compose/EvidenceButton.kt` | Floating FAB + animated type-selector sheet |

---

## Wire-up

### 1. AppDatabase — add entity + DAO

```kotlin
// In your existing AppDatabase.kt:
@Database(
    entities = [/* existing... */, Evidence::class],
    version = N + 1,               // bump version, add migration
)
abstract class AppDatabase : RoomDatabase() {
    // existing DAOs ...
    abstract fun evidenceDao(): EvidenceDao
}
```

Add a Room migration if you have existing data:

```kotlin
val MIGRATION_N_N1 = object : Migration(N, N + 1) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `evidence` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `ticketId` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `snapshotJson` TEXT,
                `photoUri` TEXT
            )
        """.trimIndent())
    }
}
```

---

### 2. Mount EvidenceButton in OverlayRoot

`EvidenceButton` is a standard `@Composable`. Add it at the bottom-end of whichever
pane is active during diagnosis (e.g. `DiagnosisPane`, `FullScanResultsPane`).

```kotlin
// Inside your DiagnosisPane or OverlayRoot composable:
val engineState by engineStateFlow.collectAsStateWithLifecycle()
val ticketId by viewModel.activeTicketId.collectAsStateWithLifecycle()

Box(modifier = Modifier.fillMaxSize()) {
    // ... existing pane content ...

    if (ticketId != null) {
        EvidenceButton(
            engineState = engineState,
            ticketId = ticketId!!,
            onBookmark = { type, label, state ->
                viewModel.bookmarkEvidence(type, label, state)
            },
            onCamera = { type, label ->
                viewModel.startPhotoCapture(type, label)
            },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}
```

Only show the button when `engineState.screen` is a diagnostic screen:

```kotlin
val showCapture = engineState.screen in listOf(
    ScreenKind.FullScanResults,
    ScreenKind.DtcDetail,
    ScreenKind.LiveDataView,
)
```

---

### 3. OverlayService / ViewModel — implement callbacks

```kotlin
// In OverlayViewModel (or directly in OverlayService):
fun bookmarkEvidence(type: EvidenceType, label: String, state: EngineState) {
    viewModelScope.launch(Dispatchers.IO) {
        val ticketId = activeTicketId.value ?: return@launch
        evidenceCapture.bookmarkLiveData(state, label, ticketId, type)
    }
}

fun startPhotoCapture(type: EvidenceType, label: String) {
    pendingCaptureType = type
    pendingCaptureLabel = label
    val (intent, uri) = PhotoCapture.buildCameraIntent(applicationContext)
    pendingPhotoUri = uri
    // Launch CameraCaptureActivity (already registered in AndroidManifest):
    intent.setClass(applicationContext, CameraCaptureActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    applicationContext.startActivity(intent)
}

// In your LocalBroadcastReceiver listening for ACTION_PHOTO_CAPTURED:
fun onPhotoCaptured(uri: Uri) {
    viewModelScope.launch {
        val ticketId = activeTicketId.value ?: return@launch
        evidenceCapture.capturePhotoEvidence(
            ticketId = ticketId,
            type = pendingCaptureType,
            label = pendingCaptureLabel,
            photoUri = uri,
        )
    }
}
```

---

### 4. Generate the PDF

```kotlin
// Triggered by "Generate Report" button in the ticket detail screen:
viewModelScope.launch {
    val pdfFile = repairStoryGenerator.generate(ticketId)
    // Share via FileProvider:
    val shareUri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", pdfFile)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, shareUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(shareIntent, "Share Repair Story"))
}
```

---

### 5. PdfReportBuilder contract (assumed existing)

`RepairStoryGenerator` calls these methods on `PdfReportBuilder`:

```kotlin
// report/PdfReportBuilder.kt (existing) — must expose:
fun addHeading(text: String, level: Int = 1)
fun addParagraph(text: String)
fun addTable(rows: List<List<String>>)        // first row = headers
fun addImage(uri: Uri, caption: String = "")
fun newPage()
fun build(outFile: File): File
```

If your existing builder uses different signatures, update the call-sites in
`RepairStoryGenerator` — the logic is isolated to `renderBeforePage`,
`renderFixPage`, `renderAfterPage`.

---

## Manifest permissions

Both `CAMERA` and `READ_MEDIA_IMAGES` are **already declared** in your
`AndroidManifest.xml` (confirmed from source). No new `<uses-permission>` lines needed.

For reference, the relevant existing entries are:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

Runtime permission requests for `CAMERA` and (on Android 13+) `READ_MEDIA_IMAGES`
must be requested at runtime before calling `PhotoCapture.launch()` or displaying
photos. Use `ActivityResultContracts.RequestMultiplePermissions`.

---

## FileProvider paths

`PhotoCapture` writes to `{cacheDir}/evidence_photos/` (temp) and
`EvidenceCapture.attachPhoto` moves to `{filesDir}/evidence_photos/` (permanent).
Both paths must appear in `res/xml/file_paths.xml`:

```xml
<paths>
    <!-- existing entries ... -->
    <cache-path name="evidence_cache" path="evidence_photos/" />
    <files-path name="evidence_files" path="evidence_photos/" />
    <files-path name="repair_stories" path="repair_stories/" />
</paths>
```

---

## OverlayRoot lifecycle note

`EvidenceButton` holds no lifecycle resources itself — it is pure Compose UI.
`EvidenceCapture` and `RepairStoryGenerator` should be scoped to
`OverlayService` (or a `ViewModelStoreOwner` attached to the service) so they
survive configuration changes and screen rotations.

Recommended injection point:

```kotlin
// OverlayService.kt
private val evidenceCapture by lazy {
    EvidenceCapture(applicationContext, AppDatabase.getInstance(applicationContext))
}
private val repairStoryGenerator by lazy {
    RepairStoryGenerator(applicationContext, AppDatabase.getInstance(applicationContext))
}
```
