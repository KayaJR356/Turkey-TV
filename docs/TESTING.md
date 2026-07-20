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
8. Kanal panelinin üstündeki sabit Ayarlar satırını yön tuşlarıyla seçin; Mavi tuşla da doğrudan açın.
9. Yeşil tuşla kanal adına göre arama yapın, aramayı temizleyin ve doğru kanal numarasının korunduğunu doğrulayın.
10. Kırmızı, Yeşil, Sarı ve Mavi tuş görevlerini ayrı ayrı sınayın.
11. Doğrudan HLS, YouTube ve dış iframe kullanan örnekleri açın; uygulamanın hiçbirinde web sayfası/YouTube göstermediğini doğrulayın.
12. Kanal değişiminde yayın hazır olana kadar siyah ekran ve `Yayın yükleniyor…` mesajını doğrulayın.
13. Görüntü oranını Orijinal, Yakınlaştır ve Doldur arasında değiştirin.
14. Panel, kanal bilgisi ve splash geçişlerinin akıcı; mevcut kanal ve saatin görünür olduğunu doğrulayın.
15. Uygulamayı kapatıp açarak splash ekranını, son kanalı ve ayarların korunduğunu doğrulayın.
16. İnterneti kapatıp daha önce kaydedilen eksiksiz katalogla açılışı doğrulayın.
17. Temiz kurulumda internet yokken kısmi kanal listesi yerine görünür hata gösterildiğini doğrulayın.
