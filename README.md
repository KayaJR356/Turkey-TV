<div align="center">

# Türkiye TV

**Android TV ve Google TV için kumanda odaklı, tam ekran canlı Türkiye kanalları oynatıcısı.**

<!-- TODO: Add a 1280×640 project banner at docs/assets/banner.png. -->

> [!NOTE]
> **Banner:** TODO — Uygulamanın gerçek TV arayüzünü gösteren doğrulanmış bir banner eklenecek.

[![Android TV build](https://github.com/KayaJR356/Turkey-TV/actions/workflows/android.yml/badge.svg)](https://github.com/KayaJR356/Turkey-TV/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-2ea44f.svg)](LICENSE)
![Android](https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Java-Android-ED8B00?logo=openjdk&logoColor=white)

[✨ Özellikler](#özellikler) · [🚀 Kurulum](#kurulum) · [🧭 Kullanım](#kullanım) · [🤝 Katkı](#katkı-sağlama) · [🔒 Güvenlik](SECURITY.md)

</div>

> [!IMPORTANT]
> Türkiye TV yayın içeriğinin sahibi veya dağıtıcısı değildir. Kanal adları, yayınlar ve üçüncü taraf oynatıcılar kendi hak sahiplerine aittir; erişilebilirlik ve kalite yayıncıya bağlıdır.

## İçindekiler

- [Neden Türkiye TV?](#neden-türkiye-tv)
- [Özellikler](#özellikler)
- [Ekran görüntüleri](#ekran-görüntüleri)
- [Demo](#demo)
- [Kurulum](#kurulum)
- [Kullanım](#kullanım)
- [Proje yapısı](#proje-yapısı)
- [Teknoloji yığını](#teknoloji-yığını)
- [Yol haritası](#yol-haritası)
- [Katkı sağlama](#katkı-sağlama)
- [Lisans](#lisans)
- [İletişim](#iletisim)
- [Teşekkürler](#teşekkürler)

## Neden Türkiye TV?

Türkiye TV, Android TV ve Google TV cihazlarında tarayıcı veya WebView açmadan canlı yayınları yerel Media3 oynatıcısıyla izlemek için tasarlanmıştır. Kanal kataloğunu uzaktan yeniler, son doğrulanmış tam listeyi cihazda saklar ve 10-foot TV deneyimine uygun büyük, odak görünürlüğü yüksek bir arayüz sunar.

## Özellikler

| Alan               | Yetenek                                                                                  |
| ------------------ | ---------------------------------------------------------------------------------------- |
| Dinamik katalog    | `canlitv.diy/tr` kataloğundaki kanal adlarını ve sırasını açılışta yeniler               |
| Güvenli önbellek   | Eksik yanıtı tam kataloğun üzerine yazmaz; son eksiksiz listeyi cihazda saklar           |
| Yerel oynatma      | HLS ve DASH akışlarını Media3/ExoPlayer ile uyarlanabilir kalitede oynatır               |
| Yayın çözümleme    | İç içe oynatıcı sayfalarını üç seviyeye kadar izleyerek doğrudan akış arar               |
| TV odaklı arayüz   | Büyük metin, görünür odak halkası, kanal paneli, saat ve yükleme durumu sunar            |
| Kumanda desteği    | Kanal, sayı, renk, arama, bilgi, oynat/duraklat ve ayar tuşlarını destekler              |
| Kişisel tercihler  | Başlangıç davranışı, son kanal, otomatik oynatma, bilgi süresi ve görüntü oranını saklar |
| Hata dayanıklılığı | Yayını otomatik bir kez yeniden dener; Kırmızı tuşla elle yenileme sağlar                |
| Gizlilik           | Hesap, konum, kişi, kamera veya mikrofon izni istemez                                    |
| Sürekli doğrulama  | Her değişiklikte Android Lint ve debug APK derlemesi çalıştırır                          |

> [!NOTE]
> Kaynak yayın yalnızca SD kalite sunuyorsa, coğrafi olarak engelliyse veya kapalıysa uygulama bunu yapay biçimde HD yayına dönüştüremez.

## Ekran görüntüleri

> [!NOTE]
> TODO: Doğrulanmış ekran görüntülerini `docs/assets/screenshots/` altında ekleyin ve aşağıdaki yer tutucuları güncelleyin.

| Kanal listesi | Oynatıcı | Ayarlar |
| ------------- | -------- | ------- |
| TODO          | TODO     | TODO    |

## Demo

- **CI çıktısı:** [Android TV build](https://github.com/KayaJR356/Turkey-TV/actions/workflows/android.yml) iş akışındaki `TurkiyeTV-debug` artifact'i
- **Video / GIF:** TODO
- **Web demo:** Android TV uygulaması olduğu için bulunmaz

> [!WARNING]
> Debug APK yalnızca test amaçlıdır. Bir APK yüklemeden önce kaynağını ve bütünlüğünü doğrulayın.

## Kurulum

### Gereksinimler

- JDK 17
- Android SDK 35
- Gradle 8.9
- Android 6.0 / API 23 veya üzeri bir Android TV ya da Google TV cihazı

### Kaynaktan derleme

```bash
git clone https://github.com/KayaJR356/Turkey-TV.git
cd Turkey-TV
gradle :app:assembleDebug
```

Debug APK şu konumda oluşturulur:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Lint ve derlemeyi birlikte çalıştırmak için:

```bash
gradle :app:assembleDebug :app:lint
```

## Kullanım

1. Uygulamayı TV cihazına yükleyin ve açın.
2. İlk açılışta tercih ettiğiniz başlangıç davranışını seçin.
3. Kanal listesini `OK`, Menü veya Rehber tuşuyla açın.
4. Yön tuşlarıyla gezinin ve `OK` ile kanalı seçin.
5. Yayın açılmazsa Kırmızı tuşla bir kez yeniden çözümleyin.

| Kumanda tuşu           | İşlev                           |
| ---------------------- | ------------------------------- |
| `Program + / -`        | Sonraki / önceki kanal          |
| `0–9`                  | Kanal numarasına doğrudan git   |
| `OK`, Menü veya Rehber | Kanal listesini aç              |
| Yön tuşları            | Kanal ve ayar menülerinde gezin |
| Sağ veya Geri          | Açık menüyü kapat               |
| Kırmızı                | Geçerli yayını yeniden başlat   |
| Yeşil veya Arama       | Kanal adına göre ara            |
| Sarı                   | Kanal listesini aç / kapat      |
| Ayarlar veya Mavi      | Ayarları aç / kapat             |
| Bilgi                  | Kanal bilgisini göster          |
| Oynat / Duraklat       | Yayını oynat veya duraklat      |

## Proje yapısı

```text
Turkey-TV/
├── .github/
│   ├── ISSUE_TEMPLATE/       Issue formları
│   └── workflows/android.yml Android derleme ve lint
├── app/
│   ├── src/main/java/tv/kaya/turksat/
│   │   ├── MainActivity.java
│   │   ├── SplashActivity.java
│   │   └── ChannelRepository.java
│   ├── src/main/res/         TV arayüzü kaynakları
│   ├── src/main/AndroidManifest.xml
│   └── build.gradle
├── docs/ARCHITECTURE.md
├── build.gradle
├── settings.gradle
└── README.md
```

Mimari ve güven sınırları için [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) dosyasına bakın.

## Teknoloji yığını

| Katman   | Teknoloji                                       |
| -------- | ----------------------------------------------- |
| Dil      | Java                                            |
| Platform | Android TV / Google TV, minSdk 23, targetSdk 35 |
| Oynatıcı | AndroidX Media3 1.5.1; ExoPlayer, HLS ve DASH   |
| Arayüz   | Yerel Android TV arayüzü ve D-pad odak yönetimi |
| Veri     | HTTPS katalog çözümleme ve `SharedPreferences`  |
| Derleme  | Android Gradle Plugin 8.7.3, Gradle 8.9, JDK 17 |
| CI       | GitHub Actions                                  |

## Yol haritası

- [x] Dinamik ve güvenli kanal kataloğu
- [x] Yerel HLS/DASH oynatma
- [x] Kumanda odaklı kanal, arama ve ayar panelleri
- [x] Son kanal ve kullanıcı tercihlerinin yerel saklanması
- [x] CI üzerinde lint ve debug APK üretimi
- [ ] TODO: Proje sahibi tarafından onaylanmış gelecek sürüm hedefleri
- [ ] TODO: Doğrulanmış ekran görüntüleri ve demo videosu
- [ ] TODO: Kamuya açık release ve sürümleme politikası

Yol haritasındaki TODO maddeleri onaylanmış ürün taahhüdü değildir. Öneriler için [feature request formunu](https://github.com/KayaJR356/Turkey-TV/issues/new?template=feature_request.yml) kullanın.

## Katkı sağlama

Katkılar memnuniyetle karşılanır. Başlamadan önce [CONTRIBUTING.md](CONTRIBUTING.md) ve [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) dosyalarını okuyun.

1. Mevcut issue'ları kontrol edin.
2. Güncel `main` üzerinden odaklı bir branch açın.
3. Değişikliğinizi ve gerekli doğrulamaları hazırlayın.
4. `gradle :app:assembleDebug :app:lint` çalıştırın.
5. Pull request şablonunu eksiksiz doldurun.

Güvenlik açıklarını public issue olarak paylaşmayın; [SECURITY.md](SECURITY.md) politikasını izleyin.

## Lisans

Kaynak kod [MIT Lisansı](LICENSE) altında sunulur. Yayın içeriği ve üçüncü taraf markalar bu lisansın kapsamında değildir.

<a id="iletisim"></a>

## İletişim

- GitHub: [@KayaJR356](https://github.com/KayaJR356)
- Destek: [SUPPORT.md](SUPPORT.md)
- Hata bildirimi: [GitHub Issues](https://github.com/KayaJR356/Turkey-TV/issues)
- Güvenlik açığı: [Private Security Advisory](https://github.com/KayaJR356/Turkey-TV/security/advisories/new)

## Teşekkürler

Android, Media3 ve açık kaynak ekosistemini sürdürenlere; yayın altyapısını sağlayan hak sahiplerine ve projeye katkıda bulunan herkese teşekkürler.

---

<div align="center">
Türkiye TV'yi yararlı bulduysanız projeye ⭐ vermeyi düşünebilirsiniz.
</div>
