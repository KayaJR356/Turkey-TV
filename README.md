# Türkiye TV

Android TV ve Google TV için kumanda odaklı, tam ekran canlı kanal oynatıcısı.

## Özellikler

- `canlitv.diy/tr` kataloğundaki kanal adlarını ve sırasını her açılışta yeniler
- 20 Temmuz 2026 kataloğundaki 285 kanalın tamamını aynı sırayla gösterir
- Kanal oynatıcısındaki HLS/DASH adresini bulup Media3 ile uyarlanabilir kalitede oynatır
- TRT kanallarında doğrulanmış resmî HLS yayınlarını kullanır
- HLS/DASH sağlamayan YouTube ve dış kaynak kanallarını kendi doğrudan oynatıcısında tam ekrana alır
- Son eksiksiz kanal listesini cihazda saklar; eksik listeyi hiçbir zaman tam listenin üzerine yazmaz
- Büyük yazı, belirgin odak halkası ve 10-foot TV mesafesine uygun kanal paneli sunar
- İlk açılışta başlangıç davranışını sorar
- Son izlenen kanal, her zaman 1. kanal veya açılışta kanal seçimi seçeneklerini destekler
- Açılışta otomatik oynatma ve kanal bilgi süresi ayarları içerir
- Son izlenen kanalı cihazda saklar
- GitHub Actions ile her değişiklikte debug APK derler ve Android Lint çalıştırır

## Kumanda

| Tuş | İşlev |
|---|---|
| `Program + / -` | Sonraki / önceki kanal |
| `0–9` | Kanal numarasına doğrudan git |
| `OK`, Menü veya Rehber | Kanal listesini aç |
| Yön tuşları | Yalnızca kanal ve ayar menülerinde gezin |
| Sağ veya Geri | Açık menüyü kapat |
| Ayarlar veya Mavi | Ayarları aç |
| Bilgi | Kanal bilgisini göster |
| Oynat / Duraklat | Yayını oynat veya duraklat |

## Yayın kalitesi

Uygulama, kaynakta bulunan en iyi uyarlanabilir akışı Media3'e verir. 20 Temmuz 2026 taramasında site oynatıcılarının 212 tanesi doğrudan HLS yayın sağladı; TRT ve bazı ek doğrulanmış kanallar tercih edilen doğrudan yayınlarla açılır. Kalan kanallar kaynak olarak YouTube, dış iframe veya yayıncının kendi oynatıcısını kullanır. Uygulama bunları reklamlı katalog sayfası yerine doğrudan tam ekran kaynak oynatıcısında açar.

Bir yayıncının yalnızca SD yayın vermesi, coğrafi engel koyması veya yayını kapatması durumunda uygulama görüntüyü yapay olarak HD'ye çeviremez. Liste ve sıra korunur; yayın kalitesi ve erişilebilirlik yayın sahibine bağlıdır.

## Derleme

Gereksinimler: JDK 17, Android SDK 35 ve Gradle 8.9.

```shell
gradle :app:assembleDebug
```

GitHub Actions başarılı olduğunda kurulabilir debug APK, `TurkiyeTV-debug` adıyla iş akışı çıktısına eklenir.

## Gizlilik

Uygulama hesap, konum, kişi, kamera veya mikrofon izni istemez. Yalnızca kanal kataloğu ve yayınları için internet erişimi kullanır. Ayarlar, son kanal ve son eksiksiz katalog cihazda saklanır.

## Lisans ve yayın hakları

Kaynak kod MIT lisanslıdır. Uygulama yayın içeriğinin sahibi veya dağıtıcısı değildir. Kanal adları, yayınlar ve üçüncü taraf oynatıcılar kendi sahiplerine aittir.
