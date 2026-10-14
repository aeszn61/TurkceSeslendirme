package com.turkceseslendirme.tts

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Python (tts_tr) ile aynı çıktıyı verdiğimizi doğrular; beklenen.tsv Python'dan üretilir. */
class PythonUyumTest {
    private val satirlar = File(System.getProperty("beklenen")).readLines().filter { it.isNotBlank() }.map { it.split("\t") }

    @Test
    fun normalizasyon() {
        for ((metin, normal, _) in satirlar) assertEquals(normal, Normalizer.normalize(metin), metin)
    }

    @Test
    fun fonemler() {
        for ((_, normal, fonemler) in satirlar) {
            val kotlin = G2p.fonemlereCevir(normal).joinToString(" ") { if (it == " ") "|" else it }
            assertEquals(fonemler, kotlin, normal)
        }
    }
}
