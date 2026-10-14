package com.turkceseslendirme.tts

/** Okuma için metni cümlelere bölme ve PDF'ten gelen metni toparlama. */
object Metin {
    private const val EN_UZUN = 280

    private val KISALTMA_SONU = setOf(
        "dr", "prof", "doç", "av", "gör", "vb", "vs", "örn", "bkz", "yy", "no", "s", "sf", "sn", "st",
        "cad", "mah", "tel", "çev", "haz", "m.ö", "m.s", "a.g.e",
    )
    private val CUMLE_SONU = Regex("[.!?…]+[\"'”’)\\]]*(?=\\s)|\\n\\s*\\n")
    private val ROMA = Regex("[IVXLCDM]+")

    /**
     * Metni okunacak parçalara böler; her parça metindeki konumuyla döner (okunan yeri işaretlemek için).
     * Kısaltmalardan (Dr.) ve sıra sayılarından (3. kuşak, XX. yüzyıl) sonra bölmez; çok uzun cümleleri
     * virgülden böler.
     */
    fun cumleler(metin: String, baslangic: Int = 0): List<IntRange> {
        val sonuc = mutableListOf<IntRange>()
        var bas = baslangic
        for (m in CUMLE_SONU.findAll(metin, baslangic)) {
            val son = m.range.last + 1
            if (m.value.startsWith(".") && !bolunurMu(metin, m.range.first, son)) continue
            ekle(metin, bas, son, sonuc)
            bas = son
        }
        ekle(metin, bas, metin.length, sonuc)
        return sonuc
    }

    private fun bolunurMu(metin: String, nokta: Int, son: Int): Boolean {
        var i = nokta
        while (i > 0 && !metin[i - 1].isWhitespace()) i--
        val onceki = metin.substring(i, nokta)
        val sonraki = metin.substring(son).trimStart().firstOrNull() ?: return true
        if (Normalizer.kucukHarf(onceki).trimStart('(') in KISALTMA_SONU) return false
        val sayiGibi = onceki.isNotEmpty() && (onceki.all { it.isDigit() } || ROMA.matches(onceki))
        return !(sayiGibi && sonraki.isLowerCase())
    }

    private fun ekle(metin: String, bas: Int, son: Int, sonuc: MutableList<IntRange>) {
        var b = bas
        while (b < son && metin[b].isWhitespace()) b++
        var s = son
        while (s > b && metin[s - 1].isWhitespace()) s--
        if (b >= s) return
        if (s - b <= EN_UZUN) {
            sonuc += b until s
            return
        }
        // Uzun parçayı virgül/noktalı virgülden, yoksa boşluktan böl
        var p = b
        while (s - p > EN_UZUN) {
            val pencere = metin.substring(p, p + EN_UZUN)
            val kes = maxOf(pencere.lastIndexOf(", "), pencere.lastIndexOf("; "), pencere.lastIndexOf(": "))
                .takeIf { it > EN_UZUN / 3 }?.plus(1) ?: pencere.lastIndexOf(' ').takeIf { it > 0 } ?: EN_UZUN
            ekle(metin, p, p + kes, sonuc)
            p += kes
        }
        ekle(metin, p, s, sonuc)
    }

    /** PDF'ten çıkan metni toparlar: satır sonu tirelerini birleştirir, paragraf içi satırları tek satır yapar. */
    fun pdfTemizle(ham: String): String {
        val satirlar = ham.replace("\r", "").replace("\u00AD", "").split("\n").map { it.trim() }
            .filterNot { it.isNotEmpty() && it.all { c -> c.isDigit() } } // sayfa numaraları
        val sb = StringBuilder()
        for ((i, satir) in satirlar.withIndex()) {
            if (satir.isEmpty()) {
                if (sb.isNotEmpty() && !sb.endsWith("\n\n")) sb.append("\n\n")
                continue
            }
            sb.append(satir)
            val sonraki = satirlar.getOrNull(i + 1).orEmpty()
            when {
                sonraki.isEmpty() -> sb.append("\n")
                satir.endsWith("-") && satir.length > 1 && satir[satir.length - 2].isLetter() &&
                    sonraki.first().isLowerCase() -> sb.setLength(sb.length - 1)
                else -> sb.append(" ")
            }
        }
        return sb.toString().replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
    }
}
