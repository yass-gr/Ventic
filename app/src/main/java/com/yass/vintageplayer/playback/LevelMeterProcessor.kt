package com.yass.vintageplayer.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Pass-through [AudioProcessor] that taps the PCM stream to produce spectrum bands
 * and an overall level for the vintage spectrum display and VU needle.
 *
 * Audio is never modified: input bytes are copied to the output unchanged. Analysis
 * runs on the ExoPlayer audio thread with preallocated buffers only (no allocation
 * per frame); publication to [levels] is throttled to roughly 30 Hz.
 */
@OptIn(UnstableApi::class)
class LevelMeterProcessor : BaseAudioProcessor() {

    private var encoding: Int = C.ENCODING_INVALID
    private var channelCount: Int = 0
    private var sampleRate: Int = 44_100

    private val ring = FloatArray(FRAME_SIZE)
    private var ringPos = 0
    private var frameFill = 0
    private var frameSumSq = 0f
    private var samplesSinceEmit = 0

    private val window = FloatArray(FRAME_SIZE)
    private val binBand = IntArray(FRAME_SIZE / 2 + 1)
    private val fftRe = FloatArray(FRAME_SIZE)
    private val fftIm = FloatArray(FRAME_SIZE)
    private val bandEnergy = FloatArray(AudioLevels.BAND_COUNT)
    private val bandBins = IntArray(AudioLevels.BAND_COUNT)
    private val smoothed = FloatArray(AudioLevels.BAND_COUNT)
    private var smoothLevel = 0f

    private var bufA = FloatArray(AudioLevels.BAND_COUNT)
    private var bufB = FloatArray(AudioLevels.BAND_COUNT)
    private var front: FloatArray = bufA
    private var back: FloatArray = bufB

    init {
        for (i in 0 until FRAME_SIZE) {
            window[i] = (0.5 * (1.0 - cos(2.0 * PI * i / (FRAME_SIZE - 1)))).toFloat()
        }
        computeBinBands()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        encoding = inputAudioFormat.encoding
        channelCount = inputAudioFormat.channelCount
        sampleRate = if (inputAudioFormat.sampleRate > 0) inputAudioFormat.sampleRate else 44_100
        computeBinBands()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) {
            return
        }
        if (encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT) {
            tapSamples(inputBuffer)
        }
        replaceOutputBuffer(remaining).put(inputBuffer).flip()
    }

    override fun onFlush() {
        clear()
    }

    override fun onReset() {
        clear()
    }

    /** Reads samples (mixed to mono) into the ring without disturbing the buffer position. */
    private fun tapSamples(input: ByteBuffer) {
        val channels = if (channelCount > 0) channelCount else 1
        val base = input.position()
        if (encoding == C.ENCODING_PCM_16BIT) {
            val frames = input.remaining() / (2 * channels)
            for (f in 0 until frames) {
                var mono = 0f
                for (ch in 0 until channels) {
                    val idx = base + (f * channels + ch) * 2
                    val lo = input.get(idx).toInt() and 0xFF
                    val hi = input.get(idx + 1).toInt()
                    mono += (((hi shl 8) or lo).toFloat() / 32_768f)
                }
                pushSample(mono / channels)
            }
        } else {
            val frames = input.remaining() / (4 * channels)
            for (f in 0 until frames) {
                var mono = 0f
                for (ch in 0 until channels) {
                    val idx = base + (f * channels + ch) * 4
                    val b0 = input.get(idx).toInt() and 0xFF
                    val b1 = input.get(idx + 1).toInt() and 0xFF
                    val b2 = input.get(idx + 2).toInt() and 0xFF
                    val b3 = input.get(idx + 3).toInt()
                    mono += Float.fromBits(b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24))
                }
                pushSample(mono / channels)
            }
        }
    }

    private fun pushSample(sample: Float) {
        ring[ringPos] = sample
        ringPos = (ringPos + 1) and (FRAME_SIZE - 1)
        frameSumSq += sample * sample
        frameFill++
        if (frameFill == FRAME_SIZE) {
            frameFill = 0
            onFrame()
        }
    }

    private fun onFrame() {
        val rms = sqrt(frameSumSq / FRAME_SIZE)
        frameSumSq = 0f
        samplesSinceEmit += FRAME_SIZE
        if (samplesSinceEmit < sampleRate / EMIT_RATE_HZ) {
            return
        }
        samplesSinceEmit = 0
        for (i in 0 until FRAME_SIZE) {
            fftRe[i] = ring[(ringPos + i) and (FRAME_SIZE - 1)] * window[i]
            fftIm[i] = 0f
        }
        fft(fftRe, fftIm)
        bandEnergy.fill(0f)
        bandBins.fill(0)
        for (bin in 1..FRAME_SIZE / 2) {
            val band = binBand[bin]
            if (band < 0) {
                continue
            }
            val re = fftRe[bin]
            val im = fftIm[bin]
            bandEnergy[band] += re * re + im * im
            bandBins[band]++
        }
        for (b in 0 until AudioLevels.BAND_COUNT) {
            val target = if (bandBins[b] > 0) {
                toUnit(sqrt(bandEnergy[b] / bandBins[b]) * FFT_NORM)
            } else {
                0f
            }
            val current = smoothed[b]
            smoothed[b] = current + (target - current) * if (target > current) ATTACK else RELEASE
        }
        val levelTarget = toUnit(rms)
        smoothLevel += (levelTarget - smoothLevel) * if (levelTarget > smoothLevel) ATTACK else RELEASE
        smoothed.copyInto(back)
        val publish = back
        back = front
        front = publish
        levels.value = AudioLevels(publish, smoothLevel)
    }

    private fun computeBinBands() {
        val span = ln(BAND_MAX_HZ / BAND_MIN_HZ)
        for (bin in 0..FRAME_SIZE / 2) {
            val freq = bin.toDouble() * sampleRate / FRAME_SIZE
            binBand[bin] = when {
                bin == 0 || freq < BAND_MIN_HZ -> 0
                freq > BAND_MAX_HZ -> -1
                else -> (AudioLevels.BAND_COUNT * ln(freq / BAND_MIN_HZ) / span).toInt()
                    .coerceIn(0, AudioLevels.BAND_COUNT - 1)
            }
        }
    }

    private fun clear() {
        ringPos = 0
        frameFill = 0
        frameSumSq = 0f
        samplesSinceEmit = 0
        smoothed.fill(0f)
        smoothLevel = 0f
        levels.value = AudioLevels.SILENT
    }

    /** In-place radix-2 FFT over the preallocated buffers. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = FRAME_SIZE
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tempRe = re[i]
                re[i] = re[j]
                re[j] = tempRe
                val tempIm = im[i]
                im[i] = im[j]
                im[j] = tempIm
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wLenRe = cos(angle)
            val wLenIm = sin(angle)
            val half = len shr 1
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                for (k in 0 until half) {
                    val uRe = re[i + k].toDouble()
                    val uIm = im[i + k].toDouble()
                    val aRe = re[i + k + half].toDouble()
                    val aIm = im[i + k + half].toDouble()
                    val vRe = aRe * wRe - aIm * wIm
                    val vIm = aRe * wIm + aIm * wRe
                    re[i + k] = (uRe + vRe).toFloat()
                    im[i + k] = (uIm + vIm).toFloat()
                    re[i + k + half] = (uRe - vRe).toFloat()
                    im[i + k + half] = (uIm - vIm).toFloat()
                    val nextWRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    companion object {
        val levels = MutableStateFlow(AudioLevels.SILENT)

        private const val FRAME_SIZE = 1024
        private const val EMIT_RATE_HZ = 30
        private const val BAND_MIN_HZ = 60.0
        private const val BAND_MAX_HZ = 16_000.0
        private const val ATTACK = 0.6f
        private const val RELEASE = 0.15f
        private const val EPS = 1e-9f
        private const val FFT_NORM = 4f / FRAME_SIZE

        private fun toUnit(magnitude: Float): Float {
            return ((20f * log10(magnitude + EPS) + 60f) / 60f).coerceIn(0f, 1f)
        }
    }
}
