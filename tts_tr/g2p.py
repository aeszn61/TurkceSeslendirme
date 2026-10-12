"""Kural tabanlı Türkçe G2P (harf → IPA), espeak-ng kullanmadan.

Çıktı, Piper'ın espeak sembol kümesindeki sembollerle yazılır; böylece Piper/VITS
checkpoint'lerinin `phoneme_id_map`'i olduğu gibi kullanılabilir ama çalışma anında
GPL'li espeak'e ihtiyaç kalmaz.

Kapsam (v0):
- Türkçe büyük/küçük harf (İ/ı) doğru dönüşüm, şapkalı harfler (â, î, û).
- k, g, l için ince/kalın ünlü bağlamına göre allofonlar (c, ɟ, l / k, ɡ, ɫ).
- ğ: ince ünlüler arasında j, diğer durumlarda önceki ünlüyü uzatır.
- Vurgu: varsayılan son hece; soru eki ve bağlaç gibi vurgusuz kelimeler; küçük bir
  istisna sözlüğü (yer adları, bazı zarflar); kesme işaretinden sonraki ekler vurgusuz.
Sayı/tarih/kısaltma açma bu modülün işi değil; metin önce normalize edilmeli.
"""
import re
from typing import Dict, List, Optional

INCE = set("eiöüîê")
KALIN = set("aıouâû")
UNLULER = INCE | KALIN

_HARF = {
    "a": "a", "b": "b", "c": "dʒ", "ç": "tʃ", "d": "d", "e": "e", "f": "f",
    "h": "h", "ı": "ɯ", "i": "i", "j": "ʒ", "m": "m", "n": "n", "o": "o",
    "ö": "ø", "p": "p", "r": "ɾ", "s": "s", "ş": "ʃ", "t": "t", "u": "u",
    "ü": "y", "v": "v", "y": "j", "z": "z", "q": "k", "w": "v", "x": "ks",
    "â": "aː", "î": "iː", "û": "uː", "ê": "eː",
}

# Vurgusuz (klitik) kelimeler: vurgu bir önceki kelimede kalır.
VURGUSUZ = {"mı", "mi", "mu", "mü", "mısın", "misin", "musun", "müsün", "mıyım", "miyim",
            "muyum", "müyüm", "mıdır", "midir", "mudur", "müdür", "de", "da", "ki", "ve", "ile"}

# Vurgusu son hecede olmayan kelimeler: kelime → vurgulu ünlünün sırası (0'dan).
VURGU_ISTISNA: Dict[str, int] = {
    "ankara": 0, "istanbul": 1, "izmir": 0, "antalya": 1, "türkiye": 0, "avrupa": 1,
    "almanya": 1, "amerika": 1, "londra": 0, "paris": 0, "adana": 0, "bursa": 0,
    "konya": 0, "trabzon": 0, "kayseri": 0, "samsun": 0, "erzurum": 0, "eskişehir": 0,
    "şimdi": 0, "sonra": 0, "önce": 0, "belki": 0, "yalnız": 0, "ancak": 0, "nasıl": 0,
    "nerede": 0, "nereye": 0, "neden": 0, "niçin": 0, "hangi": 0, "bazen": 0, "çünkü": 0,
    "yarın": 0, "dün": 0, "bugün": 0, "akşam": 0, "sabah": 0, "elbette": 1, "lütfen": 0,
    "evet": 0, "hayır": 0, "merhaba": 0, "tamam": 1, "efendim": 0, "işte": 0, "hemen": 0,
    "aslında": 0, "galiba": 1, "gerçekten": 0, "maalesef": 1, "örneğin": 0, "yani": 0,
    "fakat": 0, "lakin": 0, "ama": 0, "oysa": 0, "sanki": 0, "hatta": 0, "henüz": 0,
}

NOKTALAMA = {",": ",", ".": ".", "?": "?", "!": "!", ";": ";", ":": ":",
             "…": ".", "-": ",", "–": ",", "—": ",", "(": ",", ")": ","}


def kucult(metin: str) -> str:
    return metin.replace("I", "ı").replace("İ", "i").lower()


def _baglam_ince(kelime: str, i: int) -> bool:
    """k/g/l için: aynı hecedeki ünlü ince mi? Önce sonraki, yoksa önceki ünlüye bakar."""
    if i + 1 < len(kelime) and kelime[i + 1] in UNLULER:
        return kelime[i + 1] in INCE
    for j in range(i - 1, -1, -1):
        if kelime[j] in UNLULER:
            return kelime[j] in INCE
    for j in range(i + 1, len(kelime)):
        if kelime[j] in UNLULER:
            return kelime[j] in INCE
    return False


def _kelime_fonemleri(kelime: str) -> List[List[str]]:
    """Her harf için fonem listesi döndürür (ğ gibi sessiz harfler boş liste)."""
    sonuc: List[List[str]] = []
    for i, h in enumerate(kelime):
        if h in "kg":
            ince = _baglam_ince(kelime, i)
            # Şapkalı a (kâr, gâvur) önündeki k/g incelir
            if i + 1 < len(kelime) and kelime[i + 1] == "â":
                ince = True
            sonuc.append([("c" if h == "k" else "ɟ") if ince else ("k" if h == "k" else "ɡ")])
        elif h == "l":
            ince = _baglam_ince(kelime, i) or (i + 1 < len(kelime) and kelime[i + 1] == "â")
            sonuc.append(["l" if ince else "ɫ"])
        elif h == "ğ":
            onceki = kelime[i - 1] if i else ""
            sonraki = kelime[i + 1] if i + 1 < len(kelime) else ""
            if onceki in INCE and sonraki in UNLULER:
                sonuc.append(["j"])
            elif onceki in UNLULER and sonraki not in UNLULER and sonuc and "ː" not in sonuc[-1][-1]:
                sonuc[-1] = sonuc[-1][:-1] + [sonuc[-1][-1] + "ː"]
                sonuc.append([])
            else:
                sonuc.append([])
        elif h in _HARF:
            sonuc.append(list(_HARF[h]) if h in "cçx" else [_HARF[h]])
        else:
            sonuc.append([])
    return sonuc


def _vurgulu_unlu(kelime: str, kok_uzunlugu: int) -> Optional[int]:
    """Vurgulu ünlünün kelime içindeki harf indeksini döndürür."""
    unluler = [i for i, h in enumerate(kelime) if h in UNLULER]
    if not unluler:
        return None
    kok = kelime[:kok_uzunlugu]
    kok_unluler = [i for i in unluler if i < kok_uzunlugu]
    if kok in VURGU_ISTISNA and kok_unluler:
        return kok_unluler[min(VURGU_ISTISNA[kok], len(kok_unluler) - 1)]
    if kok_uzunlugu < len(kelime) and kok_unluler:  # kesme işaretinden sonraki ekler vurgusuz
        return kok_unluler[-1]
    return unluler[-1]


def _kelime_ipa(ham: str) -> List[str]:
    kok_uzunlugu = len(ham)
    if "'" in ham:
        kok_uzunlugu = ham.index("'")
        ham = ham.replace("'", "")
    kelime = ham
    if not kelime:
        return []
    harf_fonemleri = _kelime_fonemleri(kelime)
    vurgu = None if kelime in VURGUSUZ else _vurgulu_unlu(kelime, kok_uzunlugu)
    if vurgu is not None and len([h for h in kelime if h in UNLULER]) < 2 and kelime not in VURGU_ISTISNA:
        vurgu_yeri = 0  # tek heceli kelime: vurgu işareti başa
    elif vurgu is not None:
        # Türkçe hecede en fazla bir başlangıç ünsüzü var: vurgu, ünlüden önceki ünsüzden başlar
        vurgu_yeri = vurgu - 1 if vurgu > 0 and kelime[vurgu - 1] not in UNLULER else vurgu
        if not harf_fonemleri[vurgu_yeri]:  # sessiz ğ: vurgu doğrudan ünlüde başlar
            vurgu_yeri = vurgu
    fonemler: List[str] = []
    for i, f in enumerate(harf_fonemleri):
        if vurgu is not None and i == vurgu_yeri:
            fonemler.append("ˈ")
        for p in f:
            fonemler.extend(list(p) if p.endswith("ː") else [p])
    return fonemler


def fonemlere_cevir(metin: str) -> List[str]:
    """Normalize edilmiş Türkçe metni IPA fonem listesine çevirir (boşluk ve noktalama dahil)."""
    metin = kucult(metin).replace("’", "'").replace("`", "'")
    parcalar = re.findall(r"[a-zçğıöşüâîûêqwx']+|[,.?!;:…\-–—()]", metin)
    fonemler: List[str] = []
    for p in parcalar:
        if p in NOKTALAMA:
            if fonemler and fonemler[-1] == " ":
                fonemler.pop()
            if fonemler and fonemler[-1] in NOKTALAMA.values():
                continue
            fonemler.append(NOKTALAMA[p])
            fonemler.append(" ")
            continue
        ipa = _kelime_ipa(p.strip("'"))
        if ipa:
            fonemler.extend(ipa)
            fonemler.append(" ")
    while fonemler and fonemler[-1] == " ":
        fonemler.pop()
    return fonemler


def fonem_idleri(fonemler: List[str], fonem_id_haritasi: Dict[str, List[int]]) -> List[int]:
    """Piper biçiminde id dizisi: ^ _ f1 _ f2 _ ... $ (her fonemden sonra dolgu)."""
    dolgu = fonem_id_haritasi["_"]
    idler = list(fonem_id_haritasi["^"]) + list(dolgu)
    for f in fonemler:
        if f not in fonem_id_haritasi:
            raise KeyError(f"Bilinmeyen fonem: {f!r}")
        idler += list(fonem_id_haritasi[f]) + list(dolgu)
    return idler + list(fonem_id_haritasi["$"])
