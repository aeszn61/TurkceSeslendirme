"""Türkçe metin normalizasyonu: rakam, tarih, saat, yüzde, para, kısaltma → okunacak yazı.

G2P'den önce çalışır. Kurallar bilerek basit ve kural tabanlı tutuldu ki Kotlin'e birebir
taşınabilsin (bkz. android/tts-tr). Kapsam:
- Tam sayı (1.250.000), ondalık (3,5), sıra sayısı (3. kuşak, XX. yüzyıl)
- Rakamdan sonra kesmeyle gelen ekler: 10'da → onda, 4'ü → dördü
- Yüzde (%40), saat (14:30), tarih (12.10.2026, 12/10/2026), para birimi (250 TL, ₺, $, €)
- Kısaltma sözlüğü (Dr., vb., örn.) ve büyük harfli kısaltmalar (TBMM → te be me me)
"""
import re
from typing import List

BIRLER = ["", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz"]
ONLAR = ["", "on", "yirmi", "otuz", "kırk", "elli", "altmış", "yetmiş", "seksen", "doksan"]
BUYUKLER = [(10**12, "trilyon"), (10**9, "milyar"), (10**6, "milyon"), (1000, "bin")]
AYLAR = ["", "ocak", "şubat", "mart", "nisan", "mayıs", "haziran", "temmuz", "ağustos",
         "eylül", "ekim", "kasım", "aralık"]

KISALTMALAR = {
    "Prof. Dr.": "profesör doktor", "Doç. Dr.": "doçent doktor", "Dr.": "doktor", "Prof.": "profesör",
    "Doç.": "doçent", "Av.": "avukat", "Öğr. Gör.": "öğretim görevlisi", "Arş. Gör.": "araştırma görevlisi",
    "vb.": "ve benzeri", "vs.": "vesaire", "örn.": "örneğin", "bkz.": "bakınız", "Bkz.": "bakınız",
    "yy.": "yüzyıl", "M.Ö.": "milattan önce", "M.S.": "milattan sonra", "No.": "numara", "no.": "numara",
    "s.": "sayfa", "sf.": "sayfa", "Sn.": "sayın", "St.": "sokak", "Cad.": "cadde", "Mah.": "mahalle",
    "Tel.": "telefon", "çev.": "çeviren", "haz.": "hazırlayan", "a.g.e.": "adı geçen eser",
    "km": "kilometre", "kg": "kilogram", "cm": "santimetre", "mm": "milimetre", "gr": "gram",
    "lt": "litre", "m²": "metrekare", "km²": "kilometrekare", "°C": "derece", "TL": "lira",
}
# Cümle başında büyük harfle yazılmış hâlleri de (Örn., Vb.)
KISALTMALAR.update({k[0].upper() + k[1:]: v for k, v in list(KISALTMALAR.items())
                    if k[0].islower() and len(k) > 2 and k.endswith(".")})
# Harf harf okunmayıp kelime gibi okunan büyük harfli kısaltmalar
KELIME_KISALTMA = {"NATO", "ASELSAN", "TÜBİTAK", "TOKİ", "UNESCO", "UNICEF", "NASA", "TÜSİAD",
                   "ODTÜ", "İTÜ", "YÖK", "ÖSYM", "AKUT", "TEKEL", "SEKA", "İSKİ", "ASKİ", "BOTAŞ",
                   # Büyük harfle vurgu için yazılmış kısa Türkçe kelimeler
                   "VE", "BU", "ŞU", "AN", "EN", "DA", "DE", "Kİ", "NE", "HER", "BİR", "ÇOK", "YOK", "VAR",
                   "SON", "İLK", "TÜM", "HİÇ", "ASLA", "SEN", "BEN", "BİZ", "SİZ", "ONU", "HEM", "YA", "GÜN"}
HARF_ADI = {"A": "a", "B": "be", "C": "ce", "Ç": "çe", "D": "de", "E": "e", "F": "fe", "G": "ge",
            "Ğ": "yumuşak ge", "H": "he", "I": "ı", "İ": "i", "J": "je", "K": "ke", "L": "le", "M": "me",
            "N": "ne", "O": "o", "Ö": "ö", "P": "pe", "Q": "kü", "R": "re", "S": "se", "Ş": "şe", "T": "te",
            "U": "u", "Ü": "ü", "V": "ve", "W": "dabılyu", "X": "iks", "Y": "ye", "Z": "ze"}
SEMBOLLER = {"&": " ve ", "+": " artı ", "=": " eşittir ", "§": " madde ", "@": " et ", "°": " derece "}
ROMA = {"I": 1, "V": 5, "X": 10, "L": 50, "C": 100, "D": 500, "M": 1000}

UNLULER = "aeıioöuüâîû"


def sayi_oku(n: int) -> str:
    """Tam sayıyı Türkçe yazıya çevirir: 1953 → 'bin dokuz yüz elli üç'."""
    if n == 0:
        return "sıfır"
    if n >= 10**15:  # bu kadar büyük sayılar rakam rakam okunur
        return " ".join(sayi_oku(int(r)) for r in str(n))
    if n < 0:
        return "eksi " + sayi_oku(-n)
    kelimeler: List[str] = []
    for deger, ad in BUYUKLER:
        if n >= deger:
            kat, n = divmod(n, deger)
            if not (kat == 1 and ad == "bin"):
                kelimeler.append(sayi_oku(kat))
            kelimeler.append(ad)
    if n >= 100:
        yuz, n = divmod(n, 100)
        if yuz > 1:
            kelimeler.append(BIRLER[yuz])
        kelimeler.append("yüz")
    if n >= 10:
        kelimeler.append(ONLAR[n // 10])
        n %= 10
    if n:
        kelimeler.append(BIRLER[n])
    return " ".join(kelimeler)


def _son_unlu(kelime: str) -> str:
    for h in reversed(kelime):
        if h in UNLULER:
            return h
    return "e"


def _dar_unlu(kelime: str) -> str:
    """Büyük ünlü uyumuna göre ı/i/u/ü."""
    return {"a": "ı", "ı": "ı", "â": "ı", "e": "i", "i": "i", "î": "i",
            "o": "u", "u": "u", "û": "u", "ö": "ü", "ü": "ü"}[_son_unlu(kelime)]


def _yumusat(kelime: str) -> str:
    """Ünlüyle başlayan ek gelince sondaki t → d (dört → dörd)."""
    return kelime[:-1] + "d" if kelime.endswith("dört") else kelime


def sira_oku(n: int) -> str:
    """Sıra sayısı: 3 → 'üçüncü', 20 → 'yirminci'."""
    yazi = sayi_oku(n)
    son = yazi.split()[-1]
    v = _dar_unlu(son)
    if son[-1] in UNLULER:
        ek = "nc" + v
    else:
        yazi = _yumusat(yazi)
        ek = v + "nc" + v
    return yazi + ek


SERT = set("fstkçşhp")


def ek_uyumla(kok: str, ek: str) -> str:
    """Eki köke göre yeniden uyumlar: lira + ye → ya, üç + da → te, dört + ü → dörd + ü.

    Rakam ve kısaltmalardan sonra yazılan ek, okunan kelimeye göre değil yazılan şekle göre
    seçilmiş olabilir (TL'ye → liraya); ünlü uyumu, sertleşme ve kaynaştırma harfi burada düzelir.
    """
    if not ek:
        return ek
    kok_unlu_bitis = kok[-1] in UNLULER
    if not kok_unlu_bitis and len(ek) > 1 and ek[0] in "yns" and ek[1] in UNLULER:
        ek = ek[1:]  # kaynaştırma harfi ünsüzle biten kökte düşer
    elif kok_unlu_bitis and ek[0] in UNLULER:
        ek = ("n" if ek in ("ın", "in", "un", "ün") else "y") + ek  # ünlüyle biten köke kaynaştırma harfi
    sonuc, onceki = [], _son_unlu(kok)
    for i, h in enumerate(ek):
        if h in "ae":
            h = "e" if onceki in "eiöüî" else "a"
        elif h in "ıiuü":
            h = _dar_unlu(onceki)
        elif i == 0 and h in "dt":
            h = "t" if kok[-1] in SERT else "d"
        elif i == 0 and h in "cç":
            h = "ç" if kok[-1] in SERT else "c"
        if h in UNLULER:
            onceki = h
        sonuc.append(h)
    return "".join(sonuc)


def _ek_ekle(yazi: str, ek: str) -> str:
    """'on' + 'da' → 'onda'; 'dört' + 'ü' → 'dördü'."""
    ek = ek_uyumla(yazi, ek)
    if ek and ek[0] in UNLULER:
        yazi = _yumusat(yazi)
    return yazi + ek


def _roma_degeri(s: str) -> int:
    toplam = 0
    for i, h in enumerate(s):
        d = ROMA[h]
        toplam += -d if i + 1 < len(s) and ROMA[s[i + 1]] > d else d
    return toplam


def _ondalik_oku(tam: str, kesir: str) -> str:
    kesir_oku = " ".join(["sıfır"] * (len(kesir) - len(kesir.lstrip("0")))) + " " + (sayi_oku(int(kesir)) if kesir.strip("0") else "")
    return sayi_oku(int(tam)) + " virgül " + " ".join(kesir_oku.split())


EK = r"(?:'([a-zçğıöşüâîû]+))?"


def normalize(metin: str) -> str:
    m = metin.replace("’", "'").replace("‘", "'").replace("–", "-").replace("—", " - ").replace("…", "...")

    # Kısaltmalar (uzundan kısaya, kelime sınırında)
    for k in sorted(KISALTMALAR, key=len, reverse=True):
        m = re.sub(r"(?<![\wçğıöşüÇĞİÖŞÜ])" + re.escape(k) + r"(?![\wçğıöşüÇĞİÖŞÜ])" + EK,
                   lambda t, k=k: _ek_ekle(KISALTMALAR[k], t.group(1) or ""), m)

    # Tarih: 12.10.2026 veya 12/10/2026
    def tarih(t):
        g, a, y = int(t.group(1)), int(t.group(2)), int(t.group(3))
        if not (1 <= g <= 31 and 1 <= a <= 12):
            return t.group(0)
        return f"{sayi_oku(g)} {AYLAR[a]} {_ek_ekle(sayi_oku(y), t.group(4) or '')}"
    m = re.sub(r"\b(\d{1,2})[./](\d{1,2})[./](\d{4})\b" + EK, tarih, m)

    # Kesir: 2/3 → üçte iki (tarihten sonra, böylece 12/10/2026 bozulmaz)
    m = re.sub(r"\b(\d{1,3})/(\d{1,3})\b",
               lambda t: _ek_ekle(sayi_oku(int(t.group(2))), "de") + " " + sayi_oku(int(t.group(1))), m)

    # Saat: 14:30, 09.05'te (sadece iki nokta üst üste)
    def saat(t):
        s, d = int(t.group(1)), int(t.group(2))
        dk = "" if d == 0 else (" sıfır " + sayi_oku(d) if t.group(2).startswith("0") else " " + sayi_oku(d))
        return _ek_ekle(sayi_oku(s) + dk, t.group(3) or "")
    m = re.sub(r"\b([01]?\d|2[0-3]):([0-5]\d)\b" + EK, saat, m)

    # Yüzde: %40, % 3,5, %40'ı
    def yuzde(t):
        return "yüzde " + _ek_ekle(_sayi_ifadesi(t.group(1)), t.group(2) or "")
    m = re.sub(r"%\s?(\d[\d.]*(?:,\d+)?)" + EK, yuzde, m)

    # Para: ₺250, $5, €3,5 → sayı + birim
    birimler = {"₺": "lira", "$": "dolar", "€": "avro", "£": "sterlin"}
    m = re.sub(r"([₺$€£])\s?(\d[\d.]*(?:,\d+)?)", lambda t: _sayi_ifadesi(t.group(2)) + " " + birimler[t.group(1)], m)
    m = re.sub(r"(\d)\s?([₺$€£])", lambda t: t.group(1) + " " + birimler[t.group(2)], m)

    # Roma rakamıyla sıra: XX. yüzyıl, IV. Murat
    m = re.sub(r"\b([IVXLCDM]{1,7})\.(?=\s+[a-zçğıöşüA-ZÇĞİÖŞÜ])",
               lambda t: sira_oku(_roma_degeri(t.group(1))) if re.fullmatch(r"M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})", t.group(1)) else t.group(0), m)

    # Sıra sayısı: "3. kuşak" (rakam + nokta + küçük harfle devam)
    m = re.sub(r"\b(\d+)\.(?=\s+[a-zçğıöşü])", lambda t: sira_oku(int(t.group(1))), m)

    # Aralık: 1990-2000 → "bin dokuz yüz doksan, iki bin"
    m = re.sub(r"(\d)\s?-\s?(\d)", r"\1, \2", m)

    # Sayılar (binlik noktalı, ondalık virgüllü), kesmeli ekiyle
    m = re.sub(r"\b(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d+))?" + EK,
               lambda t: _ek_ekle(_ondalik_oku(t.group(1).replace(".", ""), t.group(2)) if t.group(2)
                                  else sayi_oku(int(t.group(1).replace(".", ""))), t.group(3) or ""), m)

    # Tamamı büyük harfli başlık satırları (GİRİŞ VE AMAÇ) kısaltma sayılmasın: küçük harfe çevir
    m = "\n".join(kucuk_harf(sat) if _baslik_mi(sat) else sat for sat in m.split("\n"))

    # Büyük harfli kısaltmalar: TBMM'de → te be me mede. İki ve daha fazla ünlüsü olan (NATO, GİRİŞ) kelime gibi okunur.
    def kisaltma(t):
        k, ek = t.group(1), t.group(2) or ""
        if k in KELIME_KISALTMA or sum(h in "AEIİOÖUÜ" for h in k) >= 2:
            return k + ("'" + ek if ek else "")
        harfler = " ".join(HARF_ADI[h] for h in k)
        return harfler + ek_uyumla(harfler, ek)
    m = re.sub(r"\b([A-ZÇĞİÖŞÜ]{2,6})\b" + EK, kisaltma, m)

    for s, k in SEMBOLLER.items():
        m = m.replace(s, k)
    return re.sub(r"\s+", " ", m).strip()


def _baslik_mi(satir: str) -> bool:
    """Kelimelerinin en az %60'ı tamamen büyük harf olan, en az 2 kelimelik satır başlıktır."""
    kelimeler = re.findall(r"[^\W\d_]+", satir)
    buyuk = [k for k in kelimeler if len(k) >= 2 and k == k.upper()]
    return len(kelimeler) >= 2 and len(buyuk) >= 0.6 * len(kelimeler)


def kucuk_harf(metin: str) -> str:
    return metin.replace("I", "ı").replace("İ", "i").lower()


def _sayi_ifadesi(s: str) -> str:
    tam, _, kesir = s.partition(",")
    tam = tam.replace(".", "")
    return _ondalik_oku(tam, kesir) if kesir else sayi_oku(int(tam))
