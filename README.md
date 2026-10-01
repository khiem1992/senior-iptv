# 📺 IPTV Cho Người Già - Android TV & Google TV (Media3 ExoPlayer)

Ứng dụng IPTV chuyên dụng được thiết kế tối giản, tốc độ cao dành riêng cho người lớn tuổi sử dụng Android TV / Google TV (Sony, TCL, Xiaomi, Casper, v.v.).

---

## 🌟 Điểm nổi bật
1. **Mở app là xem ngay**: Tự động phát toàn màn hình kênh xem gần nhất (hoặc kênh số 01 nếu lần đầu mở).
2. **Điều khiển Remote TV trực quan**:
   - **LÊN / XUỐNG**: Đổi kênh trước / sau.
   - **Hiển thị OSD**: Số kênh và Tên kênh cỡ chữ cực lớn, tự tắt sau 3 giây.
   - **OK**: Mở danh sách kênh trượt từ bên trái (Drawer).
   - **BACK**: Nếu Drawer đang mở -> đóng Drawer. Nếu đang xem -> hiện hộp thoại hỏi thoát chữ lớn dễ bấm.
3. **Drawer Kênh Thông Minh**:
   - Khi Drawer mở: LÊN/XUỐNG chỉ di chuyển focus chọn kênh, **không đổi kênh ngầm**.
   - Chỉ khi nhấn **OK** mới xác nhận chuyển kênh.
   - Dùng chính ExoPlayer hiện tại để phát, không tải lại M3U, không tạo Player mới.
   - Logo lỗi/hỏng tự động dùng icon TV dự phòng, **tuyệt đối không bao giờ crash app**.
4. **M3U & Caching Thông Minh**:
   - Link mặc định: `https://raw.githubusercontent.com/khiem1992/kdtvm/refs/heads/main/iptv86.m3u`
   - Tải lần đầu lưu vào cache local để mở app xem tức thì không cần đợi tải lại.
   - Nút **"Cập nhật kênh"** thủ công trong Cài đặt: nếu mạng lỗi hoặc M3U hỏng, hệ thống tự động giữ nguyên danh sách kênh cũ.
5. **Auto-Retry & Mạng Yếu**:
   - Mất tín hiệu hiển thị banner: *"Đang kết nối lại..."*
   - Tự động thử lại mỗi **4 giây** cho đến khi có mạng trở lại.
   - Chỉ 1 cơ chế retry chạy tại 1 thời điểm, hủy ngay khi người dùng bấm chuyển kênh khác.

---

## 🚀 QUY TRÌNH BUILD FILE APK TRÊN GITHUB VÀ CÀI ĐẶT LÊN TV

### Bước 1: Tải trọn bộ Project về máy
- Nhấn nút **"Tải trọn bộ Project Android Studio (ZIP)"** trên giao diện web để nhận file `iptv-android-tv.zip`.
- Giải nén file zip ra thư mục trên máy tính của bạn.

### Bước 2: Tạo Repository trên GitHub và Push code
1. Truy cập [github.com](https://github.com/) và tạo một repository mới (ví dụ: `senior-iptv-tv`), chọn chế độ **Public** hoặc **Private**.
2. Mở Terminal / CMD tại thư mục vừa giải nén:
```bash
git init
git add .
git commit -m "Initial commit Senior IPTV Android TV"
git branch -M main
git remote add origin https://github.com/<tai-khoan-cua-ban>/senior-iptv-tv.git
git push -u origin main
```

### Bước 3: GitHub Actions tự động Build ra file APK
1. Vào tab **Actions** trên GitHub repository của bạn.
2. Bạn sẽ thấy quy trình **"Build Android TV IPTV APK"** tự động chạy trong khoảng 2 - 3 phút.
3. Khi biểu tượng chuyển sang màu xanh lá (Success):
   - Nhấp vào phiên bản build vừa hoàn thành.
   - Cuộn xuống mục **Artifacts** -> Tải file `Senior-IPTV-AndroidTV-APK.zip` về.
   - Giải nén ra bạn sẽ có file: `app-release.apk` (hoặc `app-debug.apk`).

### Bước 4: Cài đặt file APK lên Sony Android TV
Có 3 cách cực kỳ đơn giản để cài file APK vào TV:

#### 👉 Cách 1: Dùng ứng dụng "Send files to TV" (Khuyên dùng nhất - Không cần dây cáp)
1. Trên Sony Android TV, mở **Google Play Store**, tìm và cài đặt app **"Send files to TV"** và app quản lý file (như **"AnExplorer"** hoặc **"File Commander"**).
2. Trên điện thoại Android của bạn, cài đặt app **"Send files to TV"** và copy file `app-release.apk` vào máy.
3. Đảm bảo TV và điện thoại cùng kết nối vào một mạng Wi-Fi.
4. Mở app trên điện thoại chọn **SEND** -> chọn file `app-release.apk`. Mở app trên TV chọn **RECEIVE**.
5. Nhận file xong trên TV, dùng AnExplorer bấm vào file APK và chọn **Cài đặt (Install)**.

#### 👉 Cách 2: Dùng USB
1. Copy file `app-release.apk` vào USB.
2. Cắm USB vào cổng sau của Sony Android TV.
3. Mở ứng dụng duyệt file trên TV (hoặc cài app *File Commander* từ CH Play), chọn file APK trong USB và nhấn **Cài đặt**.

*Lưu ý lần đầu*: Nếu TV hỏi *"Cho phép cài đặt từ nguồn không xác định"*, chọn **Cho phép (Allow)**.

---
