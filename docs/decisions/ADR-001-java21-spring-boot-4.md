# ADR-001: Lựa chọn Java 21 LTS và Spring Boot 4.1.0 làm Nền tảng Backend Chính

## Trạng thái
Đã chấp thuận (Accepted)

## Ngày quyết định
2026-10-24

## Bối cảnh & Vấn đề (Context)
Ban đầu, hệ thống AI Agent được thiết kế thử nghiệm bằng ngôn ngữ Python. Tuy nhiên, khi chuyển sang giai đoạn phát triển nền tảng production hỗ trợ nhiều người dùng đồng thời, quản lý tác vụ bất đồng bộ phức tạp và yêu cầu bảo mật cấp doanh nghiệp, nhóm phát triển gặp phải các thách thức:
- Khó khăn trong việc kiểm soát kiểu dữ liệu tĩnh nghiêm ngặt trên quy mô lớn.
- Chi phí bảo trì concurrency, thread management và transaction trong Python cao.
- Nhu cầu tích hợp mạnh mẽ với các hệ sinh thái bảo mật doanh nghiệp (Spring Security, JWT, RBAC).

## Quyết định (Decision)
Chuyển đổi toàn bộ Backend sang **Java 21 LTS** và **Spring Boot 4.1.0** kết hợp với **Spring Data JPA** và **Hibernate ORM**.

## Các giải pháp thay thế đã xem xét (Alternatives Considered)

### 1. Tiếp tục duy trì Python (FastAPI / LangChain)
- **Ưu điểm:** Hệ sinh thái thư viện AI phong phú, thử nghiệm nhanh.
- **Nhược điểm:** Hiệu năng đa luồng bị giới hạn bởi GIL (Global Interpreter Lock), chi phí triển khai và chuẩn hóa kiểu dữ liệu cho kiến trúc enterprise lớn hơn.
- **Lý do từ chối:** Không phù hợp với mục tiêu xây dựng nền tảng backend ổn định lâu dài, bảo mật chặt chẽ và tích hợp giao dịch ACID.

### 2. Node.js / NestJS (TypeScript)
- **Ưu điểm:** Cú pháp TypeScript hiện đại, chia sẻ code dễ dàng với Frontend.
- **Nhược điểm:** Hệ sinh thái ORM và quản lý giao dịch chưa trưởng thành bằng Spring/Hibernate trong việc xử lý các bài toán cascade delete phức tạp và bảo mật đa tầng.
- **Lý do từ chối:** Spring Boot cung cấp giải pháp bảo mật toàn diện (`spring-boot-starter-security`) và hỗ trợ tốt hơn cho các thư viện AI chuẩn doanh nghiệp.

## Hệ quả & Lợi ích (Consequences)
- Tận dụng các tính năng mới của Java 21: Pattern Matching, Record patterns, Switch expressions, tối ưu hóa bộ nhớ và hiệu năng cao.
- Spring Boot 4.1.0 mang lại khả năng quản lý cấu hình tập trung, actuator healthcheck và tương thích tự nhiên với Spring AI 2.0.0.
- Mã nguồn có cấu trúc chặt chẽ, dễ kiểm thử (148 tests tự động với Mockito và JUnit 5), phân tách rõ ràng giữa Controller, Service, Repository và Entity.
