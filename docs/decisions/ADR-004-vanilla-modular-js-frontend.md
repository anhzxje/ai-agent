# ADR-004: Thiết kế Frontend Web Modular với Vanilla JavaScript ES6+

## Trạng thái
Đã chấp thuận (Accepted)

## Ngày quyết định
2026-10-24

## Bối cảnh & Vấn đề (Context)
Giao diện người dùng cho Soopi AI Agent cần đáp ứng các tiêu chí:
1. Giao diện trực quan, phản hồi tức thì, hỗ trợ trải nghiệm Dark/Light theme cao cấp và bố cục quản trị trang cuộn đơn (Continuous Scroll).
2. Tích hợp liền khối với backend Spring Boot mà không tạo ra gánh nặng quy trình build phức tạp (Node.js, npm, Webpack, Vite, node_modules hàng trăm MB).
3. Dễ dàng nhúng vào ứng dụng Desktop chạy bằng Electron hoặc WebView.

## Quyết định (Decision)
Xây dựng giao diện bằng **Vanilla JavaScript chuẩn ES6 Modules** kết hợp với **Vanilla CSS3** và HTML5 ngữ nghĩa, đặt trực tiếp trong thư mục `src/main/resources/static/`:
- Phân chia module hướng thành phần: `chatView.js`, `projectView.js`, `adminView.js`, `markdownUtils.js`, `api.js`, và `app.js`.
- Sử dụng Custom CSS Properties (CSS Variables) để chuyển đổi mượt mà giữa Dark và Light theme.
- Tận dụng chuẩn ES6 Module nạp trực tiếp qua trình duyệt (`<script type="module" src="js/app.js">`), không cần bước đóng gói (Bundling).

## Các giải pháp thay thế đã xem xét (Alternatives Considered)

### 1. Single Page Application với React / Vue / Angular
- **Ưu điểm:** Hệ sinh thái component phong phú, quản lý state qua thư viện mạnh.
- **Nhược điểm:** Cần cài đặt Node.js và npm, cấu hình bundler phức tạp, kích thước file output lớn, phát sinh thêm bước build trong CI/CD pipeline và Dockerfile.
- **Lý do từ chối:** Không cần thiết cho phạm vi ứng dụng nội bộ / desktop, làm tăng độ phức tạp vận hành của dự án.

### 2. Sử dụng Tailwind CSS
- **Ưu điểm:** Tiện ích style nhanh bằng utility classes.
- **Nhược điểm:** Cần CLI build hoặc CDN không tối ưu, làm rối mã nguồn HTML khi có nhiều biến thể giao diện kính mờ (Frosted glass).
- **Lý do từ chối:** Vanilla CSS với CSS Variables mang lại khả năng kiểm soát tuyệt đối trên từng pixel và hỗ trợ theme mượt mà mà không có dependency nào.

## Hệ quả & Lợi ích (Consequences)
- Không có bất kỳ dependency bên ngoài nào ở phía frontend ngoài các module nội tại.
- Thời gian tải trang ban đầu gần như bằng 0 (Zero-delay load time).
- Dockerfile chỉ cần đóng gói Spring Boot JAR mà không cần cài đặt Node.js trong môi trường build, giúp quá trình triển khai nhanh hơn 3 lần.
- Bộ kiểm thử giao diện có thể chạy trực tiếp bằng Node.js built-in test runner (`node --test src/test/js/frontend.test.cjs`) cực kỳ nhanh chóng.
