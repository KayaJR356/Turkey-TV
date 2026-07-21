<div align="center">

<img src="docs/assets/banner.svg" alt="Türkiye Canlı TV — Android TV ve Google TV için kumanda odaklı canlı yayın oynatıcısı" width="100%">

# Türkiye Canlı TV

**Android TV ve Google TV için kumanda odaklı, tam ekran canlı yayın oynatıcısı.**

[![Android TV build](https://github.com/KayaJR356/Turkey-TV/actions/workflows/android.yml/badge.svg)](https://github.com/KayaJR356/Turkey-TV/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/github/license/KayaJR356/Turkey-TV?style=flat-square)](LICENSE)
[![Release](https://img.shields.io/github/v/release/KayaJR356/Turkey-TV?display_name=tag&include_prereleases&style=flat-square)](https://github.com/KayaJR356/Turkey-TV/releases)
[![Stars](https://img.shields.io/github/stars/KayaJR356/Turkey-TV?style=flat-square)](https://github.com/KayaJR356/Turkey-TV/stargazers)
[![Forks](https://img.shields.io/github/forks/KayaJR356/Turkey-TV?style=flat-square)](https://github.com/KayaJR356/Turkey-TV/forks)
[![Issues](https://img.shields.io/github/issues/KayaJR356/Turkey-TV?style=flat-square)](https://github.com/KayaJR356/Turkey-TV/issues)
[![Last commit](https://img.shields.io/github/last-commit/KayaJR356/Turkey-TV?style=flat-square)](https://github.com/KayaJR356/Turkey-TV/commits/main)
[![Top language](https://img.shields.io/github/languages/top/KayaJR356/Turkey-TV?style=flat-square)](https://github.com/KayaJR356/Turkey-TV)

</div>


Türkiye Canlı TV; canlı kanal kataloğunu yenileyen, uygun HLS/DASH/MP4 akışlarını Media3 ile oynatan ve TV kumandası için tasarlanmış açık kaynak bir Android uygulamasıdır. Büyük ekran odak davranışı, hızlı kanal seçimi, alternatif yayın geçişi ve çevrimdışı katalog önbelleğiyle koltuktan kullanıma odaklanır.

> [!IMPORTANT]
> Bu proje yayın içeriği barındırmaz veya yayın hakları sağlamaz. Kanal erişilebilirliği, görüntü kalitesi ve coğrafi kısıtlamalar ilgili yayıncıya bağlıdır.

## İçindekiler

- [Neden Türkiye Canlı TV?](#why)
- [Özellikler](#features)
- [Demo](#demo)
- [Kurulum](#installation)
- [Kullanım](#usage)
- [Proje yapısı](#structure)
- [Teknoloji yığını](#stack)
- [Yol haritası](#roadmap)
- [Katkı sağlama](#contributing)
- [Lisans](#license)
- [İletişim ve destek](#contact)
- [Teşekkürler](#acknowledgements)

<a id="why"></a>
## Neden Türkiye Canlı TV?

| İhtiyaç | Türkiye Canlı TV'nin yaklaşımı |
| --- | --- |
| TV'de kolay gezinme | D-pad, numara, renkli işlev ve medya tuşlarına uygun arayüz |
| Yerel oynatma | HLS/DASH akışlarını Media3/ExoPlayer ile tam ekranda oynatma |
| Güncel kanal listesi | Uygulama açılışında katalog yenileme ve eksik sonuçları reddetme |
| Kesintilere dayanıklılık | Son eksiksiz kataloğu cihazda saklama ve yayın hatasını yeniden deneme |
| Büyük ekran okunabilirliği | 10-foot arayüz, yüksek kontrastlı odak ve belirgin kanal paneli |

<a id="features"></a>
## ✨ Özellikler

- Android TV ve Google TV Leanback başlatıcı desteği
- Katalogdaki **285 kanalı** doğrulanmış sırayla gösterme
- HLS ve DASH akışlarını Media3 ile uyarlanabilir kalitede oynatma
- TRT kanalları için doğrulanmış resmî HLS akışlarını tercih etme
- İç içe oynatıcı sayfalarında üç seviyeye kadar doğrudan akış çözümleme
- Kanal adına göre arama ve numarayla doğrudan kanal seçimi
- Son izlenen kanal, başlangıç davranışı, otomatik oynatma ve bilgi süresi ayarları
- Orijinal, yakınlaştır ve ekranı doldur görüntü oranı seçenekleri
- Son eksiksiz kanal kataloğunu cihazda güvenli biçimde önbelleğe alma
- Bozuk akışta alternatif URL deneme ve komşu kanalları arka planda ön çözümleme
- Kırmızı tuşla elle yayın yenileme
- Cihaz açılışında uygulamayı otomatik başlatma seçeneği
- Oynatma başlayınca kendiliğinden gizlenen üst durum göstergesi
- Menü açıp kapatıldığında kesintisiz oynatma
- Her push ve pull request'te debug APK derleme ve Android Lint kontrolü

> [!NOTE]
> Kanal sayısı 20 Temmuz 2026 tarihli katalog doğrulamasına dayanır. Kaynak katalog değiştikçe sayı ve erişilebilirlik değişebilir.

<a id="demo"></a>
## 🎬 Demo

- **Sürüm sayfası:** [v3.3.0](https://github.com/KayaJR356/Turkey-TV/releases/tag/v3.3.0)
- **CI çıktısı:** Başarılı GitHub Actions çalışmasındaki `TurkiyeCanliTV-debug` artifact'i

<a id="installation"></a>
## Kurulum

### Kaynaktan derleme

#### Gereksinimler

- JDK 17
- Android SDK 35
- Gradle 8.9
- Git

```bash
git clone https://github.com/KayaJR356/Turkey-TV.git
cd Turkey-TV
gradle :app:assembleDebug
```

Derlenen APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

ADB ile bağlı TV cihazına yüklemek için:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> [!WARNING]
> GitHub Release sayfasındaki APK şu an **debug/test build** olarak yayımlanır ve üretim anahtarıyla imzalanmış mağaza sürümü değildir. İndirmeden sonra sağlanan SHA-256 checksum değerini doğrulayın.

Derleme ve lint kontrollerini birlikte çalıştırmak için:

```bash
gradle :app:assembleDebug :app:lint
```

<a id="usage"></a>
## 🎮 Kullanım

İlk açılışta başlangıç davranışını seçin. Ardından kumandanızla kanal değiştirebilir, kanal listesinde gezinebilir ve oynatma ayarlarını yönetebilirsiniz.

`Ayarlar > Cihaz açılışı` seçeneğini **Bu uygulamayı başlat** olarak ayarlarsanız destekleyen Android TV cihazlarında uygulama cihaz açıldıktan sonra otomatik başlar. Bazı üretici yazılımları arka plandan başlatmayı engelleyebilir.

| Tuş | İşlev |
| --- | --- |
| `Program + / -` | Sonraki / önceki kanal |
| `0–9` | Kanal numarasına doğrudan git |
| `OK`, Menü veya Rehber | Kanal listesini aç |
| Yön tuşları | Kanal ve ayar menülerinde gezin |
| Sağ veya Geri | Açık menüyü kapat |
| Kırmızı | Geçerli yayını yeniden başlat |
| Yeşil veya Arama | Kanal adına göre ara |
| Sarı | Kanal listesini aç / kapat |
| Ayarlar veya Mavi | Ayarları aç / kapat |
| Bilgi | Kanal bilgisini göster |
| Oynat / Duraklat | Yayını oynat veya duraklat |

Yayın çözümleme ve hata toleransı ayrıntıları için [mimari belgesine](docs/ARCHITECTURE.md), kabul adımları için [test belgesine](docs/TESTING.md) bakın.

<a id="structure"></a>
## 🗂️ Proje yapısı

```text
Turkey-TV/
├── .github/
│   ├── ISSUE_TEMPLATE/          # Yapılandırılmış issue formları
│   ├── workflows/android.yml    # Build, lint ve APK artifact iş akışı
│   ├── workflows/release.yml    # Sürüm değişince debug prerelease yayımlama
│   └── PULL_REQUEST_TEMPLATE.md
├── app/
│   ├── build.gradle             # Android uygulama yapılandırması
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/tv/kaya/turksat/
│       │   ├── BootReceiver.java
│       │   ├── Channel.java
│       │   ├── ChannelRepository.java
│       │   ├── MainActivity.java
│       │   └── SplashActivity.java
│       └── res/                 # TV arayüzü kaynakları
├── docs/
│   ├── ARCHITECTURE.md
│   └── TESTING.md
├── build.gradle
└── settings.gradle
```

<a id="stack"></a>
## 🧰 Teknoloji yığını

| Katman | Teknoloji |
| --- | --- |
| Dil | Java |
| Platform | Android TV / Google TV |
| Android SDK | Compile/Target 35, Minimum 23 |
| Oynatıcı | AndroidX Media3 1.5.1 (ExoPlayer, HLS, DASH, UI) |
| Arayüz | Android Views, AppCompat 1.7.0, Leanback launcher |
| Build | Gradle 8.9, Android Gradle Plugin 8.7.3, JDK 17 |
| CI | GitHub Actions |

<a id="roadmap"></a>
## 🛣️ Yol haritası

- [x] Kumanda odaklı tam ekran TV arayüzü
- [x] HLS/DASH çözümleme ve Media3 oynatma
- [x] Kanal arama, ayarlar ve katalog önbelleği
- [x] CI üzerinde debug build ve Android Lint
- [x] Sürümlenmiş debug GitHub Release ve SHA-256 checksum
- [x] Alternatif akış geçişi ve komşu kanal ön çözümlemesi
- [x] İsteğe bağlı cihaz açılışında otomatik başlatma
- [ ] Üretim anahtarıyla imzalı kararlı APK dağıtımı
- [ ] Birim ve entegrasyon test kapsamını genişletme

Planlanan çalışma için [issue'lara](https://github.com/KayaJR356/Turkey-TV/issues) bakın. Yeni bir yön önermeden önce [feature request](https://github.com/KayaJR356/Turkey-TV/issues/new?template=feature_request.yml) açın.

<a id="contributing"></a>
## 🤝 Katkı sağlama

Katkılar değerlidir. Başlamadan önce [CONTRIBUTING.md](CONTRIBUTING.md) ve [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) belgelerini okuyun.

1. Uygun issue formuyla değişikliği tartışın.
2. Repository'yi fork'layın ve odaklı bir branch oluşturun.
3. Değişikliğinizi build ve lint ile doğrulayın.
4. Küçük, açıklayıcı commit'ler oluşturun.
5. Pull request şablonunu eksiksiz doldurun.

Güvenlik açığı bildirimleri için issue açmayın; [SECURITY.md](SECURITY.md) politikasını izleyin.

<a id="license"></a>
## 📄 Lisans

Kaynak kod [MIT Lisansı](LICENSE) ile sunulur.

Kanal adları, logoları, yayınları ve üçüncü taraf hizmetleri kendi sahiplerinin mülkiyetindedir. MIT Lisansı üçüncü taraf içerikleri için kullanım veya dağıtım hakkı vermez.

<a id="contact"></a>
## 💬 İletişim ve destek

- Hata bildirimi: [Bug report](https://github.com/KayaJR356/Turkey-TV/issues/new?template=bug_report.yml)
- Özellik önerisi: [Feature request](https://github.com/KayaJR356/Turkey-TV/issues/new?template=feature_request.yml)
- Kullanım desteği: [SUPPORT.md](SUPPORT.md)
- Güvenlik bildirimi: [SECURITY.md](SECURITY.md)
- Bakımcı: [@KayaJR356](https://github.com/KayaJR356)

<a id="acknowledgements"></a>
## 🙏 Teşekkürler

- [AndroidX Media3](https://developer.android.com/media/media3) ekibine
- Android TV ve açık kaynak Android ekosistemine
- Katalog bilgisini sağlayan kaynaklara ve yayınlarını erişilebilir kılan yayıncılara
- Hata bildiren, belge geliştiren ve kod katkısı sağlayan tüm katılımcılara

---

<div align="center">
Türkiye Canlı TV'yi yararlı bulduysanız repository'yi ⭐ ile destekleyebilirsiniz.
</div>
