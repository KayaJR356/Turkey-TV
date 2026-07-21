# Türkiye TV'ye Katkı Sağlama

Katkınız için teşekkürler. Bu rehber, değişikliklerin küçük, doğrulanabilir ve incelemesi kolay kalmasını amaçlar.

## Başlamadan önce

- Destek talepleri için [SUPPORT.md](SUPPORT.md) dosyasını kullanın.
- Güvenlik açıklarını public issue olarak açmayın; [SECURITY.md](SECURITY.md) politikasını izleyin.
- Anlamlı davranış değişiklikleri için önce bir issue açın.
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) kurallarına uyun.

## Geliştirme ortamı

Gereksinimler:

- JDK 17
- Android SDK 35
- Gradle 8.9

```bash
git clone https://github.com/KayaJR356/Turkey-TV.git
cd Turkey-TV
gradle :app:assembleDebug :app:lint
```

## Katkı akışı

1. Repository'yi fork edin.
2. Güncel `main` dalından açıklayıcı bir branch oluşturun.
3. Tek bir probleme odaklanan değişiklik yapın.
4. İlgili testleri ve dokümantasyonu güncelleyin.
5. Derleme ve lint kontrollerini çalıştırın.
6. Pull request şablonunu eksiksiz doldurun.

Örnek branch adları:

```text
fix/channel-focus
docs/remote-controls
feat/catalog-validation
```

## Pull request kontrol listesi

- Değişiklik kapsamı açık ve sınırlıdır.
- Gereksiz biçimlendirme veya bağımlılık değişikliği yoktur.
- `gradle :app:assembleDebug :app:lint` başarılıdır.
- Kullanıcı davranışı değiştiyse README güncellenmiştir.
- Yeni gizli bilgi, yayın anahtarı veya kişisel veri eklenmemiştir.
- Üçüncü taraf içerik ve yayın hakları gözetilmiştir.

## Commit mesajları

Kısa ve eylem odaklı mesajlar kullanın:

```text
fix: keep channel focus after refresh
docs: explain remote control shortcuts
```

## İnceleme

Bakımcılar kapsam, doğruluk, güvenlik, TV kullanılabilirliği ve geriye dönük uyumluluğu değerlendirir. Geri bildirim istenmesi katkının reddedildiği anlamına gelmez.
