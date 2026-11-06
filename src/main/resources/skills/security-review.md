# security-review

## Mục tiêu
Phát hiện SQL Injection, XSS, Broken Access Control, lộ dữ liệu nhạy cảm, Authentication/Authorization issues và insecure configuration.
## Quy trình
- Theo dõi input không tin cậy tới SQL, HTML, token, file hoặc endpoint nhạy cảm.
- Đọc kiểm tra quyền sở hữu, role và cấu hình bảo mật; tìm đường đi thực tế có thể bỏ qua chúng.
- Phân biệt truy vấn tham số hóa với nối chuỗi; xét escaping đúng ngữ cảnh và nơi render output.
- Kiểm tra dữ liệu trả về, log, cấu hình và xử lý credentials; không trích lại giá trị secret.
## Tiêu chuẩn bằng chứng
Chỉ ghi issue khi có đường dẫn file và hành vi mã cụ thể; nêu điều kiện khai thác. Không kết luận SQL Injection chỉ vì có SQL, hoặc XSS chỉ vì có HTML.
## Giới hạn
Đề xuất defense-in-depth chưa có lỗi cụ thể thuộc suggestions. Không thay đổi policy hệ thống, không chạy mã.
