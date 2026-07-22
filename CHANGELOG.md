# Değişiklik Günlüğü

Bu projedeki önemli değişiklikler bu dosyada belgelenir.

Biçim [Keep a Changelog](https://keepachangelog.com/tr-TR/1.1.0/) yaklaşımını izler ve proje [Semantic Versioning](https://semver.org/lang/tr/) kullanmayı hedefler.

## [Unreleased]

### Added

- Profesyonel README, katkı rehberi, davranış kuralları, güvenlik ve destek politikaları
- Yapılandırılmış bug report ve feature request formları
- Pull request şablonu

## [3.5.1] - 2026-07-22

### Added

- APK içine gömülü, internet ve önbellekten bağımsız 285 kanallık başlangıç kataloğu
- API 23 emülatöründe gerçek MainActivity açılışı ve gömülü katalog enstrümantasyon testleri
- Yakalanmayan çökmeleri ve katalog hatalarını cihazda saklayan tanılama kaydı
- Ayarlar panelinde son tanılama kaydını görüntüleme

### Changed

- Uygulama önce yerel/gömülü kataloğu anında açıyor, canlı kataloğu arka planda yeniliyor
- İlk açılıştaki ağır yayın sağlık taraması 285 yerine ilk 24 kanalla sınırlandı
- Canlı katalog yenilenirken seçili kanal adı ve oynatma oturumu korunuyor
- Kanal menüsü kompakt başlık, kategori çipleri, hızlı eylemler ve ayrı numara/program/durum
  alanlarına sahip kumanda odaklı kanal kartlarıyla baştan tasarlandı

### Fixed

- Ağ veya bozuk önbellek durumunda kanal listesinin boş kalması
- Bazı üretici Android TV yazılımlarında MediaSession başlatma hatasının tüm uygulamayı düşürmesi
- Katalog indirme/ayrıştırma hatalarının hiçbir tanılama izi bırakmadan sessizce yutulması
- Android 6 WebView yolunda YouTube, Castr ve diğer güvenilir dış oynatıcıların engellenmesi
- Bir reklam veya alt kaynak SSL hatasının açık web kanalının tamamını kapatması
- Eski web içeriklerinin güvenli uyumluluk modunda video yükleyememesi

## [3.5.0] - 2026-07-21

### Added

- Favori kanallar ve son izlenen 20 kanal filtresi
- Kaynak katalog etiketlerine dayalı Ulusal, Haber, Spor, Çocuk, Belgesel, Dini ve Yerel kategorileri
- Çevrimdışı önbellekli şimdi/sonra program rehberi ve sekiz programlık kanal akışı
- Kanal satırlarında yerel, web ve geçici sorun yayın durumu
- Otomatik, 480p, 720p ve 1080p yayın kalitesi sınırı
- Yayında bulunan ses ve altyazı dillerini seçme
- Android MediaSession sistem oynatma denetimi ve destekleyen cihazlarda görüntü içinde görüntü
- Ebeveyn PIN'i, kanal kilidi ve oturumluk PIN doğrulaması
- Favori, geçmiş, kilit ve uygulama ayarlarını JSON dosyasına yedekleme/geri yükleme
- Kanal kategori ve XMLTV program rehberi eşleştirme birim testleri

### Changed

- Kanal rehberi kategori sekmeleri, iki satırlı program bilgisi ve daha küçük eylem satırlarıyla yeniden düzenlendi
- Üst durum şeridi geçerli programı ve ilerleme yüzdesini gösteriyor
- Kanalın Media3 metadatası sistem medya denetimlerinde kanal/program adıyla yayımlanıyor
- Cihaz açılışında başlatma, üretici TV uygulamasına karşı 12 ve 32 saniyelik iki ek deneme yapıyor
- GitHub Release notları güncel CHANGELOG sürümünden otomatik üretiliyor

### Fixed

- `TR -` önekli program rehberi kanal adlarının katalog adlarıyla eşleşmemesi
- Kategorilerin yalnızca kanal adından tahmin edilmesi
- Android 7 cihazlarında yeni koleksiyon yardımcılarının kullanılması

### Security

- Ebeveyn PIN'i cihazda rastgele tuz ve 50.000 turlu SHA-256 türetimiyle saklanıyor
- XMLTV ayrıştırıcısında harici varlıklar ve DOCTYPE devre dışı bırakılıyor
- İçe aktarılan yedek yalnızca izin verilen ayar anahtarlarını kabul ediyor ve 1 MB ile sınırlandırılıyor

## [3.4.0] - 2026-07-21

### Added

- GitHub Release sürümlerini otomatik denetleyen uygulama içi güncelleme sistemi
- APK boyutu ve SHA-256 özeti doğrulandıktan sonra Android paket yükleyicisini açma
- Android TV Ayarlar panelinden elle güncelleme denetimi
- Bütün katalog için arka planda yayın sağlık taraması
- Kalıcı repository secret anahtarıyla imzalı Release APK üretme iş akışı
- Yerel medya bulunamadığında kaynak sitenin oynatıcısını açan güvenli uygulama içi web fallback
- Kaynak oynatıcı yanıt vermezse aynı kanalın katalog sayfasına otomatik ikinci web fallback
- Katalog HTML değişikliklerini kapsayan jsoup ayrıştırıcı ve birim testleri
- Bağlantı geri geldiğinde geçerli yayını otomatik yeniden deneme

### Changed

- HLS, DASH ve MP4 kaynakları oynatılmadan önce gerçek ağ yanıtıyla doğrulanıyor
- Kaynak katalogdaki kanallar sağlık sonucu ne olursa olsun listede tutuluyor
- Kanal sınıf sırası, bağlantı başlığı veya görünür ad biçimi değişse de katalog dayanıklı ayrıştırılıyor
- TV arayüzü, kanal satırları ve açılış ekranı daha kompakt ve ekran boyutuna uyarlanabilir hâle getirildi
- Cihaz açılışında başlatma, üretici TV uygulamasının öne geçmesine karşı gecikmeli ikinci deneme yapıyor
- Web oynatıcıda `Program + / -` ile sonraki veya önceki kanala geçilebiliyor

### Fixed

- Sağlık taraması başarısız olan kanalların katalogdan eksilmesi
- YouTube, dış iframe veya tarayıcı gerektiren kanalların hiç açılamaması
- Bazı TV yazılımlarında cihaz açılışı seçeneğinin varsayılan TV uygulamasının arkasında kalması

### Security

- Güncelleme indirmeleri yalnızca HTTPS GitHub adresleriyle ve 250 MB sınırıyla kısıtlandı
- APK, `FileProvider` içerik URI'siyle ve geçici okuma izniyle paket yükleyicisine aktarılıyor

> [!IMPORTANT]
> Önceki debug anahtarıyla kurulmuş APK'lar, yeni kalıcı anahtarla imzalanmış `v3.4.0` sürümüne
> yerinde yükseltilemez. Kullanıcı eski paketi bir kez kaldırıp imzalı sürümü yeniden kurmalıdır.

## [3.3.0] - 2026-07-21

### Added

- Destekleyen Android TV cihazlarında açılışta uygulamayı otomatik başlatma ayarı
- Komşu kanallar için arka planda yayın URL'si ön çözümlemesi
- Başarısız bir akıştan sonra aynı kanalın alternatif medya URL'sine otomatik geçiş

### Changed

- Uygulama görünen adı **Türkiye Canlı TV** olarak güncellendi
- Kanal kaynakları paralel çözümlenerek başarısız uç noktaların bekleme süresi kısaltıldı
- Bağlantı ve okuma zaman aşımı değerleri TV kanal geçişine uygun hâle getirildi

### Fixed

- Oynatma başladıktan sonra üst durum göstergesinin ekranda kalması
- Kanal menüsü açılıp kapatılırken mevcut kanalın yeniden hazırlanması
- Eski veya erişilemeyen ilk medya URL'sinin aynı kanalda tekrar tekrar denenmesi

## [3.2.0] - 2026-07-21

> [!NOTE]
> GitHub Release etiketi: [`v3.2.0`](https://github.com/KayaJR356/Turkey-TV/releases/tag/v3.2.0). Yayımlanan APK debug/test build'dir; üretim anahtarıyla imzalanmış mağaza sürümü değildir.

### Added

- Kanal, oynatma durumu ve saati gösteren yeni üst durum şeridi
- Yeniden tasarlanan kanal rehberi, ayar paneli, yükleme kartı, splash ekranı, ikon ve TV banner'ı
- Percent-encoded, JavaScript kaçışlı, base64 ve lazy-loaded medya URL'leri için güvenli çözümleme
- Oynatıcı uç noktası sonuç vermezse kanal sayfasını deneyen aynı sistemli fallback
- Doğrudan MP4 ve açık HLS/DASH MIME türü desteği

### Changed

- Kanal rehberi açıldığında odağın mevcut kanala gelmesi sağlandı
- Üçüncü taraf CDN'leri engelleyebilen genel `Origin` ve `Referer` oynatma başlıkları kaldırıldı
- Çözümleme derinliği beş seviye ve indirilen oynatıcı belgesi 2 MB ile sınırlandı
- Repository içindeki sürümsüz `releases/TurksatTV.apk` kaldırıldı

### Fixed

- Dış iframe, `data-src`, `source` ve kaçırılmış medya URL'lerinin bulunamaması
- Bazı doğrudan yayınların yanlış genel HTTP başlıkları nedeniyle açılamaması
- Kanal rehberinin her açılışta arama düğmesine odaklanması

## [3.1.0] - 2026-07-20

Bu kayıt `app/build.gradle` içindeki `versionName` ve repository commit geçmişine dayanır.

### Added

- Android TV ve Google TV için kumanda odaklı tam ekran oynatıcı
- 285 kanallık katalog yenileme ve son eksiksiz katalog önbelleği
- HLS/DASH akış çözümleme ve Media3 adaptif oynatma
- Kanal arama, numarayla seçim ve renkli kumanda tuşları
- Başlangıç, otomatik oynatma, bilgi süresi ve görüntü oranı ayarları
- Splash ekranı, kanal bilgi kartı, saat ve yükleme göstergesi
- GitHub Actions ile debug APK ve Android Lint doğrulaması

### Changed

- Web sayfası ve YouTube geri dönüşleri kaldırılarak oynatma yerel Media3 akışlarıyla sınırlandı
- TV odak davranışı, panel animasyonları ve hata geri bildirimi geliştirildi

### Fixed

- Splash etkinliği için Media3 kararsız API denetimi düzeltildi

## Sürümleme politikası

- `MAJOR`: Geriye dönük uyumsuz davranış veya yapı değişiklikleri
- `MINOR`: Geriye uyumlu özellikler
- `PATCH`: Geriye uyumlu hata ve güvenlik düzeltmeleri

Yeni bir release yayımlanırken tarih, karşılaştırma bağlantıları ve migration notları bu dosyaya eklenmelidir.

[Unreleased]: https://github.com/KayaJR356/Turkey-TV/compare/v3.5.1...HEAD
[3.5.1]: https://github.com/KayaJR356/Turkey-TV/releases/tag/v3.5.1
[3.5.0]: https://github.com/KayaJR356/Turkey-TV/releases/tag/v3.5.0
[3.4.0]: https://github.com/KayaJR356/Turkey-TV/releases/tag/v3.4.0
[3.3.0]: https://github.com/KayaJR356/Turkey-TV/releases/tag/v3.3.0
[3.2.0]: https://github.com/KayaJR356/Turkey-TV/releases/tag/v3.2.0
