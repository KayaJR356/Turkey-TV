# Doğrulama

## Otomatik kontroller

GitHub Actions her push ve pull request için şunları çalıştırır:

- `:app:assembleDebug`
- `:app:lint`
- Derlenen debug APK'yı `TurkiyeTV-debug` iş akışı çıktısı olarak yükleme

## Katalog araştırması — 20 Temmuz 2026

- Katalog satırı: 285
- Benzersiz kanal adı: 285
- Kaynak oynatıcı sayfası başarıyla okunan kayıt: 285
- Doğrudan HLS bulunan site oynatıcısı: 212
- YouTube, dış iframe veya yayıncı sayfası kullanan kayıt: 73
- Katalog sırası: TRT 1 ile başlayıp Türkmen Sport Tv ile bitiyor

Kaynak yayınların çözünürlüğü yayıncı tarafından belirlenir. Kabul testinde uygulamanın seçtiği oynatma yolu ve TV odak davranışı ayrıca doğrulanmalıdır.

## Android TV kabul testi

1. Uygulamayı TV başlatıcısındaki yeni Türkiye TV banner'ından açın.
2. İlk açılış seçeneklerinin yalnızca bir kez gösterildiğini doğrulayın.
3. Son izlenen, 1. kanal ve kanal listesi başlangıç seçeneklerini ayrı ayrı sınayın.
4. İzleme ekranında dört yön tuşunun kanal değiştirmediğini doğrulayın.
5. `Program + / -` ile kanal değiştirin.
6. `2`, ardından kısa bekleme ile Show TV'ye; `285` ile Türkmen Sport Tv'ye gidin.
7. OK ile listeyi açın, yön tuşlarıyla ekran dışındaki satırlara ilerleyin ve OK ile seçin.
8. Ayarlar/Mavi tuşuyla ayar panelini açıp tüm seçenekleri değiştirin.
9. Doğrudan HLS, YouTube ve dış iframe kullanan örnek kanalları sınayın.
10. Uygulamayı kapatıp açarak son kanal ve ayarların korunduğunu doğrulayın.
11. İnterneti kapatıp daha önce kaydedilen eksiksiz katalogla açılışı doğrulayın.
12. Temiz kurulumda internet yokken kısmi kanal listesi yerine görünür hata gösterildiğini doğrulayın.
