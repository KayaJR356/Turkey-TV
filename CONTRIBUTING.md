# Türkiye Canlı TV'ye katkı sağlama

Türkiye Canlı TV'yi geliştirmeye ayırdığınız zaman için teşekkürler. Bu rehber, katkıların güvenli, incelenebilir ve proje kapsamıyla uyumlu kalmasını sağlar.

## Başlamadan önce

- [Davranış Kuralları](CODE_OF_CONDUCT.md) belgesini okuyun.
- Güvenlik açıklarını issue olarak paylaşmayın; [Güvenlik Politikası](SECURITY.md) üzerinden bildirin.
- Büyük değişiklikler için kod yazmadan önce bir feature request açın.
- Yayın adresi veya üçüncü taraf içerik eklerken paylaşım hakkınız olduğundan emin olun.

## Katkı türleri

- Hata raporları ve tekrarlanabilir test senaryoları
- Dokümantasyon ve erişilebilirlik iyileştirmeleri
- TV kumandası, odak davranışı ve cihaz uyumluluğu düzeltmeleri
- Test kapsamı ve güvenilirlik geliştirmeleri
- Bakımcı tarafından onaylanan özellikler

## Geliştirme ortamı

### Gereksinimler

- Git
- JDK 17
- Android SDK 35
- Gradle 8.9

### Kurulum

```bash
git clone https://github.com/YOUR_USERNAME/Turkey-TV.git
cd Turkey-TV
gradle :app:assembleDebug
```

`YOUR_USERNAME` değerini GitHub kullanıcı adınızla değiştirin.

## Önerilen iş akışı

1. Uygun issue'yu açın veya mevcut bir issue seçin.
2. Fork'unuzu güncel `main` branch'iyle eşitleyin.
3. Tek amaca odaklanan bir branch oluşturun:

   ```bash
   git switch -c fix/kisa-aciklama
   ```

4. Küçük ve anlaşılır değişiklikler yapın.
5. İlgili kontrolleri çalıştırın.
6. Açıklayıcı commit mesajları kullanın.
7. Pull request şablonunu doldurarak `main` branch'ine PR açın.

## Doğrulama

Pull request göndermeden önce en az şu kontrolleri çalıştırın:

```bash
gradle :app:assembleDebug :app:lint
```

TV arayüzünü etkileyen değişikliklerde [docs/TESTING.md](docs/TESTING.md) kabul adımlarını uygun bir Android TV/Google TV cihazında veya emülatörde uygulayın.

## Commit ve pull request ilkeleri

- Her commit tek, anlamlı bir değişikliği temsil etmelidir.
- Başlıkları kısa ve emir kipinde yazın; örnek: `Kanal aramasında odağı düzelt`.
- PR açıklamasında motivasyonu, davranış değişikliğini ve doğrulama sonucunu belirtin.
- Görsel değişikliklerde telifsiz veya sansürlenmiş önce/sonra görüntüsü ekleyin.
- İlgili issue'yu `Closes #123` biçiminde bağlayın.
- Kaynak kodla ilgisiz biçimlendirme değişikliklerini aynı PR'a karıştırmayın.

## Hata raporları

[Bug report formunu](https://github.com/KayaJR356/Turkey-TV/issues/new?template=bug_report.yml) kullanın ve şunları ekleyin:

- Uygulama sürümü veya commit SHA
- Cihaz modeli ve Android sürümü
- Tekrarlanabilir adımlar
- Beklenen ve gerçekleşen davranış
- Gizli veri içermeyen loglar

## Özellik önerileri

[Feature request formunda](https://github.com/KayaJR356/Turkey-TV/issues/new?template=feature_request.yml) önce problemi açıklayın. Önerilen çözümün TV kumandası deneyimi, bakım yükü, güvenlik ve yayın hakları üzerindeki etkisini belirtin.

## Lisans

Katkı göndererek çalışmanızın repository'nin [MIT Lisansı](LICENSE) altında yayımlanmasını kabul etmiş olursunuz ve katkıyı sunma hakkına sahip olduğunuzu beyan edersiniz.
