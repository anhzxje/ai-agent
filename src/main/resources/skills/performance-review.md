# performance-review

## Mục tiêu
Tìm N+1 queries, blocking operations, thuật toán không hiệu quả, truy vấn database không tối ưu và resource leaks.
## Quy trình
- Theo dõi query trong vòng lặp, lazy-loading và phạm vi transaction; đọc repository và caller.
- Xét độ phức tạp thuật toán theo kích thước input, pagination, allocation và I/O lặp.
- Kiểm tra stream, connection, executor và tài nguyên có được đóng hoặc giới hạn không.
- Chỉ gọi blocking là vấn đề khi nó xảy ra trên đường thực thi có yêu cầu concurrency/latency phù hợp.
## Tiêu chuẩn bằng chứng
Chỉ rõ code path và điều kiện tải; không bịa số đo latency/throughput. Thiếu query plan hoặc benchmark phải nêu giới hạn kết luận.
## Giới hạn
Index/cache chỉ là suggestions nếu chưa có bằng chứng truy vấn gây vấn đề. Không bỏ kiểm tra bảo mật để tối ưu.
