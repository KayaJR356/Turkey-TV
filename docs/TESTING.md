# Doğrulama notları

## Otomatik/yerel kontroller

- `clean assembleRelease`: başarılı
- Android Lint Vital: başarılı
- R8 küçültme ve kaynak daraltma: başarılı
- APK zipalign: başarılı
- APK imza doğrulama: v1, v2 ve v3 başarılı
- Web katalog ayrıştırma: 20 Temmuz 2026 tarihinde 285 benzersiz kanal
- Dokuz resmî TRT HLS uç noktası: HTTP 200

## TV üzerinde kabul testi

1. APK'yı yükleyin ve uygulamayı Android TV uygulama satırından açın.
2. İlk kanalın başladığını doğrulayın.
3. `2`, ardından 1,3 saniye bekleyerek Show TV'ye geçildiğini doğrulayın.
4. `285` girerek son kanala gidin.
5. Kanal `+/-`, Yukarı/Aşağı ve OK ile liste davranışını sınayın.
6. Listede ekran dışındaki kanallara D-pad ile ilerleyin.
7. Kırmızı tuşla favoriyi açıp kapatın.
8. Web kanalında Yeşil/Bilgi tuşuyla yayın akışını açıp kapatın.
9. Uygulamayı kapatıp yeniden açarak kataloğun güncellendiğini doğrulayın.
10. İnterneti kapatıp son başarılı önbellekle listenin açıldığını doğrulayın.
