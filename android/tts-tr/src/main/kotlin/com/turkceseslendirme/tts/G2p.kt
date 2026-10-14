package com.turkceseslendirme.tts

import java.util.Locale

/**
 * Kural tabanlı Türkçe G2P (harf → IPA), espeak-ng kullanmadan.
 *
 * `tts_tr/g2p.py` dosyasının birebir karşılığıdır; model bu G2P'nin çıktısıyla eğitildiği için
 * ikisinin aynı fonemleri üretmesi şarttır (`tests/beklenen.tsv` ile test edilir).
 */
object G2p {
    private const val INCE = "eiöüîê"
    private const val KALIN = "aıouâû"
    private const val UNLULER = INCE + KALIN

    private val HARF = mapOf(
        'a' to "a", 'b' to "b", 'c' to "dʒ", 'ç' to "tʃ", 'd' to "d", 'e' to "e", 'f' to "f",
        'h' to "h", 'ı' to "ɯ", 'i' to "i", 'j' to "ʒ", 'm' to "m", 'n' to "n", 'o' to "o",
        'ö' to "ø", 'p' to "p", 'r' to "ɾ", 's' to "s", 'ş' to "ʃ", 't' to "t", 'u' to "u",
        'ü' to "y", 'v' to "v", 'y' to "j", 'z' to "z", 'q' to "k", 'w' to "v", 'x' to "ks",
        'â' to "aː", 'î' to "iː", 'û' to "uː", 'ê' to "eː",
    )

    private val VURGUSUZ = setOf(
        "mı", "mi", "mu", "mü", "mısın", "misin", "musun", "müsün", "mıyım", "miyim",
        "muyum", "müyüm", "mıdır", "midir", "mudur", "müdür", "de", "da", "ki", "ve", "ile",
    )

    private val VURGU_ISTISNA = mapOf(
        "ankara" to 0, "istanbul" to 1, "izmir" to 0, "antalya" to 1, "türkiye" to 0, "avrupa" to 1,
        "almanya" to 1, "amerika" to 1, "londra" to 0, "paris" to 0, "adana" to 0, "bursa" to 0,
        "konya" to 0, "trabzon" to 0, "kayseri" to 0, "samsun" to 0, "erzurum" to 0, "eskişehir" to 0,
        "şimdi" to 0, "sonra" to 0, "önce" to 0, "belki" to 0, "yalnız" to 0, "ancak" to 0, "nasıl" to 0,
        "nerede" to 0, "nereye" to 0, "neden" to 0, "niçin" to 0, "hangi" to 0, "bazen" to 0, "çünkü" to 0,
        "yarın" to 0, "dün" to 0, "bugün" to 0, "akşam" to 0, "sabah" to 0, "elbette" to 1, "lütfen" to 0,
        "evet" to 0, "hayır" to 0, "merhaba" to 0, "tamam" to 1, "efendim" to 0, "işte" to 0, "hemen" to 0,
        "aslında" to 0, "galiba" to 1, "gerçekten" to 0, "maalesef" to 1, "örneğin" to 0, "yani" to 0,
        "fakat" to 0, "lakin" to 0, "ama" to 0, "oysa" to 0, "sanki" to 0, "hatta" to 0, "henüz" to 0,
    )

    private val NOKTALAMA = mapOf(
        "," to ",", "." to ".", "?" to "?", "!" to "!", ";" to ";", ":" to ":",
        "…" to ".", "-" to ",", "–" to ",", "—" to ",", "(" to ",", ")" to ",",
    )
    private val NOKTALAMA_DEGERLERI = NOKTALAMA.values.toSet()
    private val PARCA = Regex("[a-zçğıöşüâîûêqwx']+|[,.?!;:…\\-–—()]")

    fun kucult(metin: String) = metin.replace("I", "ı").replace("İ", "i").lowercase(Locale.ROOT)

    private fun baglamInce(kelime: String, i: Int): Boolean {
        if (i + 1 < kelime.length && kelime[i + 1] in UNLULER) return kelime[i + 1] in INCE
        for (j in i - 1 downTo 0) if (kelime[j] in UNLULER) return kelime[j] in INCE
        for (j in i + 1 until kelime.length) if (kelime[j] in UNLULER) return kelime[j] in INCE
        return false
    }

    /** Her harf için fonem listesi (ğ gibi sessiz harfler boş liste). */
    private fun kelimeFonemleri(kelime: String): MutableList<MutableList<String>> {
        val sonuc = mutableListOf<MutableList<String>>()
        kelime.forEachIndexed { i, h ->
            when {
                h == 'k' || h == 'g' -> {
                    var ince = baglamInce(kelime, i)
                    if (i + 1 < kelime.length && kelime[i + 1] == 'â') ince = true
                    val f = if (ince) (if (h == 'k') "c" else "ɟ") else (if (h == 'k') "k" else "ɡ")
                    sonuc += mutableListOf(f)
                }
                h == 'l' -> {
                    val ince = baglamInce(kelime, i) || (i + 1 < kelime.length && kelime[i + 1] == 'â')
                    sonuc += mutableListOf(if (ince) "l" else "ɫ")
                }
                h == 'ğ' -> {
                    val onceki = if (i > 0) kelime[i - 1] else ' '
                    val sonraki = if (i + 1 < kelime.length) kelime[i + 1] else ' '
                    val sonDolu = sonuc.isNotEmpty() && sonuc.last().isNotEmpty()
                    if (onceki in INCE && sonraki in UNLULER) {
                        sonuc += mutableListOf("j")
                    } else if (onceki in UNLULER && sonraki !in UNLULER && sonDolu && "ː" !in sonuc.last().last()) {
                        val son = sonuc.last()
                        son[son.size - 1] = son.last() + "ː"
                        sonuc += mutableListOf<String>()
                    } else {
                        sonuc += mutableListOf<String>()
                    }
                }
                h in HARF -> {
                    val f = HARF.getValue(h)
                    sonuc += if (h in "cçx") f.map { it.toString() }.toMutableList() else mutableListOf(f)
                }
                else -> sonuc += mutableListOf<String>()
            }
        }
        return sonuc
    }

    private fun vurguluUnlu(kelime: String, kokUzunlugu: Int): Int? {
        val unluler = kelime.indices.filter { kelime[it] in UNLULER }
        if (unluler.isEmpty()) return null
        val kok = kelime.substring(0, kokUzunlugu)
        val kokUnluler = unluler.filter { it < kokUzunlugu }
        val istisna = VURGU_ISTISNA[kok]
        if (istisna != null && kokUnluler.isNotEmpty()) return kokUnluler[minOf(istisna, kokUnluler.size - 1)]
        if (kokUzunlugu < kelime.length && kokUnluler.isNotEmpty()) return kokUnluler.last()
        return unluler.last()
    }

    private fun kelimeIpa(ham0: String): List<String> {
        var ham = ham0
        var kokUzunlugu = ham.length
        if ('\'' in ham) {
            kokUzunlugu = ham.indexOf('\'')
            ham = ham.replace("'", "")
        }
        val kelime = ham
        if (kelime.isEmpty()) return emptyList()
        val harfFonemleri = kelimeFonemleri(kelime)
        val vurgu = if (kelime in VURGUSUZ) null else vurguluUnlu(kelime, kokUzunlugu)
        var vurguYeri = -1
        if (vurgu != null && kelime.count { it in UNLULER } < 2 && kelime !in VURGU_ISTISNA) {
            vurguYeri = 0
        } else if (vurgu != null) {
            vurguYeri = if (vurgu > 0 && kelime[vurgu - 1] !in UNLULER) vurgu - 1 else vurgu
            if (harfFonemleri[vurguYeri].isEmpty()) vurguYeri = vurgu
        }
        val fonemler = mutableListOf<String>()
        harfFonemleri.forEachIndexed { i, f ->
            if (vurgu != null && i == vurguYeri) fonemler += "ˈ"
            for (p in f) {
                if (p.endsWith("ː")) fonemler += p.map { it.toString() } else fonemler += p
            }
        }
        return fonemler
    }

    /** Normalize edilmiş Türkçe metni IPA fonem listesine çevirir (boşluk ve noktalama dahil). */
    fun fonemlereCevir(metin0: String): List<String> {
        val metin = kucult(metin0).replace("’", "'").replace("`", "'")
        val fonemler = mutableListOf<String>()
        for (eslesme in PARCA.findAll(metin)) {
            val p = eslesme.value
            val nok = NOKTALAMA[p]
            if (nok != null) {
                if (fonemler.isNotEmpty() && fonemler.last() == " ") fonemler.removeAt(fonemler.size - 1)
                if (fonemler.isNotEmpty() && fonemler.last() in NOKTALAMA_DEGERLERI) continue
                fonemler += nok
                fonemler += " "
                continue
            }
            val ipa = kelimeIpa(p.trim('\''))
            if (ipa.isNotEmpty()) {
                fonemler += ipa
                fonemler += " "
            }
        }
        while (fonemler.isNotEmpty() && fonemler.last() == " ") fonemler.removeAt(fonemler.size - 1)
        return fonemler
    }

    /** Piper biçiminde id dizisi: ^ _ f1 _ f2 _ ... $ (her fonemden sonra dolgu). */
    fun fonemIdleri(fonemler: List<String>, harita: Map<String, List<Int>>): LongArray {
        val dolgu = harita.getValue("_")
        val idler = mutableListOf<Int>()
        idler += harita.getValue("^")
        idler += dolgu
        for (f in fonemler) {
            idler += harita[f] ?: throw IllegalArgumentException("Bilinmeyen fonem: $f")
            idler += dolgu
        }
        idler += harita.getValue("$")
        return LongArray(idler.size) { idler[it].toLong() }
    }
}
