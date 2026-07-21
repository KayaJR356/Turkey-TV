# Mimari

## Katalog

1. `ChannelRepository`, masaüstü tarayıcı kimliğiyle `https://www.canlitv.diy/tr` sayfasını indirir.
2. `ft_<oynatıcı-kimliği> tv` sınıfına sahip kanal satırlarından ad, sayfa bağlantısı ve oynatıcı kimliği alınır.
3. Kanallar sayfadaki sıraya göre 1'den başlayarak numaralandırılır.
4. En az 250 kayıt dönmeyen yanıt eksik kabul edilir ve kaydedilmez.
5. Ağ veya site hatasında yalnızca daha önce kaydedilmiş eksiksiz katalog kullanılır. Eksiksiz katalog yoksa kullanıcıya hata gösterilir.

## Oynatma

1. Doğrulanmış resmî akışlar varsa kanal adına göre tercih edilir.
2. Diğer kanallarda kaynak sitenin oynatıcı uç noktası indirilir.
3. HLS (`.m3u8`), DASH (`.mpd`) ve Media3'ün desteklediği doğrudan MP4 kaynakları aranır.
4. JavaScript kaçışları, HTML entity'leri, percent-encoded URL'ler ve sınırlı base64 oynatıcı değerleri normalize edilir.
5. `iframe`, `video`, `source`, `data-src` ve `data-lazy-src` kaynakları en fazla beş seviye izlenir.
6. Oynatıcı uç noktası sonuç vermezse kanal sayfası aynı güvenli çözümleme hattıyla denenir.
7. Reklam/izleme adresleri ile YouTube ve tarayıcıya özel kaynaklar yerel medya URL'si olarak kabul edilmez.
8. Media3 için HLS/DASH MIME türü açıkça atanır. Kaynak uyumluluğu için katalog `Referer` başlığı korunur; farklı CDN'leri engelleyebilen zorunlu `Origin` başlığı uygulanmaz.
9. Kanal değişiminde eski çözümleme sonucu kuşak numarasıyla iptal edilir; geç tamamlanan ağ yanıtı yeni kanalın üzerine yazamaz.

> [!IMPORTANT]
> Kaynak sayfa oynatılabilir bir HLS/DASH/MP4 akışı vermiyorsa, yayın kapalıysa veya erişim yayıncı tarafından engelleniyorsa uygulama akış üretemez. Çözümleyici yalnızca mevcut ve izin verilen medya kaynaklarını bulur.

## TV arayüzü

- İzleme ekranında yön tuşları yanlışlıkla kanal değiştirmez.
- Kanal değişimi `KEYCODE_CHANNEL_UP/DOWN` veya numara tuşlarıyla yapılır.
- Üst durum şeridi kanal numarası, kanal adı, canlı/yükleniyor durumu ve saati gösterir.
- Kanal rehberi mevcut kanala odaklanarak açılır ve listeyi ilgili satıra kaydırır.
- Kanal ve ayar panelleri yarı saydam, yüksek kontrastlı 10-foot tasarıma sahiptir.
- Kırmızı yayın yenilemeyi, Yeşil aramayı, Sarı kanal rehberini ve Mavi ayarları açar.
- Yükleme, hata ve numara girişleri ayrı kartlarla görünür; video üzerinde okunabilirlik için ekran gölgesi kullanılır.
- İlk açılış seçimi, son kanal, otomatik oynatma, bilgi süresi ve görüntü oranı `SharedPreferences` içinde tutulur.
- Görüntü oranı Media3 `FIT`, `ZOOM` ve `FILL` modlarına eşlenir.

## Dayanıklılık ve güvenlik

- Bağlantı ve okuma zaman aşımı vardır.
- Yalnızca HTTPS katalog, oynatıcı ve medya adresleri kabul edilir.
- Oynatıcı belgeleri 2 MB ile sınırlandırılır.
- Çözümleme döngüleri ziyaret edilen URL kümesi ve derinlik sınırıyla engellenir.
- Son eksiksiz katalog çevrimdışı başlangıcı destekler.
- Süreli medya URL'leri yalnızca çalışan uygulama oturumunda saklanır.
- Media3 hatası bir kez otomatik yeniden denenir; kullanıcı Kırmızı tuşla önbelleği temizleyip tekrar deneyebilir.
