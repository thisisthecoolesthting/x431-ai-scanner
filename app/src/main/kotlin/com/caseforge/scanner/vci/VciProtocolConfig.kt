package com.caseforge.scanner.vci

import com.caseforge.scanner.data.SettingsRepo

/**
 * Runtime wire-format settings (header magic + hex transport) shared by the socket client,
 * frame builder, and protocol probe UI. Persisted via [SettingsRepo].
 */
object VciProtocolConfig {

  /** Header bytes used by [VciFrame.build]; updated from settings or probe sweep. */
  @Volatile
  var header: ByteArray = VciFrame.DEFAULT_HEADER.copyOf()

  val HEADER_CANDIDATES: List<ByteArray> = listOf(
    byteArrayOf(0x55.toByte(), 0xAA.toByte()),
    byteArrayOf(0xAA.toByte(), 0x55.toByte()),
    byteArrayOf(0xFE.toByte(), 0x01.toByte()),
    byteArrayOf(0x40.toByte(), 0xC8.toByte()),
  )

  fun headerLabel(bytes: ByteArray): String =
    bytes.joinToString(" ") { "0x%02X".format(it.toInt() and 0xFF) }

  fun applyFromSettings(settings: SettingsRepo) {
    header = byteArrayOf(
      settings.vciHeaderByte0.toByte(),
      settings.vciHeaderByte1.toByte(),
    )
  }

  fun persistToSettings(settings: SettingsRepo, confirmedHeader: ByteArray, useHex: Boolean) {
    settings.vciHeaderByte0 = confirmedHeader[0].toInt() and 0xFF
    settings.vciHeaderByte1 = confirmedHeader[1].toInt() and 0xFF
    settings.vciUseHexEncoding = useHex
    settings.vciProtocolConfirmed = true
    applyFromSettings(settings)
  }
}
