package com.turkceseslendirme.demo

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.turkceseslendirme.tts.G2p
import com.turkceseslendirme.tts.Normalizer
import org.json.JSONObject
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.abs

/**
 * Piper/VITS ONNX modelini çalıştırır: metin → normalizasyon → G2P → fonem id → PCM.
 * Model girdileri Piper'ın dışa aktarımıyla aynı: input, input_lengths, scales.
 */
class Seslendirici(modelYolu: String, configJson: String) : AutoCloseable {
    private val ortam = OrtEnvironment.getEnvironment()
    private val oturum: OrtSession
    private val harita: Map<String, List<Int>>
    val ornekleme: Int
    private val gurultu: Float
    private val gurultuW: Float

    init {
        val secenekler = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
        }
        oturum = ortam.createSession(modelYolu, secenekler)
        val config = JSONObject(configJson)
        val idHaritasi = config.getJSONObject("phoneme_id_map")
        harita = idHaritasi.keys().asSequence().associateWith { k ->
            val dizi = idHaritasi.getJSONArray(k)
            List(dizi.length()) { dizi.getInt(it) }
        }
        ornekleme = config.getJSONObject("audio").getInt("sample_rate")
        val cikarim = config.optJSONObject("inference")
        gurultu = cikarim?.optDouble("noise_scale", 0.667)?.toFloat() ?: 0.667f
        gurultuW = cikarim?.optDouble("noise_w", 0.8)?.toFloat() ?: 0.8f
    }

    /** Bir cümleyi 16 bit PCM'e çevirir. [hiz] 1.0 normal; büyüdükçe hızlanır. */
    fun sentezle(cumle: String, hiz: Float): ShortArray {
        val fonemler = G2p.fonemlereCevir(Normalizer.normalize(cumle)).filter { it in harita }
        if (fonemler.none { it != " " && it.length == 1 && it[0].isLetter() }) return ShortArray(0)
        val idler = G2p.fonemIdleri(fonemler, harita)
        val girdi = OnnxTensor.createTensor(ortam, LongBuffer.wrap(idler), longArrayOf(1, idler.size.toLong()))
        val uzunluk = OnnxTensor.createTensor(ortam, LongBuffer.wrap(longArrayOf(idler.size.toLong())), longArrayOf(1))
        val olcek = OnnxTensor.createTensor(
            ortam, FloatBuffer.wrap(floatArrayOf(gurultu, 1f / hiz, gurultuW)), longArrayOf(3)
        )
        val ses = girdi.use { a ->
            uzunluk.use { b ->
                olcek.use { c ->
                    oturum.run(mapOf("input" to a, "input_lengths" to b, "scales" to c)).use { sonuc ->
                        val buf = (sonuc[0] as OnnxTensor).floatBuffer
                        FloatArray(buf.remaining()).also { buf.get(it) }
                    }
                }
            }
        }
        val tepe = ses.maxOfOrNull { abs(it) }?.coerceAtLeast(0.01f) ?: 1f
        val kazanc = 0.9f * Short.MAX_VALUE / tepe
        return ShortArray(ses.size) { (ses[it] * kazanc).toInt().coerceIn(-32768, 32767).toShort() }
    }

    override fun close() {
        oturum.close()
    }
}
