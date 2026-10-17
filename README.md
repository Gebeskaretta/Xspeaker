# 🔊 Xspeaker — Android TV Bluetooth Hoparlör Alıcısı

> **Televizyonunuzu yüksek kaliteli bir kablosuz Bluetooth Hi-Fi hoparlöre dönüştürün!**  
> Telefonunuzdan, tabletinizden veya bilgisayarınızdan doğrudan televizyonunuzun güçlü ses sistemine bağlanın ve müziklerinizi TV hoparlöründen dinleyin.

---

## ✨ Özellikler

- 🎵 **Bluetooth A2DP Sink Alıcısı**: Telefonunuzun Bluetooth menüsünde televizyonunuzu görünür bir ses hoparlörü yapar.
- 📱 **Telefon & Cihaz Desteği**: iPhone (iOS), Android telefonlar, iPad ve dizüstü bilgisayarlar ile tam uyumludur.
- 🎚️ **DSP Ekolayzer & Akustik Modlar**:
  - **X-Bass Punch**: Derin subwoofer rezonansı ve güçlü baslar.
  - **Sinematik 3D**: Genişletilmiş oda dolusu çevreleyen ses sahnesi.
  - **Net Vokal & Diyalog**: Kristal berraklığında konuşma ve şarkı sözleri.
  - **Gece Modu**: Komşuları rahatsız etmeyen dengeli dinamik aralık.
  - **Doğal Stüdyo Hi-Fi**: Saf, kaynaktan geldiği gibi aktarım.
- 📊 **32-Bant Canlı Akustik Görselleştirici (Audio Visualizer)**: Müzik çalarken TV ekranında parlayan neon spektrum grafikleri.
- 🛡️ **OLED / QLED Ekran Koruyucu (Ambient Mode)**: Uzun süreli müzik dinlerken TV ekranında piksel yanmasını (burn-in) önleyen minimalist karartma modu.
- 📺 **Android TV / Google TV Kumanda Desteği**: D-pad ok tuşları, ses artırma/azaltma ve oynat/durdur tuşları ile tam kumanda uyumu.
- 🔔 **Dahili Akustik Ses Testi**: Telefon bağlamadan önce TV hoparlörlerinin frekanslarını test eden ses motoru.

---

## 🚀 GitHub Üzerinden Otomatik APK Derleme & İndirme

Bu projede **GitHub Actions ile otomatik derleme (CI/CD)** yapılandırılmıştır. Bilgisayarınızda Android Studio kurulu olmasa bile GitHub sizin için APK dosyasını otomatik olarak derler.

### 📥 Hazır APK Dosyasını İndirme Adımları:
1. Bu depoyu GitHub hesabınıza push yapın veya fork edin.
2. GitHub'da deponuzun üst menüsündeki **Actions** sekmesine tıklayın.
3. Sol taraftan **"Xspeaker Android TV APK Build"** iş akışını seçin.
4. En son yeşil tikli çalışan derlemeye tıklayın.
5. Sayfanın en altındaki **Artifacts** bölümünden **`Xspeaker-AndroidTV-APK`** dosyasını indirin.
6. İndirdiğiniz zip dosyasını açtığınızda kuruluma hazır `Xspeaker-TV-v1.0.apk` dosyası çıkacaktır!

---

## 📺 Televizyona APK Nasıl Yüklenir?

1. **Yöntem 1 (USB Bellek):** İndirdiğiniz `.apk` dosyasını bir USB belleğe atın. USB'yi Android TV'nize takın ve herhangi bir dosya yöneticisi (örn. FX File Explorer) ile açıp yükleyin.
2. **Yöntem 2 (Kablosuz - En Kolay):** 
   - Hem telefonunuza hem de TV'nize Google Play'den ücretsiz **"Send Files to TV"** uygulamasını yükleyin.
   - APK'yı telefonunuza indirip TV'ye kablosuz gönderin ve TV'deki dosya yöneticisiyle kurulumu tamamlayın.

---

## 📲 Telefonu TV'ye Bağlama Adımları

1. Televizyonunuzda **Xspeaker** uygulamasını açın.
2. Ekrandaki **"Eşleşme Modu"** butonuna basın.
3. Telefonunuzda (iPhone veya Android) **Ayarlar > Bluetooth** menüsüne girin.
4. Yeni cihazlar listesinde televizyonunuzun adını (örn. `Xspeaker TV`) seçin ve eşleştirin.
5. Telefonunuzda Spotify, YouTube veya herhangi bir müzik uygulamasını başlatın; ses doğrudan televizyonunuzdan çalacaktır!

---

## 🛠️ Yerel Bilgisayarda Derleme (Opsiyonel)

Projeyi kendi bilgisayarınızda derlemek isterseniz:

```bash
git clone https://github.com/KULLANICI_ADINIZ/xspeaker-android-tv.git
cd xspeaker-android-tv

# macOS / Linux
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

Derlenen APK: `app/build/outputs/apk/debug/app-debug.apk` yolunda oluşacaktır.

---

## 📄 Lisans
Bu proje açık kaynaklıdır ve Apache 2.0 lisansı ile korunmaktadır.
