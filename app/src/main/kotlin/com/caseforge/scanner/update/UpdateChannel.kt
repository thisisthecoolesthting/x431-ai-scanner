package com.caseforge.scanner.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateChannel(
    @SerialName("schemaVersion") val schemaVersion: Int = 1,
    @SerialName("channelLabel") val channelLabel: String = "",
    @SerialName("bundleManifestUrl") val bundleManifestUrl: String = "",
    @SerialName("apk") val apk: ApkChannel = ApkChannel(),
)

@Serializable
data class ApkChannel(
    @SerialName("releaseApiUrl") val releaseApiUrl: String = "",
    @SerialName("downloadUrl") val downloadUrl: String = "",
)

@Serializable
data class RemoteUpdateManifest(
    @SerialName("schemaVersion") val schemaVersion: Int = 1,
    @SerialName("revision") val revision: String = "",
    @SerialName("publishedAt") val publishedAt: String = "",
    @SerialName("files") val files: List<RemoteBundleFile> = emptyList(),
    @SerialName("apk") val apk: RemoteApkManifest? = null,
)

@Serializable
data class RemoteBundleFile(
    @SerialName("path") val path: String,
    @SerialName("url") val url: String? = null,
    @SerialName("sha256") val sha256: String? = null,
)

@Serializable
data class RemoteApkManifest(
    @SerialName("versionCode") val versionCode: Int = 0,
    @SerialName("versionName") val versionName: String = "",
    @SerialName("downloadUrl") val downloadUrl: String = "",
    @SerialName("sha256") val sha256: String? = null,
    @SerialName("buildSha") val buildSha: String? = null,
)
