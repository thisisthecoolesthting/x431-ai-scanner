@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caseforge.scanner.ui.main

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.caseforge.scanner.R
import com.caseforge.scanner.vin.VinCameraScanActivity
import com.caseforge.scanner.vin.VinNormalizer

/**
 * Manual VIN entry, camera OCR scan, and recalls integration hooks.
 * Offline-safe: validation is local; NHTSA fetch is a placeholder until wired.
 */
@Composable
fun RecallsScreen(
    initialVin: String?,
    onBack: () -> Unit,
    onUseVinForRecalls: (String) -> Unit = {},
    onUseVinForSession: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var draftVin by remember(initialVin) {
        mutableStateOf(initialVin?.let { VinNormalizer.normalizeOcrText(it) }.orEmpty())
    }
    var activeRecallsVin by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun revalidate() {
        validationError = vinValidationError(draftVin)
    }

    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scanned = VinCameraScanActivity.parseResult(result.data) ?: return@rememberLauncherForActivityResult
        draftVin = VinNormalizer.normalizeOcrText(scanned.vin)
        revalidate()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.recalls_screen_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.recalls_vin_entry_heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.recalls_vin_entry_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = draftVin,
                        onValueChange = { raw ->
                            draftVin = VinNormalizer.normalizeOcrText(raw).take(VinNormalizer.VIN_LENGTH)
                            revalidate()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.recalls_vin_label)) },
                        placeholder = { Text(stringResource(R.string.recalls_vin_placeholder)) },
                        isError = validationError != null,
                        supportingText = validationError?.let { err ->
                            { Text(err, color = MaterialTheme.colorScheme.error) }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { revalidate() }),
                    )
                    OutlinedButton(
                        onClick = {
                            scanLauncher.launch(VinCameraScanActivity.createIntent(context))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Text(
                            stringResource(R.string.recalls_scan_vin_button),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    val normalized = draftVin.takeIf { it.isNotBlank() }
                    val isValid = normalized != null && validationError == null && VinNormalizer.isValidVin(normalized)
                    Button(
                        onClick = {
                            val vin = normalized ?: return@Button
                            activeRecallsVin = vin
                            onUseVinForRecalls(vin)
                        },
                        enabled = isValid,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.recalls_use_for_recalls))
                    }
                    OutlinedButton(
                        onClick = {
                            val vin = normalized ?: return@OutlinedButton
                            onUseVinForSession(vin)
                        },
                        enabled = isValid,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.recalls_use_for_session))
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.recalls_lookup_heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val displayVin = activeRecallsVin ?: initialVin?.let { VinNormalizer.normalizeOcrText(it) }
                        ?.takeIf { it.isNotBlank() }
                    if (displayVin.isNullOrBlank()) {
                        Text(
                            stringResource(R.string.recalls_lookup_empty),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            stringResource(R.string.recalls_lookup_vin, displayVin),
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        )
                        Text(
                            stringResource(R.string.recalls_lookup_offline_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Returns a user-visible error, or null when [raw] is empty or fully valid. */
internal fun vinValidationError(raw: String): String? {
    val vin = VinNormalizer.normalizeOcrText(raw)
    if (vin.isEmpty()) return null
    if (vin.length < VinNormalizer.VIN_LENGTH) {
        return "Enter all ${VinNormalizer.VIN_LENGTH} characters (${vin.length} so far)."
    }
    if (vin.length > VinNormalizer.VIN_LENGTH) {
        return "VIN must be exactly ${VinNormalizer.VIN_LENGTH} characters."
    }
    if (VinNormalizer.containsInvalidLetters(vin)) {
        return "VIN cannot contain I, O, or Q."
    }
    if (!VinNormalizer.hasValidCharset(vin)) {
        return "VIN uses characters that are not allowed."
    }
    if (!VinNormalizer.validateCheckDigit(vin)) {
        return "Check digit (position 9) does not match — re-read the label or plate."
    }
    return null
}
