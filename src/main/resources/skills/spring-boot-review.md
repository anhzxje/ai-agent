# spring-boot-review

## Mục tiêu
Kiểm tra Dependency Injection, bean lifecycle, transaction management, validation, Spring Security, JPA/Hibernate và Controller/Service/Repository separation.
## Quy trình
- Đọc build/config để xác định phiên bản, bean scope và dependency injection; không suy từ tên class.
- Theo dõi proxy/self-invocation, transaction boundary, async boundary và I/O bên trong transaction.
- Kiểm tra Bean Validation tại HTTP boundary, error mapping, endpoint authorization và ownership.
- Đọc fetch strategy, query, pagination và serialization; kiểm chứng đường gây N+1 hoặc lazy-loading failure.
- Xem phân tầng theo dependency/trách nhiệm thực tế, tránh áp mẫu kiến trúc không phù hợp quy mô.
## Tiêu chuẩn bằng chứng
Dẫn annotation, caller và cấu hình liên quan. Khi framework version chưa biết, nêu điều kiện thay vì khẳng định behavior.
## Giới hạn
Không yêu cầu Spring-specific API trong project không dùng Spring; unknown language cần kiểm tra build trước.
