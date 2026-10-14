package com.turkceseslendirme.demo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.turkceseslendirme.tts.Metin
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var metinKutusu: EditText
    private lateinit var durum: TextView
    private lateinit var okuDugmesi: Button
    private lateinit var hizCubugu: SeekBar
    private lateinit var hizYazisi: TextView

    private var seslendirici: Seslendirici? = null
    private var okuyucu: Okuyucu? = null

    private val modelDosyasi by lazy { File(filesDir, "model.onnx") }
    private val metinDosyasi by lazy { File(filesDir, "metin.txt") }
    private val ayarlar by lazy { getSharedPreferences("ayarlar", MODE_PRIVATE) }

    private val hiz get() = 0.6f + hizCubugu.progress / 100f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        PDFBoxResourceLoader.init(applicationContext)

        metinKutusu = findViewById(R.id.metin)
        durum = findViewById(R.id.durum)
        okuDugmesi = findViewById(R.id.oku)
        hizCubugu = findViewById(R.id.hiz)
        hizYazisi = findViewById(R.id.hiz_yazi)

        metinKutusu.setText(if (metinDosyasi.exists()) metinDosyasi.readText() else getString(R.string.ornek_metin))
        metinKutusu.setSelection(ayarlar.getInt("imlec", 0).coerceIn(0, metinKutusu.length()))
        hizCubugu.progress = ayarlar.getInt("hiz", 40)
        hizYaz()
        hizCubugu.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, kullanici: Boolean) = hizYaz()
            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) = Unit
        })

        findViewById<Button>(R.id.pdf_ac).setOnClickListener {
            durdur()
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf"),
                ISTEK_PDF
            )
        }
        findViewById<Button>(R.id.model_sec).setOnClickListener {
            durdur()
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
                ISTEK_MODEL
            )
        }
        okuDugmesi.setOnClickListener { if (okuyucu?.okuyor == true) durdur() else oku() }

        modeliYukle()
    }

    private fun hizYaz() {
        hizYazisi.text = getString(R.string.hiz, hiz)
    }

    private fun modeliYukle() {
        if (!modelDosyasi.exists()) {
            // APK'ya gömülü model varsa onu kullan
            runCatching { assets.open("model.onnx").use { g -> modelDosyasi.outputStream().use { g.copyTo(it) } } }
                .onFailure { modelDosyasi.delete() }
        }
        if (!modelDosyasi.exists()) {
            durum.setText(R.string.model_yok)
            return
        }
        durum.setText(R.string.model_yukleniyor)
        thread {
            val sonuc = runCatching {
                Seslendirici(modelDosyasi.absolutePath, assets.open("model.json").bufferedReader().readText())
            }
            runOnUiThread {
                sonuc.onSuccess {
                    seslendirici?.close()
                    seslendirici = it
                    durum.text = getString(R.string.hazir, modelDosyasi.length() / 1_000_000)
                }.onFailure {
                    durum.text = getString(R.string.model_hatasi, it.message)
                }
            }
        }
    }

    private fun oku() {
        val s = seslendirici ?: run { durum.setText(R.string.model_yok); return }
        val metin = metinKutusu.text.toString()
        val imlec = metinKutusu.selectionStart.coerceAtLeast(0)
        val cumleler = Metin.cumleler(metin).dropWhile { it.last < imlec }
        if (cumleler.isEmpty()) {
            durum.setText(R.string.okunacak_yok)
            return
        }
        okuyucu = Okuyucu(
            s,
            cumleBasladi = { aralik ->
                runOnUiThread {
                    metinKutusu.requestFocus()
                    runCatching { metinKutusu.setSelection(aralik.first, aralik.last + 1) }
                }
            },
            bitti = { hata ->
                runOnUiThread {
                    okuDugmesi.setText(R.string.oku)
                    durum.text = if (hata != null) getString(R.string.okuma_hatasi, hata.message) else getString(R.string.bitti)
                }
            },
        ).also { it.baslat(metin, cumleler, hiz) }
        okuDugmesi.setText(R.string.durdur)
        durum.setText(R.string.okunuyor)
    }

    private fun durdur() {
        okuyucu?.durdur()
        okuyucu = null
        okuDugmesi.setText(R.string.oku)
    }

    @Deprecated("Basit demo için yeterli")
    override fun onActivityResult(istek: Int, sonuc: Int, veri: Intent?) {
        super.onActivityResult(istek, sonuc, veri)
        val uri = veri?.data ?: return
        if (sonuc != RESULT_OK) return
        when (istek) {
            ISTEK_PDF -> pdfOku(uri)
            ISTEK_MODEL -> modelKopyala(uri)
        }
    }

    private fun pdfOku(uri: Uri) {
        durum.setText(R.string.pdf_okunuyor)
        thread {
            val sonuc = runCatching {
                contentResolver.openInputStream(uri)!!.use { g ->
                    PDDocument.load(g).use { belge -> Metin.pdfTemizle(PDFTextStripper().getText(belge)) }
                }
            }
            runOnUiThread {
                sonuc.onSuccess {
                    metinKutusu.setText(it)
                    metinKutusu.setSelection(0)
                    durum.text = getString(R.string.pdf_hazir, it.length)
                }.onFailure { durum.text = getString(R.string.pdf_hatasi, it.message) }
            }
        }
    }

    private fun modelKopyala(uri: Uri) {
        durum.setText(R.string.model_yukleniyor)
        thread {
            val sonuc = runCatching {
                val gecici = File(filesDir, "model.onnx.tmp")
                contentResolver.openInputStream(uri)!!.use { g -> gecici.outputStream().use { g.copyTo(it) } }
                check(gecici.length() > 1_000_000) { "Dosya bir ONNX modeli gibi görünmüyor" }
                gecici.renameTo(modelDosyasi)
            }
            runOnUiThread {
                sonuc.onSuccess { modeliYukle() }.onFailure { durum.text = getString(R.string.model_hatasi, it.message) }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        metinDosyasi.writeText(metinKutusu.text.toString())
        ayarlar.edit().putInt("imlec", metinKutusu.selectionStart).putInt("hiz", hizCubugu.progress).apply()
    }

    override fun onDestroy() {
        durdur()
        seslendirici?.close()
        super.onDestroy()
    }

    companion object {
        private const val ISTEK_PDF = 1
        private const val ISTEK_MODEL = 2
    }
}
