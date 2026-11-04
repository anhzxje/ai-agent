# Kế hoạch: Đơn giản hóa phần mã vừa thay đổi

## Phạm vi

Ưu tiên thay đổi gần đây trong `ToolManagerServiceTest` và vùng liên quan của `ToolManagerService`. Mục tiêu là dễ đọc hơn, giữ nguyên hành vi, kiểm thử và ranh giới bảo mật. Đây là kế hoạch, chưa triển khai; nếu sau khi khảo sát một thay đổi không thực sự cải thiện khả năng đọc thì bỏ qua.

## Quyết định

- Giữ nguyên chính sách đường dẫn, thứ tự kiểm tra, ngoại lệ, danh sách công cụ và trạng thái giao dịch. Không loại bỏ overload hoặc thay đổi API chỉ vì có vẻ dư thừa.
- Thực hiện từng thay đổi nhỏ, chạy kiểm thử liên quan sau mỗi thay đổi. Không sửa assertion hay phạm vi kiểm thử để làm kiểm thử vượt qua.
- Các thay đổi chưa commit trong README và tài liệu nằm ngoài phạm vi.

## Thứ tự công việc

1. Thiết lập kết quả kiểm thử ban đầu và kiểm tra lịch sử, nơi sử dụng và các trường hợp biên.
2. Xem xét bỏ cấu hình `allowedPaths` lặp lại trong kiểm thử đường dẫn được cho phép, nếu xác nhận `setUp` đã cung cấp cùng giá trị.
3. Xem xét làm rõ tài liệu và các tên đủ điều kiện trong `ToolManagerService`: khối Javadoc cũ đang đứng trước Javadoc của `isPathAllowed`; chỉ chỉnh nếu không làm mất thông tin hữu ích.
4. Kiểm tra toàn bộ và rà soát diff; không mở rộng sang CI hoặc cấu hình bảo mật nếu chưa có phạm vi mới.

Chi tiết, tiêu chí và phụ thuộc nằm trong `tasks/todo.md`.

## Rủi ro và cách giảm thiểu

| Rủi ro | Cách giảm thiểu |
| --- | --- |
| Thay đổi vô tình làm yếu sandbox hoặc master switch | Giữ logic kiểm tra và thứ tự gọi; chạy kiểm thử tool và sandbox. |
| Cấu hình chung trong test che khuất sự khác biệt giữa các ca | Kiểm tra `@BeforeEach` và các ca ghi đè trước khi bỏ cấu hình lặp. |
| Môi trường thiếu JDK, Node hoặc phụ thuộc Maven | Ghi rõ lệnh, lỗi và phần chưa kiểm chứng; không xem đó là kiểm thử thành công. |

## Điểm cần quyết định

- Nếu muốn đơn giản hóa các module khác, xác định phạm vi riêng sau khi hoàn tất vòng này.

## Bổ sung: Các phát hiện review toàn dự án

Ưu tiên xử lý riêng các vấn đề bảo mật trước, tiếp theo là độ tin cậy và hiệu năng. Các công việc bổ sung trong `tasks/todo.md` không thay thế hay đánh dấu hoàn tất kế hoạch đơn giản hóa ở trên.

1. Ngăn chèn HTML từ tên dự án và ngăn gửi thông tin xác thực sang localhost ngoài ý muốn.
2. Xác định và triển khai chính sách thu hồi phiên; kiểm thử logout và đổi mật khẩu.
3. Thiết kế phục hồi tác vụ sau restart và xử lý đồng thời theo từng hội thoại; xác minh trên nhiều tiến trình nếu hỗ trợ triển khai nhiều instance.
4. Phân trang các danh sách không giới hạn, cập nhật bên gọi và giữ thứ tự dữ liệu.

Các đề xuất trên cần được kiểm chứng bằng test hồi quy; không suy diễn rằng mọi vấn đề đã được tái hiện trong môi trường production. Chính sách thu hồi token và mô hình triển khai một hay nhiều instance cần được xác định trước khi chọn thiết kế tương ứng.
