# Kendi Türkçe TTS Modelimiz: Fizibilite ve Yol Haritası

Hedef: Telefon işlemcisinde (CPU, internetsiz) gerçek zamanlıdan hızlı çalışan, doğal ve insan gibi konuşan, uygulamalara gömülebilen, **ticari kullanıma uygun lisanslı** açık kaynak bir Türkçe metin-okuma (TTS) modeli. İlk kullanım alanı: Android PDF Türkçe seslendirme uygulaması.

## 1. Kısa cevap: Yapılabilir mi?

**Evet.** Bugün ~20–80 milyon parametreli modeller (VITS/Piper, Matcha-TTS, StyleTTS2/Kokoro ailesi) orta seviye bir Android telefonda tek CPU çekirdeğiyle bile gerçek zamandan hızlı konuşma üretebiliyor. Mimari ve araçlar hazır; eksik olan **Türkçeye özel parçalar**:

1. **İyi bir Türkçe metin ön-işleyici** (sayılar, tarihler, kısaltmalar, `3'te → üçte` gibi ünlü uyumlu ekler, vurgu).
2. **Temiz, lisansı bize ait, tek konuşmacılı bir ses veri seti** (10–20 saat).
3. **Lisans temizliği** (aşağıda: mevcut Türkçe açık modellerin çoğu GPL'li espeak-ng'ye bağımlı).

Dürüst not: "Türkiye'de ilk" iddiasını dikkatli kurmak lazım. Piper için zaten birkaç Türkçe ses var (`tr_TR-dfki`, `tr_TR-fahrettin`, `tr_TR-fettah`), ama kaliteleri ortalama, metin işlemeleri zayıf ve espeak-ng (GPL) bağımlılığı yüzünden kapalı/ticari uygulamaya gömmek sorunlu. **Gerçekten ayırt edici olan:** izin verici lisanslı (MIT/Apache), espeak'siz, Türkçe metin normalizasyonu güçlü, mobil-öncelikli ve doğallığı yüksek bir model. Bu kombinasyonu bilinen bir açık proje şu an sunmuyor.

## 2. Mevcut seçenekler

| Model | Boyut | Telefonda hız | Doğallık | Lisans | Türkçe durumu |
|---|---|---|---|---|---|
| **Piper (VITS)** | 15–60M | Çok hızlı | İyi | Kod MIT (yeni sürüm GPL), espeak-ng GPL | Hazır sesler var, kalite orta |
| **Matcha-TTS** + Vocos/HiFi-GAN | ~18M + vokoder | Çok hızlı | İyi–çok iyi | MIT | Yok, eğitmek gerek |
| **StyleTTS2 / Kokoro-82M** | ~82M | Hızlı (int8 ile rahat) | Çok iyi (en insansı) | StyleTTS2 MIT, Kokoro Apache-2.0 | Yok, eğitmek gerek |
| XTTS-v2, F5-TTS, büyük LLM-tabanlı TTS | 300M–1B+ | Telefonda pratik değil | Mükemmel | Çoğu ticari olmayan lisans | Kısmen |

**Çalıştırma katmanı:** [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Apache-2.0). VITS/Piper, Matcha ve Kokoro modellerini ONNX olarak Android'de (Kotlin/Java API, hatta sistem TTS motoru olarak) çalıştırıyor. Kendi runtime'ımızı yazmamıza gerek yok.

## 3. Önerilen mimari

```
PDF metni
  → [1] Türkçe metin normalizasyonu   (sayı, tarih, saat, para, %, kısaltma, Roma rakamı, ek uyumu)
  → [2] Türkçe G2P + vurgu            (kural tabanlı, espeak'siz; istisna sözlüğü)
  → [3] Akustik model                 (VITS → sonra StyleTTS2/Kokoro tarzı)
  → [4] Vokoder                       (VITS'te dahili; diğerlerinde iSTFTNet/Vocos)
  → ONNX (int8) → sherpa-onnx → Android
```

**Neden kendi G2P'miz?** Türkçe yazım neredeyse tamamen fonetik; harf→ses kuralları ~200 satırlık kodla yazılabilir. Zor kısımlar: vurgu (genelde son hece, ama yer adları `Ankára`, `-me/-ma` olumsuzluk, bazı ekler), şapkalı harfler, yabancı kelimeler, ğ'nin uzatma etkisi. Bunu iyi yapmak hem espeak (GPL) bağımlılığını kaldırır hem de mevcut Türkçe seslerden **en belirgin kalite farkını** yaratır. Bu katman tek başına da açık kaynak bir kütüphane olarak değerli.

## 4. Veri

| Kaynak | Lisans | TTS için uygunluk |
|---|---|---|
| **Kendi kaydımız** (profesyonel seslendirmen, 10–20 saat, tek konuşmacı) | Bize ait (sözleşmeyle) | **En iyi seçenek.** Doğallığın %70'i buradan gelir |
| Mozilla Common Voice Türkçe | CC0 | Çok konuşmacılı, gürültülü; ön-eğitim/çok-konuşmacılı denemeler için |
| FLEURS / diğer ASR setleri | CC-BY vb. | Ön-eğitim için yardımcı, son model için yetersiz |
| Ticari TTS çıktılarıyla "damıtma" | Çoğu hizmet şartı yasaklıyor | **Kullanmamalıyız** (lisans riski) |

Kayıt planı: sessiz oda, iyi bir mikrofon (USB bile olur), 48 kHz WAV, ~8.000–12.000 cümle. Cümleleri Türkçe Vikipedi/kamu malı kitaplardan, tüm ses birimlerini ve sayı/tarih okumalarını kapsayacak şekilde seçeriz (kapsam scriptini biz yazarız). Seslendirmenle sesin model olarak dağıtılmasına açık izin veren yazılı sözleşme şart. Bütçe: Türkiye'de profesyonel seslendirmen için kabaca stüdyo + ücret dahil birkaç on bin TL mertebesi; ilk deneme için gönüllü/kendi sesimizle 2–3 saatlik pilot kayıt da yeterli.

## 5. Eğitim gücü

| İş | Donanım | Süre | Tahmini bulut maliyeti |
|---|---|---|---|
| VITS/Piper ince ayar (hazır checkpoint'ten) | 1× RTX 4090 / A100 | 1–3 gün | ~30–150 $ |
| VITS sıfırdan | 1× A100 | 1–2 hafta | ~300–600 $ |
| StyleTTS2/Kokoro tarzı model | 1–2× A100 | 2–4 hafta | ~600–2000 $ |

RunPod, Vast.ai, Lambda gibi saatlik GPU kiralama yeterli; kendi kartımız (RTX 3090/4090) varsa maliyet sıfıra iner.

## 6. Mobil hedefler

- Model dosyası: ≤ 30 MB (int8), ilk sürümde ≤ 60 MB kabul.
- Hız (RTF): orta seviye telefonda (Snapdragon 7xx sınıfı) ≤ 0.3, yani 10 sn ses ≤ 3 sn'de.
- İlk ses gecikmesi: cümle bazlı akışla < 500 ms.
- Tamamen çevrimdışı.
- Ölçüm: MOS (dinleyici puanı, 1–5) hedefi ≥ 4.0; karşılaştırma için Piper Türkçe sesleri ve Google TTS.

## 7. Yol haritası

| Aşama | İçerik | Çıktı |
|---|---|---|
| **M0 – Altyapı** (1–2 hafta) | Türkçe normalizasyon + G2P kütüphanesi (Python + Kotlin). Mevcut bir Piper Türkçe sesini sherpa-onnx ile Android'de PDF okutarak uçtan uca hattı kurmak | Çalışan demo APK, test seti |
| **M1 – Veri** (2–4 hafta, M0 ile paralel) | Cümle seti, kayıt, hizalama/temizlik scriptleri, pilot 2–3 saat | Temiz veri seti |
| **M2 – İlk model** (2–3 hafta) | Kendi G2P'mizle VITS eğitimi (çok dilli checkpoint'ten ince ayar), ONNX + int8 | `turkce-vits-v0.1.onnx`, ses örnekleri, MOS ölçümü |
| **M3 – Uygulama** (1–2 hafta) | PDF uygulamasına entegrasyon, cümle bazlı akış, hız ayarı | Uygulamada kendi sesimiz |
| **M4 – Doğallık atlaması** (4–6 hafta) | StyleTTS2/Kokoro tarzı model, duygu/stil kontrolü, uzun metin prozodisi | v1.0, açık kaynak yayın (Hugging Face + GitHub) |

## 8. Riskler

- **Veri kalitesi** en büyük risk; kötü kayıt iyi modeli kurtaramaz. Pilot kayıtla erken doğrularız.
- **Vurgu ve tonlama** Türkçede kural dışı durumlarla dolu; istisna sözlüğü zamanla büyür.
- **Lisans**: espeak-ng ve ticari olmayan modellerden uzak durarak baştan temiz kalırız.
- **Eski telefonlar**: çok düşük donanım için ayrıca küçük (~15M) bir VITS sürümü çıkarırız.

## 9. Karar bekleyen

Önerim: **Yol A** ile başlamak (M0: Türkçe metin işleyici + mevcut Piper sesiyle Android demo, sonra kendi verimizle VITS). En hızlı somut sonuç, düşük risk ve M4'teki daha doğal modele doğrudan altyapı oluşturur.

- **Yol A:** VITS/Piper tarzı, hızlı ve hafif → sonra yükselt (önerilen)
- **Yol B:** Doğrudan StyleTTS2/Kokoro tarzı, en doğal ses ama daha uzun ve pahalı
- **Yol C:** Matcha-TTS + Vocos, A ile B arası
