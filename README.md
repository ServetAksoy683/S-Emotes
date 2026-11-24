# EmoteWheel (Paper 1.21.4, ViaVersion uyumlu)

Çark + emote envanteri + klasörden otomatik emote yükleme + kasa (crate) entegrasyonu.

## Derleme
`mvn package` (Java 21) -> `target/EmoteWheel-1.0.0.jar` -> `plugins/` içine at.
Ayrıca: sunucu 1.21.4, ViaVersion opsiyonel (soft-depend).

## Kullanım
- **F tuşu** (trigger.mode) çarkı açar. Çarkta bir emote'a bak -> sol tık / F ile seç, Shift = iptal.
- `/emote` : Emotelarım envanteri. Alttan emote seç -> üstteki slota tıkla = kuşan.
  Slota tıkla (seçim yokken) = çıkar, emote alta geri döner. Sağ tık = önizle. Shift+tık = ilk boş slota tak.
- Slot sayısı: `wheel.default-slots`, izin: `emotewheel.slots.<sayı>` (max 12).

## Kendi emotelarını ekleme (`plugins/EmoteWheel/emotes/`)
- png / jpg / bmp / **gif (animasyonlu)** -> doğrudan emote olur (dosya adı = id)
- `kalp.png` + `kalp.yml` -> ayarlı emote; `.yml`/`.json` ile tek dosyada çoklu tanım (`starter.yml`)
- `.zip` paket (resim + yml)
- Klasör değişince otomatik yenilenir; `/emote reload` da var.
- Alanlar: name, description, image, free, permission, sound, volume, pitch, particle,
  particle-count, duration, actions (swing | jump | spin), text, icon

## Kasa entegrasyonu
Kilitli (`free: false`) emote'u şu yollarla verebilirsin:
1. **Komut ödülü:** `emote give %player% <id>` (konsol komutu olarak)
2. **Emote kartı eşyası:** `/emote item <oyuncu> <id> [adet]` -> eşyayı kasaya ödül olarak koy.
   Oyuncu sağ tıklayınca emotelarına eklenir (zaten sahipse harcanmaz).
3. İzin: `emotewheel.emote.<id>`
Yeni emote boş slota otomatik takılır (`unlock.auto-equip`).

## B tuşu hakkında
Vanilla client tuş basışını sunucuya göndermez; B için client modu gerekir. Mod
`emotewheel:open` plugin kanalına herhangi bir paket atarsa çark açılır (altyapı hazır).

## Notlar
- Önizleme resimleri map'lere çevrilir (128x128) ve `maps.yml` ile kalıcı tutulur.
- 1.19.4 öncesi client (ViaBackwards) ve Bedrock oyuncular otomatik chest-GUI çarkı alır.
- Balon boyutu/yüksekliği `playback.*` ayarlarından ince ayarlanır (görünmezse `display-transform: FIXED`).
