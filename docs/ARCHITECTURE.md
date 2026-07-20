# Mimari

## Katalog

1. `ChannelRepository`, masaüstü tarayıcı kimliğiyle `https://www.canlitv.diy/tr` sayfasını indirir.
2. `ft_<oynatıcı-kimliği> tv` sınıfına sahip kanal satırlarından ad, sayfa bağlantısı ve oynatıcı kimliği alınır.
3. Kanallar sayfadaki sıraya göre 1'den başlayarak numaralandırılır.
4. En az 250 kayıt dönmeyen yanıt eksik kabul edilir ve kaydedilmez.
5. Ağ veya site hatasında yalnızca daha önce kaydedilmiş eksiksiz katalog kullanılır. Eksiksiz katalog yoksa kullanıcıya hata gösterilir; dokuz kanallı kısmi bir listeye düşülmez.

## Oynatma

1. TRT ve doğrulanmış birkaç yayın için tercih edilen HLS adresi katalog oluşturulurken atanır.
2. Diğer kanallarda kaynak sitenin oynatıcı sayfası arka planda indirilir.
3. HLS (`.m3u8`) veya DASH (`.mpd`) adresi bulunursa Media3/ExoPlayer doğrudan oynatır.
4. İç içe iframe oynatıcıları en fazla üç seviye izlenir; YouTube ve tarayıcıya özel kaynaklar atlanır.
5. Doğrudan uyarlanabilir akış bulunmazsa web geri dönüşü yapılmaz ve kullanıcıya yerel yayın bulunamadığı gösterilir.
6. Kanal değişiminde eski çözümleme sonucu kuşak numarasıyla iptal edilir; geç tamamlanan ağ yanıtı yeni kanalın üzerine yazamaz.

## TV arayüzü

- İzleme ekranında D-pad yönleri tüketilir ve kanal değiştirmez.
- Kanal değişimi yalnızca `KEYCODE_CHANNEL_UP/DOWN` veya numara tuşlarıyla yapılır.
- OK, Menü ve Rehber kanal panelini açar.
- Ayarlar ve Mavi tuş sağ ayar panelini açar.
- Kanal panelindeki Arama ve Ayarlar seçenekleri listenin üzerinde sabittir.
- Yeşil kanal aramasını, Sarı kanal listesini, Kırmızı yayın yenilemeyi açar.
- Kanal ve ayar satırlarında sistem odağı, yüksek kontrastlı seçici ve kısa ölçek animasyonu kullanılır.
- Paneller kayarak açılır; splash logosu, kanal bilgi kartı ve yükleme katmanı kısa geçişlerle görünür.
- Kanal paneli saat ve mevcut kanal işareti gösterir; ayarlar her ekran yüksekliğinde kaydırılabilir.
- İlk açılış seçimi, son kanal, otomatik oynatma ve bilgi süresi `SharedPreferences` içinde tutulur.
- Görüntü oranı tercihi Media3 `FIT`, `ZOOM` ve `FILL` modlarına eşlenir.
- Ayrı splash etkinliği açılış markasını gösterir; kanal değişiminde yayın hazır olana kadar siyah yükleme katmanı görünür.

## Dayanıklılık

- Bağlantı ve okuma zaman aşımı vardır.
- Yalnızca HTTPS katalog ve oynatıcı adresleri kabul edilir.
- Son eksiksiz katalog çevrimdışı başlangıcı destekler.
- Doğrudan çözülen yayın adresleri yalnızca çalışan uygulama oturumunda saklanır; süreli URL'ler kalıcılaştırılmaz.
- Media3 adaptif kalite ve yönlendirme desteği kullanır.
- Media3 hatası bir kez otomatik yeniden denenir; kullanıcı Kırmızı tuşla önbelleği temizleyip tekrar deneyebilir.
