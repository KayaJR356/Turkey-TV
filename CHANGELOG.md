# Değişiklik Günlüğü

Bu projedeki önemli değişiklikler bu dosyada belgelenir.

Biçim [Keep a Changelog](https://keepachangelog.com/tr-TR/1.1.0/) yaklaşımını izler ve proje [Semantic Versioning](https://semver.org/lang/tr/) kullanmayı hedefler.

## [Unreleased]

### Added

- Profesyonel README, katkı rehberi, davranış kuralları, güvenlik ve destek politikaları
- Yapılandırılmış bug report ve feature request formları
- Pull request şablonu

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

[Unreleased]: https://github.com/KayaJR356/Turkey-TV/compare/v3.3.0...HEAD
[3.3.0]: https://github.com/KayaJR356/Turkey-TV/releases/tag/v3.3.0
[3.2.0]: https://github.com/KayaJR356/Turkey-TV/releases/tag/v3.2.0
