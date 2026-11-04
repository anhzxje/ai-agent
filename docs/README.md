# Trung tâm Tài liệu Kỹ thuật (Documentation Hub)
# Dự án: Soopi AI Agent Platform

Chào mừng bạn đến với trung tâm tài liệu kỹ thuật của **Soopi AI Agent Platform**.  
Hệ thống tài liệu này được xây dựng toàn diện, chi tiết và có cấu trúc chặt chẽ, phục vụ cho quá trình phát triển, kiểm thử, tích hợp và vận hành sản phẩm.

---

## 📚 Danh mục Tài liệu Chính

| Tài liệu | Mô tả nội dung chính | Đối tượng độc giả |
|---|---|---|
| [**Kiến trúc Hệ thống (ARCHITECTURE.md)**](file:///d:/AI%20Agent/aiagent/docs/ARCHITECTURE.md) | Kiến trúc tổng thể, mô hình phân lớp, vòng lặp AI Agent & Tool Calling Loop, cơ chế bảo mật Sandbox chống Path Traversal, sơ đồ dữ liệu quan hệ ERD và các Sequence Diagrams. | Kỹ sư kiến trúc, Lập trình viên, AI Engineer |
| [**Đặc tả RESTful API (API_SPECIFICATION.md)**](file:///d:/AI%20Agent/aiagent/docs/API_SPECIFICATION.md) | Chi tiết toàn bộ 7 nhóm REST API: Xác thực JWT, Người dùng, Quản lý Dự án, Tác vụ AI, Hội thoại Chat đa phiên, Cấu hình Hệ thống, Bảng Quản trị Admin. Bao gồm schema, mã lỗi và ví dụ cURL. | Lập trình viên Frontend/Backend, QA/QC, Đối tác tích hợp |
| [**Hướng dẫn Triển khai & Vận hành (DEPLOYMENT_GUIDE.md)**](file:///d:/AI%20Agent/aiagent/docs/DEPLOYMENT_GUIDE.md) | Yêu cầu hệ thống, bảng biến môi trường chuẩn, hướng dẫn chạy Local qua Maven Wrapper, chạy Container qua Docker Compose, cấu hình Nginx SSL, sao lưu MySQL và xử lý sự cố. | DevOps, SysAdmin, Lập trình viên |
| [**Hướng dẫn Lập trình viên & Mở rộng (DEVELOPER_GUIDE.md)**](file:///d:/AI%20Agent/aiagent/docs/DEVELOPER_GUIDE.md) | Thiết lập môi trường IDE, cấu trúc package, hướng dẫn từng bước: Thêm Tool mới cho Agent, Thêm TaskType mới, Thêm mô hình AI mới, chiến lược viết test tự động và đóng gói JAR. | Lập trình viên, Cộng tác viên mã nguồn |
| [**Quy chuẩn Đóng góp Mã nguồn (CONTRIBUTING.md)**](file:///d:/AI%20Agent/aiagent/CONTRIBUTING.md) | Mô hình phân nhánh Git Flow, chuẩn Conventional Commits, quy trình gửi Pull Request và checklist kiểm thử trước khi commit. | Toàn bộ thành viên dự án |

---

## 🏛️ Bản ghi Quyết định Kiến trúc (Architecture Decision Records - ADRs)

Các quyết định kỹ thuật quan trọng của hệ thống được ghi nhận theo chuẩn ADR tại thư mục [`docs/decisions/`](file:///d:/AI%20Agent/aiagent/docs/decisions):

- [**ADR-001: Lựa chọn Java 21 LTS và Spring Boot 4.1.0 làm Nền tảng Backend Chính**](file:///d:/AI%20Agent/aiagent/docs/decisions/ADR-001-java21-spring-boot-4.md)  
  *Lý do chuyển đổi từ Python sang Java 21: Kiểm soát kiểu dữ liệu tĩnh nghiêm ngặt, tối ưu đa luồng, quản lý giao dịch ACID và bảo mật cấp doanh nghiệp.*

- [**ADR-002: Lựa chọn Spring AI và Google Gemini API với Cơ chế Dynamic Tool Calling**](file:///d:/AI%20Agent/aiagent/docs/decisions/ADR-002-spring-ai-gemini-tool-calling.md)  
  *Lý do chọn Spring AI và Google Gemini: Cửa sổ ngữ cảnh cực lớn (1M+ tokens), tốc độ phản hồi cao của `gemini-3.5-flash-lite`, cơ chế Function Calling tự trị linh hoạt.*

- [**ADR-003: Kiến trúc Bảo mật Sandbox Hệ thống Tệp và Xác thực Stateless JWT**](file:///d:/AI%20Agent/aiagent/docs/decisions/ADR-003-security-and-sandbox-isolation.md)  
  *Chiến lược phòng thủ đa tầng: Ngăn chặn triệt để Path Traversal qua chuẩn hóa đường dẫn, giới hạn kích thước tệp, bảo vệ chống IDOR và xác thực JWT phi trạng thái.*

- [**ADR-004: Thiết kế Frontend Web Modular với Vanilla JavaScript ES6+**](file:///d:/AI%20Agent/aiagent/docs/decisions/ADR-004-vanilla-modular-js-frontend.md)  
  *Lý do sử dụng Vanilla JS ES6 Modules thay vì React/Vue: Tốc độ tải trang tức thì, zero-dependency, giảm tải quy trình build và dễ dàng đóng gói trên máy tính cục bộ.*
