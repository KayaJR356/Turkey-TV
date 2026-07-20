# Mimari

## Veri akışı

1. Uygulama açılır ve `ChannelRepository.load()` arka planda çağrılır.
2. Kullanıcı özel HTTPS JSON adresi tanımladıysa önce bu kaynak denenir.
3. Ardından web katalog sayfası masaüstü tarayıcı kimliğiyle indirilir.
4. `title="… canlı izle"` bağlantıları TRT 1 ile Türkmen Sport TV sınırları arasında tekilleştirilir.
5. 20'den fazla kanal varsa katalog geçerli sayılır ve cihaz önbelleğine kaydedilir.
6. Ağ/web hatasında önbellek, önbellek yoksa yerleşik resmî TRT HLS listesi kullanılır.
7. `.m3u8` yayınları Media3 ile; web bağlantıları tam ekran WebView oynatıcısıyla açılır.

## Dayanıklılık kararları

- Bağlantı ve okuma zaman aşımı vardır.
- HTTP başarısız yanıtları güncelleme olarak kabul edilmez.
- Özel JSON yalnızca HTTPS URL'lerini kabul eder.
- Kanal numarası girdisi üç basamakla sınırlıdır.
- Başarısız oynatma kullanıcıya görünür uyarı verir.
- Son başarılı katalog çevrimdışı başlangıcı destekler.

## Android TV odağı

Kanal listesi `ScrollView` içindeki odaklanabilir satırlardan oluşur. Böylece D-pad odağı ekran dışına ilerlediğinde liste otomatik kayar. İzleme ekranında Yukarı/Aşağı kanal değiştirirken, liste açıkken aynı tuşlar Android'in standart odak gezinmesine bırakılır.
