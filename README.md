# Türkiye TV

Android TV ve Google TV için kumanda odaklı, tam ekran canlı kanal oynatıcısı.

## Özellikler

- `canlitv.diy/tr` kataloğundaki kanal adlarını ve sırasını her açılışta yeniler
- 20 Temmuz 2026 kataloğundaki 285 kanalın tamamını aynı sırayla gösterir
- Kanal oynatıcısındaki HLS/DASH adresini bulup Media3 ile uyarlanabilir kalitede oynatır
- TRT kanallarında doğrulanmış resmî HLS yayınlarını kullanır
- Web sitesi veya YouTube açmaz; yalnızca yerel Media3 TV oynatıcısını kullanır
- İç içe oynatıcı sayfalarını üç seviyeye kadar izleyerek doğrudan HLS/DASH yayını arar
- Son eksiksiz kanal listesini cihazda saklar; eksik listeyi hiçbir zaman tam listenin üzerine yazmaz
- Büyük yazı, belirgin odak halkası ve 10-foot TV mesafesine uygun kanal paneli sunar
- Açılış splash ekranı, kanal adına göre arama ve siyah yayın yükleme ekranı sunar
- Hatalı yayınları otomatik bir kez yeniden dener; Kırmızı tuşla elle yenileme sağlar
- Animasyonlu menüler, mevcut kanal vurgusu, saat ve yükleme göstergesi içerir
- Orijinal, yakınlaştır ve ekranı doldur görüntü oranı seçeneklerini destekler
- İlk açılışta başlangıç davranışını sorar
- Son izlenen kanal, her zaman 1. kanal veya açılışta kanal seçimi seçeneklerini destekler
- Açılışta otomatik oynatma, kanal bilgi süresi ve görüntü oranı ayarları içerir
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
| Kırmızı | Geçerli yayını yeniden başlat |
| Yeşil veya Arama | Kanal adına göre ara |
| Sarı | Kanal listesini aç / kapat |
| Ayarlar veya Mavi | Ayarları aç / kapat |
| Bilgi | Kanal bilgisini göster |
| Oynat / Duraklat | Yayını oynat veya duraklat |

## Yayın kalitesi

Uygulama, kaynakta bulunan en iyi uyarlanabilir akışı Media3'e verir. 20 Temmuz 2026 taramasında site oynatıcılarının 212 tanesi doğrudan HLS yayın sağladı; TRT ve bazı ek doğrulanmış kanallar tercih edilen doğrudan yayınlarla açılır. Web sayfası ve YouTube geri dönüşü bilinçli olarak kapalıdır. Yerel HLS/DASH adresi bulunamayan kanalda siyah ekran üzerinde açık bir hata gösterilir.

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
