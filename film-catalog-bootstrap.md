# Mio otomatik katalog yüklemesi

Otomatik yükleyici filmleri `title.basics.tsv.gz` dosyasındaki satır sırasıyla
değil, IMDb oy sayısına göre çoktan aza ekler. Oy sayıları eşitse IMDb puanı
yüksek olan önce gelir; her ikisi de eşitse IMDb kimliği sıralamayı sabit tutar.
Popülerlik ölçütü toplam oy sayısıdır; güncel trend veya vizyon tarihi değildir.
Bu nedenle çok izlenen eski filmler de listenin başında yer alabilir.

IMDb'nin [resmi veri açıklamasındaki](https://developer.imdb.com/non-commercial-datasets/)
`title.ratings.tsv.gz` dosyası kullanılır. Yükleyici etkinleştirildiğinde bu dosya
varsayılan olarak IMDb'den okunur; ek bir API anahtarı gerekmez. İstenirse yerel
bir gzip dosyası kullanılabilir. IMDb veri kullanım ve lisans koşulları geçerlidir.

## Yapılandırma

Yerel ortamda veya Render ortam değişkenlerinde:

| Değişken | Varsayılan | Açıklama |
| --- | --- | --- |
| `CATALOG_BOOTSTRAP_ENABLED` | `true` | Mio boş sonuç yanıtı sonrası yükleme. Kapatmak için `false`. |
| `CATALOG_BOOTSTRAP_DATASET_PATH` | `film-data/title.basics.tsv.gz` | Sunucuda bulunan IMDb temel veri dosyasının yolu. |
| `CATALOG_BOOTSTRAP_RATINGS_PATH` | `https://datasets.imdbws.com/title.ratings.tsv.gz` | Oy verisi için HTTPS adresi veya yerel dosya yolu. |
| `CATALOG_BOOTSTRAP_MAX_FILMS` | `50000` | Bir çalışmada başarıyla eklenecek yeni film sayısı. |
| `CATALOG_BOOTSTRAP_REQUEST_DELAY` | `250ms` | Film istekleri arasındaki bekleme. |
| `CATALOG_BOOTSTRAP_MAX_CONSECUTIVE_FAILURES` | `20` | Arka arkaya hata alındığında yüklemeyi durdurma sınırı. |

`film-data` Git'e dahil edilmez. Temel veri dosyası dağıtım ortamında ayrıca
bulunmalıdır; mevcut sunucu dosya yolunu `CATALOG_BOOTSTRAP_DATASET_PATH` ile
koruyabilirsiniz. Spring'in mevcut `app.catalog.bootstrap.*` ayarları da geçerlidir.

## Çalışma davranışı

- Uygulama başlangıcı veya Render deploy/restart yükleme başlatmaz.
- Mio'nun `/search` isteği `empty` sonucu verdiğinde önce yanıt sayfası tamamen
  oluşturulup HTTP yanıtı gönderilir; ardından yükleme ayrı bir sanal thread'de başlar.
  Kullanıcı film yüklemesini beklemez. JavaScript veya ikinci bir tarayıcı isteği gerekmez.
- Sonuç bulunan, açıklama isteyen, doğrulama hatası veren veya servis hatası alan
  Mio istekleri ve normal film aramaları yüklemeyi tetiklemez.
- Aynı uygulamada bir yükleme sürüyorsa yeni boş sonuçlar ikinci bir iş başlatmaz
  veya kuyruğa eklenmez. İş bitince sonraki boş sonuç yeniden tetikleyebilir.
- Önce temel veri dosyası taranır ve yetişkin içeriği olmayan sinema filmleri seçilir.
- Oy verisi bulunan filmler sıralanır; sıralama hazırlığı sırasında film eklenmez.
- Katalogda bulunan filmler tekrar indirilmez ve yeni film limitinden düşülmez.
- Sonraki çalıştırma yine en popüler filmlerden taramaya başlar; mevcutları atlayıp
  sıradaki eksik filmlerle devam eder.
- Oyu bulunmayan filmler otomatik yüklenmez. Manuel film arama/ekleme etkilenmez.
- Oy kaynağı okunamazsa veya uygun filmlerle eşleşmezse hata kaydedilir ve yükleme
  durur; eski satır sırasına dönülmez.
- Mevcut filmler silinmez. İstek aralığı, ardışık hata sınırı ve yükleme sonundaki
  eksik embedding tamamlama işlemi korunur.

Değişikliğin çalışan sunucuda uygulanması için yeni sürümü dağıtıp uygulamayı
yeniden başlatın. Ortamda önceden `CATALOG_BOOTSTRAP_ENABLED=false` tanımlandıysa
bu değeri kaldırın veya `true` yapın. Yükleme bir sonraki boş Mio yanıtından sonra başlar.
