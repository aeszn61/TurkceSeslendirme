package com.turkceseslendirme.tts

import java.util.Locale

/**
 * Türkçe metin normalizasyonu: rakam, tarih, saat, yüzde, para, kısaltma → okunacak yazı.
 *
 * `tts_tr/normalize.py` dosyasının birebir karşılığıdır; ikisi `tests/beklenen.tsv` ile aynı
 * çıktıyı verdiği test edilir. Bir kuralı değiştirirken ikisini birlikte değiştir.
 */
object Normalizer {
    private val BIRLER = listOf("", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz")
    private val ONLAR = listOf("", "on", "yirmi", "otuz", "kırk", "elli", "altmış", "yetmiş", "seksen", "doksan")
    private val BUYUKLER = listOf(
        1_000_000_000_000L to "trilyon", 1_000_000_000L to "milyar", 1_000_000L to "milyon", 1000L to "bin"
    )
    private val AYLAR = listOf(
        "", "ocak", "şubat", "mart", "nisan", "mayıs", "haziran", "temmuz", "ağustos",
        "eylül", "ekim", "kasım", "aralık"
    )

    private val KISALTMALAR: Map<String, String> = run {
        val temel = linkedMapOf(
            "Prof. Dr." to "profesör doktor", "Doç. Dr." to "doçent doktor", "Dr." to "doktor", "Prof." to "profesör",
            "Doç." to "doçent", "Av." to "avukat", "Öğr. Gör." to "öğretim görevlisi", "Arş. Gör." to "araştırma görevlisi",
            "vb." to "ve benzeri", "vs." to "vesaire", "örn." to "örneğin", "bkz." to "bakınız", "Bkz." to "bakınız",
            "yy." to "yüzyıl", "M.Ö." to "milattan önce", "M.S." to "milattan sonra", "No." to "numara", "no." to "numara",
            "s." to "sayfa", "sf." to "sayfa", "Sn." to "sayın", "St." to "sokak", "Cad." to "cadde", "Mah." to "mahalle",
            "Tel." to "telefon", "çev." to "çeviren", "haz." to "hazırlayan", "a.g.e." to "adı geçen eser",
            "km" to "kilometre", "kg" to "kilogram", "cm" to "santimetre", "mm" to "milimetre", "gr" to "gram",
            "lt" to "litre", "m²" to "metrekare", "km²" to "kilometrekare", "°C" to "derece", "TL" to "lira",
        )
        // Cümle başında büyük harfle yazılmış hâlleri de (Örn., Vb.)
        val buyuk = temel.filter { (k, _) -> k[0].isLowerCase() && k.length > 2 && k.endsWith(".") }
            .mapKeys { (k, _) -> k[0].uppercaseChar() + k.substring(1) }
        temel + buyuk
    }

    private val KELIME_KISALTMA = setOf(
        "NATO", "ASELSAN", "TÜBİTAK", "TOKİ", "UNESCO", "UNICEF", "NASA", "TÜSİAD",
        "ODTÜ", "İTÜ", "YÖK", "ÖSYM", "AKUT", "TEKEL", "SEKA", "İSKİ", "ASKİ", "BOTAŞ",
        "VE", "BU", "ŞU", "AN", "EN", "DA", "DE", "Kİ", "NE", "HER", "BİR", "ÇOK", "YOK", "VAR",
        "SON", "İLK", "TÜM", "HİÇ", "ASLA", "SEN", "BEN", "BİZ", "SİZ", "ONU", "HEM", "YA", "GÜN",
    )
    private val HARF_ADI = mapOf(
        'A' to "a", 'B' to "be", 'C' to "ce", 'Ç' to "çe", 'D' to "de", 'E' to "e", 'F' to "fe", 'G' to "ge",
        'Ğ' to "yumuşak ge", 'H' to "he", 'I' to "ı", 'İ' to "i", 'J' to "je", 'K' to "ke", 'L' to "le", 'M' to "me",
        'N' to "ne", 'O' to "o", 'Ö' to "ö", 'P' to "pe", 'Q' to "kü", 'R' to "re", 'S' to "se", 'Ş' to "şe", 'T' to "te",
        'U' to "u", 'Ü' to "ü", 'V' to "ve", 'W' to "dabılyu", 'X' to "iks", 'Y' to "ye", 'Z' to "ze",
    )
    private val SEMBOLLER = linkedMapOf(
        "&" to " ve ", "+" to " artı ", "=" to " eşittir ", "§" to " madde ", "@" to " et ", "°" to " derece "
    )
    private val ROMA = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
    private val PARA = mapOf("₺" to "lira", "$" to "dolar", "€" to "avro", "£" to "sterlin")

    private const val UNLULER = "aeıioöuüâîû"
    private const val SERT = "fstkçşhp"
    private const val EK = "(?:'([a-zçğıöşüâîû]+))?"
    private const val HARFLER = "\\wçğıöşüÇĞİÖŞÜ"

    // (?U) masaüstü JVM'de \w ve \b'yi Unicode yapar; Android'in ICU regex'i bu bayrağı tanımaz ama
    // orada \w ve \b zaten Unicode'dur.
    private val UNICODE_BAYRAK = runCatching { Regex("(?U)a") }.isSuccess
    private fun rx(desen: String) = if (UNICODE_BAYRAK) Regex("(?U)$desen") else Regex(desen)

    fun sayiOku(n: Long): String {
        if (n == 0L) return "sıfır"
        if (n < 0) return "eksi " + sayiOku(-n)
        var k = n
        val kelimeler = mutableListOf<String>()
        for ((deger, ad) in BUYUKLER) {
            if (k >= deger) {
                val kat = k / deger
                k %= deger
                if (!(kat == 1L && ad == "bin")) kelimeler += sayiOku(kat)
                kelimeler += ad
            }
        }
        if (k >= 100) {
            val yuz = (k / 100).toInt()
            k %= 100
            if (yuz > 1) kelimeler += BIRLER[yuz]
            kelimeler += "yüz"
        }
        if (k >= 10) {
            kelimeler += ONLAR[(k / 10).toInt()]
            k %= 10
        }
        if (k > 0) kelimeler += BIRLER[k.toInt()]
        return kelimeler.joinToString(" ")
    }

    /** Rakam dizisini okur; 15 basamaktan uzunsa rakam rakam. */
    private fun rakamOku(rakamlar: String): String {
        val r = rakamlar.trimStart('0')
        if (r.isEmpty()) return "sıfır"
        if (r.length > 15) return r.map { sayiOku((it - '0').toLong()) }.joinToString(" ")
        return sayiOku(r.toLong())
    }

    private fun sonUnlu(kelime: String): Char = kelime.lastOrNull { it in UNLULER } ?: 'e'

    private fun darUnlu(kelime: String): Char = when (sonUnlu(kelime)) {
        'a', 'ı', 'â' -> 'ı'
        'e', 'i', 'î' -> 'i'
        'o', 'u', 'û' -> 'u'
        else -> 'ü'
    }

    private fun yumusat(kelime: String) = if (kelime.endsWith("dört")) kelime.dropLast(1) + "d" else kelime

    fun siraOku(n: Long): String = siraYazi(sayiOku(n))

    private fun siraYazi(sayiYazisi: String): String {
        var yazi = sayiYazisi
        val son = yazi.split(" ").last()
        val v = darUnlu(son)
        val ek = if (son.last() in UNLULER) "nc$v" else {
            yazi = yumusat(yazi)
            "${v}nc$v"
        }
        return yazi + ek
    }

    /** Eki köke göre yeniden uyumlar: lira + ye → ya, üç + da → te. */
    fun ekUyumla(kok: String, ekHam: String): String {
        if (ekHam.isEmpty()) return ekHam
        var ek = ekHam
        val kokUnluBitis = kok.last() in UNLULER
        if (!kokUnluBitis && ek.length > 1 && ek[0] in "yns" && ek[1] in UNLULER) {
            ek = ek.substring(1)
        } else if (kokUnluBitis && ek[0] in UNLULER) {
            ek = (if (ek in setOf("ın", "in", "un", "ün")) "n" else "y") + ek
        }
        val sonuc = StringBuilder()
        var onceki = sonUnlu(kok)
        ek.forEachIndexed { i, h0 ->
            var h = h0
            when {
                h in "ae" -> h = if (onceki in "eiöüî") 'e' else 'a'
                h in "ıiuü" -> h = darUnlu(onceki.toString())
                i == 0 && h in "dt" -> h = if (kok.last() in SERT) 't' else 'd'
                i == 0 && h in "cç" -> h = if (kok.last() in SERT) 'ç' else 'c'
            }
            if (h in UNLULER) onceki = h
            sonuc.append(h)
        }
        return sonuc.toString()
    }

    private fun ekEkle(yaziHam: String, ekHam: String): String {
        val ek = ekUyumla(yaziHam, ekHam)
        val yazi = if (ek.isNotEmpty() && ek[0] in UNLULER) yumusat(yaziHam) else yaziHam
        return yazi + ek
    }

    private fun romaDegeri(s: String): Long {
        var toplam = 0L
        s.forEachIndexed { i, h ->
            val d = ROMA.getValue(h)
            toplam += if (i + 1 < s.length && ROMA.getValue(s[i + 1]) > d) -d else d
        }
        return toplam
    }

    private fun ondalikOku(tam: String, kesir: String): String {
        val sifirlar = List(kesir.length - kesir.trimStart('0').length) { "sıfır" }
        val geri = if (kesir.trim('0').isNotEmpty()) listOf(rakamOku(kesir)) else emptyList()
        return rakamOku(tam) + " virgül " + (sifirlar + geri).joinToString(" ")
    }

    private fun sayiIfadesi(s: String): String {
        val tam = s.substringBefore(",").replace(".", "")
        val kesir = if ("," in s) s.substringAfter(",") else ""
        return if (kesir.isNotEmpty()) ondalikOku(tam, kesir) else rakamOku(tam)
    }

    fun kucukHarf(metin: String) = metin.replace("I", "ı").replace("İ", "i").lowercase(Locale.ROOT)

    private fun baslikMi(satir: String): Boolean {
        val kelimeler = rx("[^\\W\\d_]+").findAll(satir).map { it.value }.toList()
        val buyuk = kelimeler.count { it.length >= 2 && it == it.uppercase(Locale.ROOT) }
        return kelimeler.size >= 2 && buyuk >= 0.6 * kelimeler.size
    }

    private val ROMA_GECERLI = Regex("M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})")

    fun normalize(metin: String): String {
        var m = metin.replace("’", "'").replace("‘", "'").replace("–", "-").replace("—", " - ").replace("…", "...")

        for (k in KISALTMALAR.keys.sortedByDescending { it.length }) {
            m = rx("(?<![$HARFLER])" + Regex.escape(k) + "(?![$HARFLER])" + EK)
                .replace(m) { ekEkle(KISALTMALAR.getValue(k), it.groupValues[1]) }
        }

        // Tarih: 12.10.2026 veya 12/10/2026
        m = rx("\\b(\\d{1,2})[./](\\d{1,2})[./](\\d{4})\\b$EK").replace(m) { t ->
            val g = t.groupValues[1].toInt()
            val a = t.groupValues[2].toInt()
            if (g !in 1..31 || a !in 1..12) t.value
            else "${rakamOku(t.groupValues[1])} ${AYLAR[a]} ${ekEkle(rakamOku(t.groupValues[3]), t.groupValues[4])}"
        }

        // Kesir: 2/3 → üçte iki
        m = rx("\\b(\\d{1,3})/(\\d{1,3})\\b").replace(m) { t ->
            ekEkle(rakamOku(t.groupValues[2]), "de") + " " + rakamOku(t.groupValues[1])
        }

        // Saat: 14:30
        m = rx("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b$EK").replace(m) { t ->
            val d = t.groupValues[2].toInt()
            val dk = when {
                d == 0 -> ""
                t.groupValues[2].startsWith("0") -> " sıfır " + sayiOku(d.toLong())
                else -> " " + sayiOku(d.toLong())
            }
            ekEkle(rakamOku(t.groupValues[1]) + dk, t.groupValues[3])
        }

        // Yüzde: %40, % 3,5, %40'ı
        m = rx("%\\s?(\\d[\\d.]*(?:,\\d+)?)$EK").replace(m) { t ->
            "yüzde " + ekEkle(sayiIfadesi(t.groupValues[1]), t.groupValues[2])
        }

        // Para birimi
        m = rx("([₺$€£])\\s?(\\d[\\d.]*(?:,\\d+)?)").replace(m) { t ->
            sayiIfadesi(t.groupValues[2]) + " " + PARA.getValue(t.groupValues[1])
        }
        m = rx("(\\d)\\s?([₺$€£])").replace(m) { t -> t.groupValues[1] + " " + PARA.getValue(t.groupValues[2]) }

        // Roma rakamıyla sıra: XX. yüzyıl
        m = rx("\\b([IVXLCDM]{1,7})\\.(?=\\s+[a-zçğıöşüA-ZÇĞİÖŞÜ])").replace(m) { t ->
            if (ROMA_GECERLI.matches(t.groupValues[1])) siraOku(romaDegeri(t.groupValues[1])) else t.value
        }

        // Sıra sayısı: "3. kuşak"
        m = rx("\\b(\\d+)\\.(?=\\s+[a-zçğıöşü])").replace(m) { t ->
            siraYazi(rakamOku(t.groupValues[1]))
        }

        // Aralık: 1990-2000
        m = rx("(\\d)\\s?-\\s?(\\d)").replace(m, "$1, $2")

        // Sayılar
        m = rx("\\b(\\d{1,3}(?:\\.\\d{3})+|\\d+)(?:,(\\d+))?$EK").replace(m) { t ->
            val tam = t.groupValues[1].replace(".", "")
            val yazi = if (t.groupValues[2].isNotEmpty()) ondalikOku(tam, t.groupValues[2]) else rakamOku(tam)
            ekEkle(yazi, t.groupValues[3])
        }

        // Başlık satırları
        m = m.split("\n").joinToString("\n") { if (baslikMi(it)) kucukHarf(it) else it }

        // Büyük harfli kısaltmalar
        m = rx("\\b([A-ZÇĞİÖŞÜ]{2,6})\\b$EK").replace(m) { t ->
            val k = t.groupValues[1]
            val ek = t.groupValues[2]
            if (k in KELIME_KISALTMA || k.count { it in "AEIİOÖUÜ" } >= 2) {
                k + (if (ek.isNotEmpty()) "'$ek" else "")
            } else {
                val harfler = k.map { HARF_ADI.getValue(it) }.joinToString(" ")
                harfler + ekUyumla(harfler, ek)
            }
        }

        for ((s, k) in SEMBOLLER) m = m.replace(s, k)
        return rx("\\s+").replace(m, " ").trim()
    }
}
