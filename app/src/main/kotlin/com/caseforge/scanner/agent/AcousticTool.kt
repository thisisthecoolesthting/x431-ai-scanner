package com.caseforge.scanner.agent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

/**
 * Records audio from the tablet microphone and performs a Cooley-Tukey radix-2 FFT
 * to extract a textual acoustic summary the agent can reason over.
 *
 * Output is intentionally plain text (no JSON) so the LLM can quote it back to the
 * mechanic / use it in chain-of-thought without parsing.
 */
object AcousticTool {

    private const val SAMPLE_RATE = 16_000
    private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    @Volatile private var appContext: Context? = null

    /** Call once from Application.onCreate() so we can resolve permissions later. */
    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Record [durationMs] of audio, FFT it, return a human-readable report.
     * Returns a string starting with "ERROR:" on any failure.
     */
    suspend fun record(durationMs: Int = 6000): String = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext "ERROR: AcousticTool not attached to a Context"

        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return@withContext "ERROR: RECORD_AUDIO permission not granted"

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) return@withContext "ERROR: AudioRecord reports unsupported config"
        val bufferBytes = max(minBuf, SAMPLE_RATE * 2) // ~1s scratch

        val totalSamples = (SAMPLE_RATE.toLong() * durationMs / 1000L).toInt()
        val samples = ShortArray(totalSamples)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.UNPROCESSED.takeIf { it >= 0 }
                    ?: MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNEL, ENCODING, bufferBytes
            )
        } catch (t: Throwable) {
            return@withContext "ERROR: Could not construct AudioRecord: ${t.message}"
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@withContext "ERROR: AudioRecord failed to initialize"
        }

        try {
            recorder.startRecording()
            var read = 0
            while (read < totalSamples) {
                val n = recorder.read(samples, read, totalSamples - read)
                if (n <= 0) break
                read += n
            }
            recorder.stop()
            if (read < totalSamples / 4) {
                return@withContext "ERROR: Captured only $read of $totalSamples samples"
            }
        } catch (t: Throwable) {
            return@withContext "ERROR: Recording failed: ${t.message}"
        } finally {
            try { recorder.release() } catch (_: Throwable) {}
        }

        analyze(samples, durationMs)
    }

    // -------- Analysis --------

    private fun analyze(samples: ShortArray, durationMs: Int): String {
        // RMS (normalized to [0, 1])
        var sumSq = 0.0
        var peak = 0
        for (s in samples) {
            val v = s.toInt()
            sumSq += (v * v).toDouble()
            val a = abs(v)
            if (a > peak) peak = a
        }
        val rmsRaw = sqrt(sumSq / samples.size)
        val rms = (rmsRaw / 32768.0).coerceIn(0.0, 1.0)

        // Noise floor estimate from lowest-energy 10% of 1024-sample frames.
        val frame = 1024
        val frameCount = samples.size / frame
        val frameRms = DoubleArray(frameCount)
        for (f in 0 until frameCount) {
            var fs = 0.0
            val base = f * frame
            for (i in 0 until frame) {
                val v = samples[base + i].toInt()
                fs += (v * v).toDouble()
            }
            frameRms[f] = sqrt(fs / frame) / 32768.0
        }
        frameRms.sort()
        val floorLin = if (frameCount > 0) {
            val n = max(1, frameCount / 10)
            var s = 0.0; for (i in 0 until n) s += frameRms[i]; s / n
        } else rms
        val noiseFloorDb = if (floorLin > 0) 20.0 * log10(floorLin) else -120.0

        // FFT on the strongest 1.0s window we can find (16384 samples nearest power of 2).
        val fftSize = 16384.coerceAtMost(largestPow2AtMost(samples.size))
        val start = strongestWindowStart(samples, fftSize)
        val re = DoubleArray(fftSize)
        val im = DoubleArray(fftSize)
        for (i in 0 until fftSize) {
            // Hann window to reduce spectral leakage
            val w = 0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))
            re[i] = (samples[start + i].toDouble() / 32768.0) * w
        }
        fftRadix2(re, im)

        // Magnitude spectrum, only the first N/2 bins are unique.
        val half = fftSize / 2
        val mag = DoubleArray(half)
        for (i in 0 until half) mag[i] = sqrt(re[i] * re[i] + im[i] * im[i])

        val binHz = SAMPLE_RATE.toDouble() / fftSize

        // Spectral centroid (energy-weighted mean frequency).
        var num = 0.0; var den = 0.0
        for (i in 1 until half) { num += i * binHz * mag[i]; den += mag[i] }
        val centroid = if (den > 0) num / den else 0.0

        // Top-3 peaks with simple "must be a local max + min spacing" heuristic.
        val peaks = findPeaks(mag, binHz, topN = 3, minSpacingHz = 40.0, minBin = 2)

        // Transients: bins that stand far above the local median across short FFTs.
        val transients = detectTransients(samples)

        val sb = StringBuilder()
        sb.append("Acoustic capture: ${durationMs}ms @ ${SAMPLE_RATE / 1000}kHz mono\n")
        sb.append("RMS amplitude: ${"%.2f".format(rms)}\n")
        if (peaks.isNotEmpty()) {
            sb.append("Dominant frequencies (Hz): ")
            sb.append(peaks.mapIndexed { idx, p ->
                if (idx == 0) "${p.hz.toInt()} (primary)" else p.hz.toInt().toString()
            }.joinToString(", "))
            sb.append("\n")
        } else {
            sb.append("Dominant frequencies (Hz): none detected\n")
        }
        sb.append("Spectral centroid: ${centroid.toInt()}Hz\n")
        if (transients.isNotEmpty()) {
            val t = transients.first()
            sb.append("Notable transient peaks: ${t.hz.toInt()}Hz (${t.hits} hits)\n")
        } else {
            sb.append("Notable transient peaks: none\n")
        }
        sb.append("Noise floor: ${noiseFloorDb.toInt()}dB")
        return sb.toString()
    }

    private data class Peak(val hz: Double, val mag: Double)
    private data class Transient(val hz: Double, val hits: Int)

    private fun findPeaks(
        mag: DoubleArray, binHz: Double,
        topN: Int, minSpacingHz: Double, minBin: Int
    ): List<Peak> {
        val candidates = ArrayList<Peak>()
        for (i in (minBin + 1) until (mag.size - 1)) {
            if (mag[i] > mag[i - 1] && mag[i] >= mag[i + 1]) {
                candidates.add(Peak(i * binHz, mag[i]))
            }
        }
        candidates.sortByDescending { it.mag }
        val picked = ArrayList<Peak>()
        for (c in candidates) {
            if (picked.all { abs(it.hz - c.hz) >= minSpacingHz }) {
                picked.add(c)
                if (picked.size >= topN) break
            }
        }
        return picked
    }

    /** Slide a small FFT across the buffer, count bins that spike >6x median. */
    private fun detectTransients(samples: ShortArray): List<Transient> {
        val n = 2048
        val hop = 1024
        if (samples.size < n * 2) return emptyList()
        val binHz = SAMPLE_RATE.toDouble() / n
        val half = n / 2
        val spikeCounts = HashMap<Int, Int>()
        val re = DoubleArray(n); val im = DoubleArray(n)
        var off = 0
        while (off + n <= samples.size) {
            for (i in 0 until n) {
                val w = 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
                re[i] = (samples[off + i].toDouble() / 32768.0) * w
                im[i] = 0.0
            }
            fftRadix2(re, im)
            val mag = DoubleArray(half)
            for (i in 0 until half) mag[i] = sqrt(re[i] * re[i] + im[i] * im[i])
            val sorted = mag.copyOf().also { it.sort() }
            val median = sorted[sorted.size / 2]
            if (median > 0) {
                for (i in 4 until half) {
                    if (mag[i] > median * 6.0) {
                        // bucket to nearest 50Hz to combine neighbors
                        val bucket = ((i * binHz) / 50.0).toInt()
                        spikeCounts[bucket] = (spikeCounts[bucket] ?: 0) + 1
                    }
                }
            }
            off += hop
        }
        return spikeCounts.entries
            .sortedByDescending { it.value }
            .take(1)
            .filter { it.value >= 2 }
            .map { Transient(it.key * 50.0, it.value) }
    }

    private fun strongestWindowStart(samples: ShortArray, win: Int): Int {
        if (samples.size <= win) return 0
        val hop = win / 4
        var bestStart = 0
        var bestEnergy = -1.0
        var off = 0
        while (off + win <= samples.size) {
            var e = 0.0
            // sub-sample for speed
            var i = 0
            while (i < win) { val v = samples[off + i].toInt(); e += (v * v).toDouble(); i += 32 }
            if (e > bestEnergy) { bestEnergy = e; bestStart = off }
            off += hop
        }
        return bestStart
    }

    private fun largestPow2AtMost(n: Int): Int {
        var p = 1
        while (p * 2 <= n) p *= 2
        return p
    }

    // -------- Radix-2 iterative Cooley-Tukey FFT (in-place) --------
    private fun fftRadix2(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(n > 0 && (n and (n - 1)) == 0) { "FFT size must be power of 2, got $n" }

        // Bit-reverse permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }

        // Butterflies
        var size = 2
        while (size <= n) {
            val half = size / 2
            val theta = -2.0 * PI / size
            val wRe = cos(theta)
            val wIm = sin(theta)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val tRe = curRe * re[b] - curIm * im[b]
                    val tIm = curRe * im[b] + curIm * re[b]
                    re[b] = re[a] - tRe
                    im[b] = im[a] - tIm
                    re[a] = re[a] + tRe
                    im[a] = im[a] + tIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                i += size
            }
            size = size shl 1
        }
        // Suppress unused warning for ln import if any
        @Suppress("UNUSED_EXPRESSION") ln(1.0)
    }
}
