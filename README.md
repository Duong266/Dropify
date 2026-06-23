# Dropify - Trình Phát Nhạc Android Hiện Đại

Dropify là một ứng dụng nghe nhạc ngoại tuyến mã nguồn mở cho Android, được thiết kế với giao diện hiện đại, tối giản và tích hợp nhiều tính năng mạnh mẽ như chỉnh sửa âm thanh (Equalizer), cắt nhạc, và tự động tìm kiếm lời bài hát trực tuyến.

## 🚀 Tính Năng Nổi Bật

- **🎵 Quản lý Thư viện Nhạc:** Tự động quét và phân loại nhạc theo Bài hát, Album, Nghệ sĩ và Danh sách phát (Playlist).
- **🎼 Bộ Chỉnh Âm (Equalizer):** Tùy chỉnh chất lượng âm thanh chuyên nghiệp với dải băng tần (bands) và các hiệu ứng âm thanh.
- **✂️ Cắt Nhạc (MP3 Cutter):** Công cụ tích hợp cho phép cắt các đoạn nhạc yêu thích để làm nhạc chuông hoặc thông báo.
- **🎤 Lời Bài Hát Trực Tuyến:** Tự động tìm kiếm và hiển thị lời bài hát (lyrics) từ các nguồn API (như LRCLIB).
- **⚡ Điều Chỉnh Tốc Độ & Cao Độ:** Thay đổi tốc độ phát (Playback Speed) và cao độ (Pitch) theo ý thích.
- **🏷️ Chỉnh Sửa Metadata:** Hỗ trợ sửa đổi thông tin bài hát (Tiêu đề, Nghệ sĩ, Album, Thể loại).
- **🎨 Giao Diện Đẹp Mắt:** Thiết kế Material Design với chế độ nền tối (Dark Mode) sang trọng, mượt mà.
- **📲 Điều Khiển Thông Minh:** Hỗ trợ thông báo trình phát nhạc, điều khiển từ màn hình khóa và tai nghe.

## 🛠 Công Nghệ Sử Dụng

- **Ngôn ngữ:** Kotlin
- **Kiến trúc:** MVVM / Fragment-based Navigation
- **Thư viện chính:**
    - `Jetpack Components`: ViewModel, Lifecycle, ViewBinding.
    - `Glide`: Tải và hiển thị ảnh bìa album mượt mà.
    - `Retrofit & Gson`: Gọi API lấy lời bài hát.
    - `MediaSessionCompat`: Quản lý trạng thái phát nhạc hệ thống.
    - `Material Components`: Các thành phần giao diện người dùng hiện đại.
    - `MediaMuxer & MediaExtractor`: Xử lý cắt và trích xuất dữ liệu âm thanh.

## ⚙️ Cài Đặt & Chạy Thử

1. **Yêu cầu:** Android Studio Hedgehog hoặc mới hơn, Android SDK 26+.
2. **Clone project:**
   ```bash
   git clone https://github.com/yourusername/dropify.git
   ```
3. **Mở project:** Mở Android Studio và chọn `Open an Existing Project`.
4. **Build:** Chờ Gradle đồng bộ và nhấn `Run` để cài đặt lên thiết bị/giả lập.

## 📂 Cấu Trúc Thư Mục

```text
app/src/main/java/com/example/simplemusicplayer/
├── MainActivity.kt        # Hoạt động chính, quản lý UI và Fragment
├── MusicService.kt       # Dịch vụ chạy ngầm xử lý phát nhạc
├── LrcLibService.kt      # Xử lý lấy lời bài hát từ API
├── fragments/            # Các màn hình: Songs, Albums, Artists, Playlists
├── adapters/             # Các bộ điều hợp cho RecyclerView (Song, Artist, Lyric...)
└── models/               # Các lớp dữ liệu: Song, Album, Playlist, LyricLine...
```

## 🤝 Đóng Góp

Mọi đóng góp nhằm cải thiện Dropify đều được trân trọng!
1. Fork dự án.
2. Tạo nhánh tính năng (`git checkout -b feature/AmazingFeature`).
3. Commit thay đổi (`git commit -m 'Add some AmazingFeature'`).
4. Push lên nhánh (`git push origin feature/AmazingFeature`).
5. Mở một Pull Request.

## 📄 Giấy Phép

Phân phối dưới giấy phép MIT. Xem `LICENSE` để biết thêm thông tin.

---
**Dropify** - Trải nghiệm âm nhạc theo cách của bạn. 🎧
