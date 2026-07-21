# Mimari

## Katalog

1. `ChannelRepository`, masaüstü tarayıcı kimliğiyle `https://www.canlitv.diy/tr` sayfasını indirir.
2. jsoup DOM ayrıştırıcısı, sınıfların sırasından ve HTML öznitelik düzeninden bağımsız olarak
   `ft_<oynatıcı-kimliği>` ve `tv` sınıflı satırlardan ad, sayfa bağlantısı, kategori ve oynatıcı kimliği alır.
3. Kanallar sayfadaki sıraya göre 1'den başlayarak numaralandırılır.
4. En az 250 kayıt dönmeyen yanıt eksik kabul edilir ve kaydedilmez.
5. Ağ veya site hatasında yalnızca daha önce kaydedilmiş eksiksiz katalog kullanılır. Eksiksiz katalog yoksa kullanıcıya hata gösterilir.

## Oynatma

1. Doğrulanmış resmî akışlar varsa kanal adına göre tercih edilir.
2. Diğer kanallarda kaynak sitenin oynatıcı uç noktası indirilir.
3. HLS (`.m3u8`), DASH (`.mpd`) ve Media3'ün desteklediği doğrudan MP4 kaynakları aranır.
4. JavaScript kaçışları, HTML entity'leri, percent-encoded URL'ler ve sınırlı base64 oynatıcı değerleri normalize edilir.
5. `iframe`, `video`, `source`, `data-src` ve `data-lazy-src` kaynakları en fazla üç seviye izlenir.
6. Oynatıcı uç noktası ve kanal sayfası paralel çözülür; ilk geçerli medya kaynağı kullanılır.
7. Reklam/izleme adresleri ile YouTube ve tarayıcıya özel kaynaklar yerel medya URL'si olarak kabul edilmez.
   Bu kanallar listeden çıkarılmak yerine yalnızca `canlitv.diy` üst sayfasına izin veren güvenli
   uygulama içi WebView oynatıcısına aktarılır.
8. Media3 için HLS/DASH MIME türü açıkça atanır. Kaynak uyumluluğu için katalog `Referer` başlığı korunur; farklı CDN'leri engelleyebilen zorunlu `Origin` başlığı uygulanmaz.
9. Kanal değişiminde eski çözümleme sonucu kuşak numarasıyla iptal edilir; geç tamamlanan ağ yanıtı yeni kanalın üzerine yazamaz.
10. Başarısız medya URL'si oturum boyunca dışlanır; aynı kanal için bulunan sonraki alternatif otomatik denenir.
11. Mevcut kanal hazır olduğunda önceki ve sonraki kanal arka planda ön çözümlenir.
12. Çözülen HLS/DASH/MP4 adresi oynatıcıya verilmeden önce gerçek bir HTTPS medya veya manifest
    yanıtıyla sınanır.
13. Katalog dört iş parçacıklı düşük öncelikli sağlık taramasından geçirilir. Tarama yalnızca
    bulunan yerel medya adreslerini önbelleğe alır; başarısız sonuçlar kanalı listeden kaldırmaz.
14. Seçili kanalın yerel alternatifleri başarısız olursa kaynak sitenin oynatıcısı otomatik açılır.
15. Kaynak oynatıcı HTTP/ağ/TLS hatası verirse aynı kanalın katalog sayfası ikinci web fallback
    olarak denenir; her iki yol başarısız olsa da kanal listede kalır.
16. Ağ bağlantısı geri geldiğinde katalog veya seçili yerel yayın otomatik yeniden denenir.
17. Web oynatıcı `Program + / -` sonucunu ana etkinliğe döndürerek katalog sırasını korur.
18. Her kanal `Denetleniyor`, `Yerel`, `Web` veya `Geçici sorun` durumuyla gösterilir; durum hiçbir
    zaman kataloğun silinmesi ya da gizlenmesi için kullanılmaz.
19. Media3 oynatıcı bir `MediaSession` ile sistem oynatma denetimlerine kanal ve program metadatası sunar.
20. Kullanıcı kalite üst sınırı ile mevcut ses/altyazı dilini `TrackSelectionParameters` üzerinden seçebilir.

> [!IMPORTANT]
> Kaynak sayfa oynatılabilir bir HLS/DASH/MP4 akışı vermiyorsa, yayın kapalıysa veya erişim yayıncı tarafından engelleniyorsa uygulama akış üretemez. Çözümleyici yalnızca mevcut ve izin verilen medya kaynaklarını bulur.

## TV arayüzü

- İzleme ekranında yön tuşları yanlışlıkla kanal değiştirmez.
- Kanal değişimi `KEYCODE_CHANNEL_UP/DOWN` veya numara tuşlarıyla yapılır.
- Üst durum şeridi kanal numarası, kanal adı, canlı/yükleniyor durumu ve saati gösterir.
- Üst durum şeridi oynatma başladıktan sonra otomatik gizlenir.
- Kanal rehberi mevcut kanala odaklanarak açılır ve listeyi ilgili satıra kaydırır.
- Kanal rehberini açıp kapatmak mevcut Media3 oturumunu yeniden hazırlamaz.
- Kanal ve ayar panelleri yarı saydam, yüksek kontrastlı 10-foot tasarıma sahiptir.
- Arayüz ölçüleri temel TV tuvalinin %82'sine ölçeklenir ve küçük görünüm alanlarında ek uyarlama yapılır.
- Kırmızı yayın yenilemeyi, Yeşil aramayı, Sarı kanal rehberini ve Mavi ayarları açar.
- Yükleme, hata ve numara girişleri ayrı kartlarla görünür; video üzerinde okunabilirlik için ekran gölgesi kullanılır.
- İlk açılış seçimi, son kanal, otomatik oynatma, cihaz açılışında başlatma, bilgi süresi ve görüntü oranı `SharedPreferences` içinde tutulur.
- Görüntü oranı Media3 `FIT`, `ZOOM` ve `FILL` modlarına eşlenir.
- Kaynak katalogdaki `data-kat` değeri uygulama kategorilerine eşlenir; eski önbelleklerde ad tabanlı
  sınıflandırma geriye uyumlu yedek olarak kullanılır.
- Favoriler, son izlenenler ve kanal kilitleri normalize edilmiş kanal anahtarıyla saklanır; katalog
  sırası değişse de kullanıcı verisi korunur.
- Destekleyen Android TV yazılımlarında yerel ve web oynatıcı Android görüntü içinde görüntü moduna geçebilir.

## Program rehberi

1. `EpgRepository`, sıkıştırılmış XMLTV verisini HTTPS üzerinden en fazla altı saatte bir yeniler.
2. Sıkıştırılmış indirme 8 MB ile sınırlıdır ve SAX ile akış halinde ayrıştırılır.
3. Yalnızca son iki saat ile gelecek 30 saat arasındaki kayıtlar tutulur.
4. `TR -`, HD/FHD/UHD/4K ekleri ve Türkçe karakter farkları normalize edilerek katalog adıyla eşleştirilir.
5. Kanal başına en fazla 12 yakın program cihazda önbelleğe alınır; ağ hatasında bu veri kullanılmaya devam eder.
6. EPG kaynağında bulunmayan kanallar program bilgisi olmadan normal biçimde oynatılır.

## Kullanıcı verisi ve ebeveyn denetimi

- Ebeveyn PIN'i düz metin saklanmaz; rastgele 24 bayt tuz ve 50.000 turlu SHA-256 türetimi kullanılır.
- Kilitli kanal PIN doğrulanmadan ayarlanmaz; doğrulama yalnızca açık uygulama oturumu boyunca hatırlanır.
- JSON yedeği yalnızca beyaz listedeki ayar, favori, geçmiş, kilit ve PIN değerlerini içerir.
- İçe aktarma 1 MB ile sınırlıdır ve bilinmeyen anahtarları yok sayar.

## Dayanıklılık ve güvenlik

- Bağlantı ve okuma zaman aşımı vardır.
- Yalnızca HTTPS katalog, oynatıcı ve medya adresleri kabul edilir.
- Web fallback yalnızca `canlitv.diy` üst düzey sayfalarını açar; HTTP, dosya/içerik erişimi,
  karma içerik, yeni pencere, kamera ve mikrofon izinleri engellenir.
- Oynatıcı belgeleri 1,25 MB ile sınırlandırılır.
- Çözümleme döngüleri ziyaret edilen URL kümesi ve derinlik sınırıyla engellenir.
- Son eksiksiz katalog çevrimdışı başlangıcı destekler.
- Süreli medya URL'leri yalnızca çalışan uygulama oturumunda saklanır.
- Media3 hatasında iki alternatif akış denemesi yapılır; kullanıcı Kırmızı tuşla dışlanan kaynakları temizleyip tekrar deneyebilir.
- XMLTV ayrıştırıcısında DOCTYPE ve harici varlıklar kapatılır.
- Cihaz açılışında başlatma, açıkça etkinleştirildiğinde ilk sinyalin ardından 12 ve 32 saniyelik
  iki ek AlarmManager denemesi yapar. Üretici yazılımının arka plan etkinliği politikasını aşamaz.

## Uygulama içi güncelleme

1. `AppUpdateManager`, açılışta ve Ayarlar içindeki elle denetimde GitHub Releases API'sini okur.
2. Taslak olmayan kararlı ve ön sürümler semantik etiketle `BuildConfig.VERSION_NAME` değerine
   göre karşılaştırılır.
3. Yalnızca HTTPS GitHub/GitHub CDN adresleri ve en fazla 250 MB APK kabul edilir.
4. APK boyutu GitHub varlık bilgisiyle, içeriği GitHub `digest` alanı veya eş adlı `.sha256`
   dosyasıyla doğrulanır.
5. Doğrulanan APK, `FileProvider` üzerinden Android paket yükleyicisine salt okunur URI ile verilir.
6. Android 8 ve üzerinde gerekirse uygulamaya özel bilinmeyen kaynak izni ekranı açılır.
7. Normal uygulamalar sessiz paket kurulumu yapamadığı için son onay sistem paket yükleyicisindedir.

Android mevcut paketin üzerine yalnızca aynı sertifika ve daha yüksek `versionCode` ile imzalanmış
APK kurar. GitHub Actions sürüm iş akışı bu nedenle repository secret içinde tutulan sabit anahtarı
kullanır. Ayrıntılar `docs/RELEASING.md` belgesindedir.
