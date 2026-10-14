package com.turkceseslendirme.demo

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Cümleleri sırayla seslendirir: bir iş parçacığı sonraki cümleleri sentezlerken diğeri çalar,
 * böylece cümleler arasında bekleme olmaz.
 */
class Okuyucu(
    private val seslendirici: Seslendirici,
    private val cumleBasladi: (IntRange) -> Unit,
    private val bitti: (hata: Throwable?) -> Unit,
) {
    private class Parca(val aralik: IntRange, val pcm: ShortArray)

    @Volatile private var calisiyor = false
    private var uretici: Thread? = null
    private var calici: Thread? = null
    private var ses: AudioTrack? = null

    fun baslat(metin: String, cumleler: List<IntRange>, hiz: Float) {
        durdur()
        calisiyor = true
        val kuyruk = ArrayBlockingQueue<Parca>(3)
        val son = Parca(IntRange.EMPTY, ShortArray(0))
        var uretimHatasi: Throwable? = null

        uretici = thread(name = "sentez") {
            try {
                for (aralik in cumleler) {
                    if (!calisiyor) break
                    val pcm = seslendirici.sentezle(metin.substring(aralik.first, aralik.last + 1), hiz)
                    while (calisiyor && !kuyruk.offer(Parca(aralik, pcm), 100, TimeUnit.MILLISECONDS)) Unit
                }
            } catch (t: Throwable) {
                uretimHatasi = t
            } finally {
                while (calisiyor && !kuyruk.offer(son, 100, TimeUnit.MILLISECONDS)) Unit
            }
        }

        calici = thread(name = "calma") {
            val minTampon = AudioTrack.getMinBufferSize(
                seslendirici.ornekleme, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val iz = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(seslendirici.ornekleme)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minTampon, seslendirici.ornekleme / 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            ses = iz
            iz.play()
            val ara = ShortArray(seslendirici.ornekleme / 6) // cümleler arası kısa sessizlik
            try {
                while (calisiyor) {
                    val p = kuyruk.poll(100, TimeUnit.MILLISECONDS) ?: continue
                    if (p === son) break
                    cumleBasladi(p.aralik)
                    yaz(iz, p.pcm)
                    yaz(iz, ara)
                }
                if (calisiyor) {
                    yaz(iz, ShortArray(seslendirici.ornekleme / 2)) // son cümlenin sonu kesilmesin
                }
            } finally {
                iz.stop()
                iz.release()
                ses = null
                val bitirdi = calisiyor
                calisiyor = false
                if (bitirdi) bitti(uretimHatasi) else bitti(null)
            }
        }
    }

    private fun yaz(iz: AudioTrack, pcm: ShortArray) {
        var i = 0
        while (calisiyor && i < pcm.size) {
            val n = iz.write(pcm, i, minOf(4096, pcm.size - i))
            if (n <= 0) break
            i += n
        }
    }

    fun durdur() {
        calisiyor = false
        ses?.pause()
        ses?.flush()
        uretici?.join(2000)
        calici?.join(2000)
        uretici = null
        calici = null
    }

    val okuyor get() = calisiyor
}
