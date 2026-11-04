# ADR-003: Kiến trúc Bảo mật Sandbox Hệ thống Tệp và Xác thực Stateless JWT

## Trạng thái
Đã chấp thuận (Accepted)

## Ngày quyết định
2026-10-24

## Bối cảnh & Vấn đề (Context)
Khi trao quyền cho AI Agent đọc tệp tin trên máy chủ, rủi ro bảo mật nghiêm trọng nhất là lỗ hổng **Path Traversal** (Agent hoặc người dùng độc hại có thể gửi các đường dẫn tương đối dạng `../../../../etc/passwd` hoặc `C:\Windows\System32` để đánh cắp dữ liệu nhạy cảm của hệ điều hành).
Đồng thời, hệ thống phục vụ cả giao diện Web và ứng dụng Desktop (Electron), đòi hỏi cơ chế xác thực phải phi trạng thái (Stateless), an toàn và kiểm soát chặt chẽ quyền sở hữu dữ liệu (chống IDOR).

## Quyết định (Decision)
1. **Thiết lập Sandbox An toàn Chống Path Traversal**:
   - Mọi đường dẫn truy cập phải được xử lý qua `Path.normalize()` và `Path.toRealPath()`.
   - Bắt buộc kiểm tra điều kiện nghiêm ngặt: `realPath.startsWith(projectRootPath)`.
   - Danh sách đen tự động loại trừ (`EXCLUDED`): `.git`, `.idea`, `.vscode`, `node_modules`, `target`, `build`, `dist`, `out`, `venv`, tệp `.env`, `.env.*`, khóa SSH và chứng chỉ số.
   - Giới hạn cứng dung lượng tệp tin (tối đa 256 KiB) và dung lượng đầu ra của công cụ (tối đa 32 KiB). Ngăn chặn hoàn toàn việc nạp tệp nhị phân bằng cách kiểm tra ký tự null byte.
2. **Kiến trúc Xác thực Phi trạng thái (Stateless JWT)**:
   - Sử dụng thư viện JJWT (JSON Web Token) với thuật toán HMAC-SHA256 (`io.jsonwebtoken:jjwt-api:0.12.5`).
   - Token có thời hạn sống 24 giờ.
   - Toàn bộ quyền truy cập dữ liệu (Task, Project, Chat) đều được trích xuất trực tiếp từ danh tính người dùng trong Token (`SecurityContextHolder`), loại bỏ nguy cơ tấn công IDOR khi người dùng cố tình thay đổi `userId` trên URL.

## Các giải pháp thay thế đã xem xét (Alternatives Considered)

### 1. Sử dụng HTTP Session & Cookie thông thường
- **Ưu điểm:** Tích hợp mặc định của Servlet container, dễ hủy session trên server.
- **Nhược điểm:** Dễ bị tấn công CSRF, khó tích hợp với Desktop App (Electron) hoặc kiến trúc Microservices phân tán.
- **Lý do từ chối:** JWT Stateless mang lại tính linh hoạt cao hơn cho API và hỗ trợ tốt cho đa nền tảng client.

### 2. Dùng Docker container riêng cho mỗi tác vụ (Container per Task)
- **Ưu điểm:** Cách ly tuyệt đối ở cấp độ hệ điều hành.
- **Nhược điểm:** Thời gian khởi tạo container lớn (overhead vài giây mỗi task), tiêu tốn tài nguyên RAM và CPU máy chủ rất lớn khi có nhiều người dùng đồng thời.
- **Lý do từ chối:** Cơ chế Sandbox logic trong Java (`SandboxTools` với Path Normalization) đạt được hiệu năng tức thì (< 1ms) trong khi vẫn bảo đảm an toàn tuyệt đối.

## Hệ quả & Lợi ích (Consequences)
- Ngăn chặn triệt để nguy cơ lộ lọt dữ liệu máy chủ và tệp nhạy cảm.
- Bảo vệ tài nguyên máy chủ trước các cuộc tấn công DoS đọc tệp dung lượng lớn (Memory Exhaustion).
- Tích hợp liền mạch với Spring Security 6 và hỗ trợ cơ chế khóa tài khoản tức thì (`isLocked`).
