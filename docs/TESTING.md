# Doğrulama

## Otomatik kontroller

GitHub Actions her push ve pull request için şunları çalıştırır:

- `:app:assembleDebug`
- `:app:lint`
- Derlenen debug APK'yı `TurkiyeCanliTV-debug` iş akışı artifact'i olarak yükleme
- `AppUpdateManager` sürüm karşılaştırması için yerel birim testleri
- Sürüm iş akışında kalıcı anahtarla imzalı release APK ve SHA-256 üretimi

## Katalog araştırması — 20 Temmuz 2026

- Katalog satırı: 285
- Benzersiz kanal adı: 285
- Kaynak oynatıcı sayfası başarıyla okunan kayıt: 285
- Doğrudan HLS bulunan site oynatıcısı: 212
- YouTube, dış iframe veya yayıncı sayfası kullanan kayıt: 73
- Katalog sırası: TRT 1 ile başlayıp Türkmen Sport Tv ile bitiyor

Bu sayılar tarihsel doğrulama kaydıdır. Kaynak site ve yayıncılar değişebileceği için sürüm iddiası olarak kullanılmamalıdır.

## Çözümleyici regresyon kontrolü

Aşağıdaki kaynak biçimlerini temsil eden, yayın hakkı bakımcı tarafından doğrulanmış örnek kanallarla test edin:

1. Doğrudan HLS (`.m3u8`)
2. Doğrudan DASH (`.mpd`)
3. JavaScript içinde `\/`, `\u002F` veya `\x2F` ile kaçırılmış HLS
4. Percent-encoded medya URL'si
5. `iframe src`, `data-src` veya `source src` içindeki medya
6. Oynatıcı uç noktası başarısız olup kanal sayfasından bulunan medya
7. YouTube/tarayıcı kaynağı — yerel URL uydurulmadan açık hata göstermeli
8. Kapalı veya coğrafi engelli yayın — kontrollü hata ve yeniden deneme sunmalı

## Android TV kabul testi

1. Uygulamayı TV başlatıcısındaki Türkiye Canlı TV banner'ından açın.
2. Yeni splash ekranı ve üst durum şeridinin 720p/1080p/4K arayüz ölçeklerinde taşmadığını doğrulayın.
3. İlk açılış seçeneklerinin yalnızca bir kez gösterildiğini doğrulayın.
4. Son izlenen, 1. kanal ve kanal rehberi başlangıç seçeneklerini ayrı ayrı sınayın.
5. İzleme ekranında dört yön tuşunun kanal değiştirmediğini doğrulayın.
6. `Program + / -` ve numara tuşlarıyla kanal değiştirin.
7. OK ile rehberi açın; odağın mevcut kanala geldiğini ve listenin doğru satıra kaydığını doğrulayın.
8. Kanal oynarken rehberi açıp kapatın; oynatıcının yeniden yüklenmediğini doğrulayın.
9. Üst durum göstergesinin oynatma başladıktan sonra kendiliğinden gizlendiğini doğrulayın.
10. Arama sonucundan kanal seçin, aramayı temizleyin ve gerçek kanal numarasının korunduğunu doğrulayın.
11. Kırmızı, Yeşil, Sarı ve Mavi tuş görevlerini ayrı ayrı sınayın.
12. Ayar panelindeki tüm seçenekleri değiştirip kalıcı olduklarını doğrulayın.
13. Kanal değişiminde `Yükleniyor`, `Canlı` ve hata rozetlerinin doğru geçiş yaptığını doğrulayın.
14. Görüntü oranını Orijinal, Yakınlaştır ve Doldur arasında değiştirin.
15. Oynatma hatasında alternatif akışa otomatik geçişi ve Kırmızı tuşla elle yenilemeyi doğrulayın.
16. `Cihaz açılışı` ayarını etkinleştirip desteklenen test cihazında yeniden başlatma davranışını doğrulayın.
17. Uygulamayı kapatıp açarak son kanal ve ayarların korunduğunu doğrulayın.
18. İnterneti kapatıp daha önce kaydedilen eksiksiz katalogla açılışı doğrulayın.
19. Temiz kurulumda internet yokken kısmi kanal listesi yerine görünür hata gösterildiğini doğrulayın.
20. Sağlam bir kanalda manifest ön denetiminin ardından oynatmanın başladığını doğrulayın.
21. Bilerek erişilemeyen bir test kaynağının listeden çıkarıldığını ve sonraki kanala geçildiğini
    doğrulayın.
22. Uygulamayı yeniden açarak çıkarılan kanalın altı saatlik sağlık süresince gizli kaldığını;
    `Kanal listesini yenile` sonrasında yeniden sınandığını doğrulayın.
23. Daha yüksek sürümlü test Release'i yayımlayın; açılış denetiminin güncelleme penceresini
    gösterdiğini doğrulayın.
24. APK indirmesinde ilerleme göstergesini, SHA-256 doğrulamasını, bilinmeyen uygulama izni
    yönlendirmesini ve Android paket kurulum ekranını doğrulayın.
25. Değiştirilmiş APK veya yanlış `.sha256` dosyasının kuruluma gönderilmediğini doğrulayın.

## Yayın doğrulama sınırı

Kaynak yayınların çözünürlüğü, sürekliliği ve bölgesel erişimi yayıncı tarafından belirlenir. Başarılı build, tüm üçüncü taraf yayınların her an erişilebilir olduğu anlamına gelmez.
