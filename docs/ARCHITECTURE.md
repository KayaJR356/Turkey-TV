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
4. Doğrudan uyarlanabilir akış bulunmazsa gerçek YouTube/dış iframe veya yayıncı adresi çıkarılır ve WebView'da doğrudan tam ekrana alınır.
5. Genel katalog sayfası TV oynatıcısı olarak gösterilmez.
6. Kanal değişiminde eski çözümleme sonucu kuşak numarasıyla iptal edilir; geç tamamlanan ağ yanıtı yeni kanalın üzerine yazamaz.

## TV arayüzü

- İzleme ekranında D-pad yönleri tüketilir ve kanal değiştirmez.
- Kanal değişimi yalnızca `KEYCODE_CHANNEL_UP/DOWN` veya numara tuşlarıyla yapılır.
- OK, Menü ve Rehber kanal panelini açar.
- Ayarlar ve Mavi tuş sağ ayar panelini açar.
- Kanal ve ayar satırlarında sistem odağı, yüksek kontrastlı seçici ve kısa ölçek animasyonu kullanılır.
- İlk açılış seçimi, son kanal, otomatik oynatma ve bilgi süresi `SharedPreferences` içinde tutulur.

## Dayanıklılık

- Bağlantı ve okuma zaman aşımı vardır.
- Yalnızca HTTPS katalog ve oynatıcı adresleri kabul edilir.
- Son eksiksiz katalog çevrimdışı başlangıcı destekler.
- Doğrudan çözülen yayın adresleri yalnızca çalışan uygulama oturumunda saklanır; süreli URL'ler kalıcılaştırılmaz.
- Media3 adaptif kalite ve yönlendirme desteği kullanır.
- Web oynatıcıları donanım hızlandırmalı, otomatik oynatmalı ve tam ekran özel görünüm desteklidir.
