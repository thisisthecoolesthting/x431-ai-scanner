package com.caseforge.scanner.transfer



import android.content.Context

import com.caseforge.scanner.agent.discovery.DiscoveryReport

import com.caseforge.scanner.data.SettingsRepo

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.withContext



/**

 * Single entry for harvest (driver discovery sidecar) + [LanPushUploader] send.

 * Failed Shop Desk ingests are queued in SharedPreferences and retried on app resume.

 */

object HarvestUploadCoordinator {



    data class UploadOutcome(

        val shopDeskIngest: Result<Unit>? = null,

        val queuedForRetry: Boolean = false,

    )



    data class RetrySummary(

        val succeeded: Int = 0,

        val failed: Int = 0,

        val skipped: Boolean = false,

    )



    suspend fun harvestAndUpload(

        context: Context,

        settings: SettingsRepo,

        vehicleProfileId: String? = null,

        vinHint: String? = null,

        discoveryReport: DiscoveryReport? = null,

        sessionId: String? = null,

    ): UploadOutcome = withContext(Dispatchers.IO) {

        val vinResolved = vinHint?.takeIf { it.isNotBlank() } ?: settings.lastVin

        val profileId = TabletDataHarvester.resolveProfileId(context, vinResolved, vehicleProfileId)

        val batch = TabletDataHarvester.build(context, profileId, discoveryReport, settings)



        if (settings.shopDeskLanBroadcastEnabled) {

            // Phase 1 scaffold: hook point for Phase 2 LAN discovery broadcast (mDNS/UDP beacon).

            emitLanDiscoveryStub(settings)

        }



        val shopDeskPair = if (settings.shopDeskIngestEnabled) {

            postShopDeskIngest(context, settings, batch, sessionId)

        } else {

            null

        }



        val inv = VehicleDatabasePathResolver.scan()

        val zipper = VehicleDatabaseZipper(

            sourceRoot = inv.root,

            sidecarFiles = batch.asZipSidecars() +

                GoldenCaptureStorage.zipSidecarsIfPresent(context) +

                SessionEventLogger.zipSidecarsIfPresent(context),

        )

        LanPushUploader.send(context, settings, zipper)

        UploadOutcome(

            shopDeskIngest = shopDeskPair?.first,

            queuedForRetry = shopDeskPair?.second == true,

        )

    }



    /**

     * Posts to Shop Desk; on failure enqueues for retry on next app resume.

     */

    suspend fun postShopDeskIngest(

        context: Context,

        settings: SettingsRepo,

        batch: HarvestBatch,

        sessionId: String? = null,

    ): Pair<Result<Unit>, Boolean> {

        val url = settings.shopDeskIngestUrl
        if (!ShopDeskIngestClient.isSupportedEndpoint(url)) {
            return Result.failure<Unit>(
                IllegalArgumentException("Shop Desk ingest URL must start with http:// or https://"),
            ) to false
        }

        val result = ShopDeskIngestClient.postHarvest(

            url = url,

            batch = batch,

            sessionId = sessionId,

        )

        val queued = if (result.isFailure) {

            PendingShopDeskUploadQueue.enqueue(

                context = context,

                url = url,

                batch = batch,

                sessionId = sessionId,

                error = result.exceptionOrNull(),

            )

            true

        } else {

            sessionId?.takeIf { it.isNotBlank() }?.let { sid ->

                PendingShopDeskUploadQueue.list(context)

                    .filter { it.sessionId == sid }

                    .forEach { PendingShopDeskUploadQueue.remove(context, it.id) }

            }

            false

        }

        return result to queued

    }



    fun isSessionQueued(context: Context, sessionId: String): Boolean =

        PendingShopDeskUploadQueue.hasSession(context, sessionId)



    fun pendingCount(context: Context): Int = PendingShopDeskUploadQueue.count(context)



    suspend fun retryQueuedUploads(context: Context, settings: SettingsRepo): RetrySummary =

        withContext(Dispatchers.IO) {

            if (!settings.shopDeskIngestEnabled) {

                return@withContext RetrySummary(skipped = true)

            }

            val items = PendingShopDeskUploadQueue.list(context)

            if (items.isEmpty()) return@withContext RetrySummary()

            var succeeded = 0

            var failed = 0

            for (item in items) {

                val batch = HarvestBatch(item.manifest)

                val result = ShopDeskIngestClient.postHarvest(

                    url = item.url,

                    batch = batch,

                    sessionId = item.sessionId,

                )

                if (result.isSuccess) {

                    PendingShopDeskUploadQueue.remove(context, item.id)

                    succeeded++

                } else {

                    PendingShopDeskUploadQueue.updateError(

                        context,

                        item.id,

                        result.exceptionOrNull()?.message,

                    )

                    failed++

                }

            }

            RetrySummary(succeeded = succeeded, failed = failed)

        }



    private fun emitLanDiscoveryStub(settings: SettingsRepo) {

        runCatching {

            // Stub only for now. Phase 2 will advertise _caseforge-scanner._tcp and/or UDP beacons.

            settings.lastReceiverHost = settings.receiverPcHost

        }

    }

    /** Manual Settings tap: stamp prefs and run Phase 1 LAN discovery stub. */
    fun broadcastLanDiscoveryNow(settings: SettingsRepo) {
        settings.recordShopDeskLanBroadcastTap()
        emitLanDiscoveryStub(settings)
    }

}


