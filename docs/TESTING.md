# Doğrulama

## Otomatik kontroller

GitHub Actions her push ve pull request için şunları çalıştırır:

- `:app:assembleDebug`
- `:app:lint`
- Derlenen debug APK'yı `TurkiyeCanliTV-debug` iş akışı artifact'i olarak yükleme
- `AppUpdateManager` sürüm karşılaştırması için yerel birim testleri
- `ChannelRepository` katalog sınıf sırası, ad fallback'i, yinelenen kimlik ve güvenilmeyen bağlantı testleri
- `Channel` kategori eşleştirme ve katalog kategori önceliği testleri
- `EpgRepository` XMLTV zaman ayrıştırma, `TR -`/HD ad eşleştirme ve şimdi/sonra program testleri
- Sürüm iş akışında kalıcı anahtarla imzalı release APK ve SHA-256 üretimi

## Katalog araştırması — 21 Temmuz 2026

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
7. YouTube/tarayıcı kaynağı — yerel URL uydurulmadan güvenli web oynatıcıya geçmeli
8. Kapalı veya coğrafi engelli yayın — kanal listede kalmalı ve web fallback/yeniden deneme sunmalı

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
21. Bilerek yerel akışı çözülemeyen bir kanalın listede kaldığını ve web oynatıcıya geçtiğini doğrulayın.
22. Web oynatıcıda `Program + / -` ile katalogdaki sonraki ve önceki kanala geçildiğini doğrulayın.
23. Ağ bağlantısını kesip geri getirerek geçerli yayının otomatik yenilendiğini doğrulayın.
24. 720p, 1080p ve 4K görünüm alanlarında kanal/ayar panellerinin ekranın aşırı bölümünü kaplamadığını doğrulayın.
25. `Cihaz açılışı` seçeneğini etkinleştirin; yeniden başlatmada ilk ve gecikmeli açılış denemelerinden
    sonra uygulamanın üretici TV ekranının önünde kaldığını doğrulayın.
26. Daha yüksek sürümlü test Release'i yayımlayın; açılış denetiminin güncelleme penceresini
    gösterdiğini doğrulayın.
27. APK indirmesinde ilerleme göstergesini, SHA-256 doğrulamasını, bilinmeyen uygulama izni
    yönlendirmesini ve Android paket kurulum ekranını doğrulayın.
28. Değiştirilmiş APK veya yanlış `.sha256` dosyasının kuruluma gönderilmediğini doğrulayın.
29. Kategori sekmelerinin kaynak katalog sınıflandırmasına göre doğru kanalları gösterdiğini doğrulayın.
30. Bir kanal satırında OK tuşunu basılı tutarak favoriyi değiştirin; Favoriler filtresini ve uygulama
    yeniden başladıktan sonra kalıcılığı doğrulayın.
31. Birkaç kanal izleyip Son İzlenen filtresinin en yeni kanal önce olacak şekilde sıralandığını doğrulayın.
32. EPG yenilemesinden sonra kanal satırı, üst şerit, Bilgi kartı ve program akışında şimdi/sonra
    verisinin tutarlı olduğunu doğrulayın; çevrimdışıyken önbelleği yeniden sınayın.
33. Kalite sınırlarını değiştirin; çoklu ses/altyazı sunan örnekte dil seçimini ve kapalı altyazıyı sınayın.
34. Sistem medya tuşlarının MediaSession oynatmasını ve kanal/program metadatasını doğrulayın.
35. Destekleyen cihazda Ana Ekran tuşuyla görüntü içinde görüntüye geçişi, dönüşü ve oynatmanın
    kesilmemesini hem yerel hem web oynatıcıda sınayın.
36. Ebeveyn PIN'i oluşturun, kanalı kilitleyin, yanlış/doğru PIN'i ve uygulama yeniden başladıktan
    sonra yeniden doğrulama gereksinimini sınayın.
37. Ayar yedeğini dışa aktarın; favori/ayar değiştirip yedeği içe alın ve izin verilen verilerin geri geldiğini doğrulayın.
38. Cihaz açılışını etkinleştirip yeniden başlatın; ilk, 12 saniyelik ve 32 saniyelik denemelerden
    sonra üretici TV uygulamasının Türkiye Canlı TV'nin önüne geçmediğini doğrulayın.

## Yayın doğrulama sınırı

Kaynak yayınların çözünürlüğü, sürekliliği ve bölgesel erişimi yayıncı tarafından belirlenir. Başarılı build, tüm üçüncü taraf yayınların her an erişilebilir olduğu anlamına gelmez.
