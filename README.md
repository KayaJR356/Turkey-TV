# Türksat TV for Android TV

Kumandayla kullanılmak üzere tasarlanmış, reklamsız uygulama arayüzüne sahip kişisel Android TV canlı kanal oynatıcısı.

## Öne çıkanlar

- Android TV / Google TV Leanback başlatıcı desteği
- Kumandanın rakam tuşlarıyla doğrudan kanal seçimi (`1`–`285`)
- Kanal Yukarı/Aşağı ve yön tuşlarıyla hızlı kanal değiştirme
- OK, Menü veya Rehber tuşuyla kumanda odaklı kanal listesi
- Kırmızı/Yıldız tuşuyla favori ekleme ve kaldırma
- Yeşil/Bilgi tuşuyla yayın akışı görünümü
- Mavi/Ayarlar tuşuyla özel HTTPS JSON kanal listesi tanımlama
- Oynat/Duraklat, ileri ve geri medya tuşları
- Her açılışta web kanal listesini yeniden keşfetme
- Ağ sorunu olduğunda son başarılı kanal listesini kullanma
- Resmî TRT HLS yayınlarında Media3/ExoPlayer ile uyarlanabilir HD oynatma
- Diğer kanallarda kanal sayfasındaki oynatıcıyı TV ekranına genişleten WebView yedeği

## Kanal sırası ve güncelleme

Türksat TKGS ana liste verisi internette indirilebilir bir LCN/JSON servisi olarak yayınlanmadığından uygulama, kullanıcı tarafından onaylanan `https://www.canlitv.diy/tr` kanal sırasını yedek sıralama olarak kullanır. Sayfa her uygulama açılışında HTTPS üzerinden okunur ve kanal başlıkları ile bağlantıları yeniden oluşturulur. 20'den az geçerli kayıt dönerse güncelleme reddedilir; son başarılı liste korunur.

Varsayılan kaynak 20 Temmuz 2026 doğrulamasında 285 kanal döndürmüştür. Site yapısı veya yayın hakları değişirse bazı kanallar geçici olarak çalışmayabilir.

## Kumanda haritası

| Tuş | İşlev |
|---|---|
| `0–9` | 1,3 saniye içinde girilen numaraya git |
| Kanal `+/-` | Sonraki / önceki kanal |
| Yukarı / Aşağı | İzleme ekranında sonraki / önceki kanal |
| OK / Menü / Rehber | Kanal listesini aç / kapat |
| Geri | Kanal listesini kapat |
| Kırmızı / `*` | Geçerli kanalı favorilere ekle / çıkar |
| Yeşil / Bilgi | Web kanallarında yayın akışını aç / kapat |
| Mavi / Ayarlar | Özel kanal listesi adresi |
| Oynat-Duraklat | Oynatmayı kontrol et |
| İleri / Geri sar | ExoPlayer zaman atlama |

## Özel kanal listesi

Mavi veya Ayarlar tuşuyla aşağıdaki biçimde bir HTTPS JSON adresi tanımlanabilir:

```json
[
  {"number": 1, "name": "Kanal Adı", "url": "https://example.com/live/master.m3u8"}
]
```

Yalnızca HTTPS kayıtları kabul edilir ve kayıtlar `number` alanına göre sıralanır. Özel adres yanıt vermezse web kataloğu, o da kullanılamazsa önbellek ve yerleşik TRT listesi kullanılır.

## Derleme

Gereksinimler: JDK 17, Android SDK 35 ve Gradle 8.9.

```shell
gradle assembleRelease
```

İmzalama anahtarı depoya eklenmez. Yerel sürüm APK'sı Android APK Signature Scheme v1, v2 ve v3 ile imzalanmıştır.

## Teknik yapı

- Java
- Android SDK 35, minimum Android 6.0 (API 23)
- AndroidX AppCompat
- AndroidX Media3 ExoPlayer + HLS
- Android WebView
- `ChannelRepository`: uzak JSON, web keşfi, önbellek ve yerleşik kaynak zinciri
- `MainActivity`: tam ekran oynatma, kumanda olayları, kanal listesi ve favoriler

## Gizlilik

Uygulama kullanıcı hesabı, konum, kişi, mikrofon veya kamera verisi istemez. Yalnızca kanal listesini ve yayınları almak için internet izni kullanır. Favoriler, özel liste adresi ve son başarılı katalog cihazdaki uygulama tercihlerinde saklanır.

## Sınırlamalar

- Uygulama yayın içeriğinin sahibi veya dağıtıcısı değildir.
- Üçüncü taraf web oynatıcılarının kullanılabilirliği, reklamları ve yayın kalitesi ilgili siteye bağlıdır.
- Spor ve benzeri içerikler coğrafi/yayın hakkı nedeniyle kanal tarafından engellenebilir.
- Türksat'ın uydu üzerinden gönderdiği TKGS sırası internete açık bir API olmadığından, bu sürümde kullanıcı tarafından onaylanan web sırası uygulanır.

## Lisans

Kaynak kod MIT Lisansı ile sunulur. Kanal logoları, adları, yayınları ve üçüncü taraf siteler kendi sahiplerine aittir.
