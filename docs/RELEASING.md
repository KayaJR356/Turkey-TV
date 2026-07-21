# Sürüm ve uygulama içi güncelleme

Uygulama, `KayaJR356/Turkey-TV` deposundaki yayımlanmış GitHub Release kayıtlarını açılışta
otomatik denetler. Kararlı sürümler ve ön sürümler desteklenir. Yeni bir sürümde APK ile aynı
ada sahip `.sha256` dosyası bulunmalı veya GitHub varlık özeti sağlamalıdır.

## Bir defalık kurulum

Android mevcut uygulamanın üzerine yalnızca aynı imzalama anahtarıyla ve daha yüksek
`versionCode` ile imzalanmış APK kurar. Bu nedenle aşağıdaki repository secret değerleri bir
kez tanımlanmalı ve aynı anahtar bütün gelecek sürümlerde korunmalıdır:

- `UPDATE_KEYSTORE_BASE64`: JKS/keystore dosyasının tek satır base64 değeri
- `UPDATE_KEYSTORE_PASSWORD`: keystore parolası
- `UPDATE_KEY_ALIAS`: anahtar takma adı
- `UPDATE_KEY_PASSWORD`: anahtar parolası

Anahtar dosyasını veya parolaları Git deposuna eklemeyin. Anahtarın çevrimdışı, şifreli bir
yedeğini saklayın; kaybolursa kurulu uygulamalar yeni sürüme yükseltilemez.

> [!IMPORTANT]
> Uygulama içinden kimlik doğrulama belirteci güvenle saklanamayacağı için güncelleme deposu
> ve Release APK varlıkları cihazlar tarafından oturum açmadan indirilebilir olmalıdır. Kaynak
> depo özel kalacaksa `UPDATE_REPOSITORY` değerini APK yayımlayan ayrı bir herkese açık depoya
> yönlendirin.

## Yeni sürüm yayımlama

1. `app/build.gradle` içindeki `versionCode` değerini artırın.
2. `versionName` değerini yeni semantik sürüme ayarlayın (örneğin `3.5.0`).
3. `CHANGELOG.md` içinde aynı sürüm başlığını ve tarihi ekleyin; Release notları bu bölümden üretilir.
4. Değişikliği `main` dalına gönderin.
5. `Publish signed app release` iş akışı imzalı APK ve SHA-256 dosyasını yayımlar.

Android 8 ve üzerinde kullanıcı ilk güncellemede bu uygulamaya “bilinmeyen uygulama yükleme”
izni verir. Android paket yükleyicisi her güncellemede son kurulum onayını gösterir; normal bir
uygulamanın bu güvenlik ekranını sessizce geçmesi mümkün değildir.
