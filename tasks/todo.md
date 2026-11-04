# Công việc: Đơn giản hóa mã gần đây

## 1. Xác lập cơ sở và kiểm tra ngữ cảnh

**Mô tả:** Đọc lịch sử, bên gọi và các test của dịch vụ công cụ, đặc biệt đường dẫn, lựa chọn công cụ và các ngoại lệ; ghi lại kết quả kiểm thử trước khi sửa.

**Tiêu chí:**
- [ ] Xác định hành vi và các trường hợp biên cần giữ; đánh dấu cơ hội không đủ chắc chắn là bỏ qua.
- [ ] Ghi kết quả kiểm thử cơ sở và mọi hạn chế môi trường.

**Kiểm tra:**
- [ ] `./mvnw.cmd -Dtest=ToolManagerServiceTest,SandboxToolsTest test` trong PowerShell.
- [ ] Kiểm tra lịch sử và nơi gọi bằng `git blame` và `rg`.

**Phụ thuộc:** Không. **Phạm vi:** đọc `ToolManagerService.java`, `ToolManagerServiceTest.java`, `SandboxTools.java` và test liên quan; không sửa mã.

## 2. Giảm cấu hình test lặp

**Mô tả:** Kiểm tra cấu hình `allowedPaths` trong `setUp` và ca `testResolveActiveTools_WithAllowedSandboxPath`; chỉ bỏ cấu hình lặp nếu tương đương hoàn toàn.

**Tiêu chí:**
- [ ] Các ca được phép, bị từ chối và đường dẫn ngoài vùng cho phép giữ nguyên ý nghĩa và assertion.
- [ ] Không thay đổi cấu hình riêng của các ca sử dụng thư mục tạm.

**Kiểm tra:**
- [ ] `./mvnw.cmd -Dtest=ToolManagerServiceTest test`.
- [ ] Đọc diff để xác nhận không thay đổi logic hay assertion.

**Phụ thuộc:** 1. **Tệp dự kiến:** `src/test/java/prj/anhzxje/aiagent/service/ToolManagerServiceTest.java`. **Quy mô:** S.

## Checkpoint sau công việc 1–2

- [ ] So sánh kết quả kiểm thử với cơ sở; xử lý hoặc báo cáo lỗi trước khi tiếp tục.
- [ ] Xem lại diff và bảo đảm các thay đổi hiện có của người dùng được giữ nguyên.

## 3. Làm rõ tài liệu dịch vụ công cụ

**Mô tả:** Rà soát khối Javadoc liên tiếp trước `isPathAllowed` và tên lớp viết đầy đủ; chỉnh phần thực sự gây khó đọc, giữ nội dung có ích và hành vi nguyên vẹn.

**Tiêu chí:**
- [ ] Tài liệu gắn với phương thức phù hợp và không mô tả sai quy trình hiện tại.
- [ ] Không đổi API, chính sách sandbox, thứ tự gọi, ngoại lệ hoặc transaction.

**Kiểm tra:**
- [ ] `./mvnw.cmd -Dtest=ToolManagerServiceTest,SandboxToolsTest test` sau thay đổi.
- [ ] Đọc diff; bỏ qua thay đổi nếu không rõ ràng hơn.

**Phụ thuộc:** 1, checkpoint 1–2. **Tệp dự kiến:** `src/main/java/prj/anhzxje/aiagent/service/ToolManagerService.java`. **Quy mô:** S.

## Checkpoint hoàn tất

- [ ] `./mvnw.cmd --batch-mode --no-transfer-progress verify`.
- [ ] `node --test` theo CI; báo rõ nếu không chạy được.
- [ ] Rà soát diff: không thay đổi ngoài phạm vi, không làm yếu xử lý lỗi; báo cáo kiểm thử và giới hạn.
- [ ] Người dùng xem xét kế hoạch trước khi triển khai.

---

# Công việc bổ sung từ review toàn dự án

## 4. Chặn HTML độc hại từ tên dự án — Critical

**Mô tả:** Bảo vệ vị trí hiển thị tên dự án trong thẻ tác vụ và rà soát các vị trí tương tự có dữ liệu không đáng tin cậy.

**Tiêu chí:**
- [ ] Tên có thẻ HTML, dấu nháy và ký tự đặc biệt hiển thị nguyên văn, không tạo phần tử hoặc thuộc tính thực thi.
- [ ] Tên dự án thông thường và thao tác chọn tác vụ vẫn hoạt động.

**Kiểm tra:**
- [ ] `node --test`; thêm test hồi quy với tên độc hại.
- [ ] Kiểm tra DOM thực tế trên trình duyệt.

**Phụ thuộc:** Không. **Tệp dự kiến:** `src/main/resources/static/js/app.js`, `src/test/js/frontend.test.cjs`. **Quy mô:** S.

## 5. Giới hạn backend dự phòng localhost — Critical

**Mô tả:** Xác định chế độ desktop được hỗ trợ; giới hạn fallback localhost vào môi trường được cấu hình rõ ràng, gồm đăng nhập, đăng ký và kiểm tra kết nối.

**Tiêu chí:**
- [ ] Trang web không gửi mật khẩu hoặc bearer token đến localhost khi backend hiện tại lỗi.
- [ ] Backend desktop được cấu hình tường minh vẫn hoạt động; lỗi kết nối được thông báo.

**Kiểm tra:**
- [ ] `node --test`; thêm test giả lập lỗi mạng, xác nhận URL và header của các request.
- [ ] Kiểm tra web và desktop nếu môi trường desktop sẵn có.

**Phụ thuộc:** Không. **Tệp dự kiến:** `src/main/resources/static/js/api.js`, `src/test/js/frontend.test.cjs`. **Quy mô:** S.

## Checkpoint bảo mật giao diện

- [ ] Công việc 4–5 đạt tiêu chí và test hồi quy; xác nhận không phát sinh gửi token đến origin ngoài dự kiến.

## 6. Thu hồi phiên xác thực

**Mô tả:** Xác định chính sách logout và đổi mật khẩu, sau đó thiết kế cơ chế thu hồi JWT phù hợp với triển khai, kể cả nhiều instance nếu được hỗ trợ.

**Tiêu chí:**
- [ ] Token bị thu hồi không truy cập được API bảo vệ; hành vi với các phiên khác được quy định rõ.
- [ ] Đăng nhập mới và tài khoản bị khóa vẫn xử lý đúng.

**Kiểm tra:**
- [ ] Test tích hợp dùng lại token sau logout và đổi mật khẩu, cộng test đăng nhập mới.
- [ ] `./mvnw.cmd --batch-mode --no-transfer-progress verify`.

**Phụ thuộc:** Xác định chính sách phiên và mô hình triển khai. **Tệp dự kiến:** dịch vụ xác thực, bộ lọc JWT, lưu trữ phiên và test liên quan; chia nhỏ sau khi chọn thiết kế. **Quy mô:** cần phân rã trước khi triển khai.

## 7. Phục hồi tác vụ sau restart

**Mô tả:** Chọn cơ chế hàng đợi bền vững hoặc phục hồi có lease; xử lý task `PENDING` và `PROCESSING` bị bỏ dở, không chạy trùng ngoài ý muốn.

**Tiêu chí:**
- [ ] Task đang chờ hoặc đang chạy khi tiến trình dừng được phục hồi hoặc chuyển sang trạng thái lỗi có thể retry.
- [ ] Worker cũ không ghi đè kết quả của lần chạy mới; an toàn với số instance được hỗ trợ.

**Kiểm tra:**
- [ ] Test tích hợp mô phỏng restart, task bỏ dở và worker hoàn tất muộn.
- [ ] `./mvnw.cmd --batch-mode --no-transfer-progress verify`.

**Phụ thuộc:** Xác định mô hình triển khai. **Tệp dự kiến:** `AgentTaskService`, `AgentTaskExecutor`, `AgentTaskRepository` và test liên quan; chia nhỏ sau khi chọn thiết kế. **Quy mô:** cần phân rã trước khi triển khai.

## 8. Đồng bộ các lượt chat cùng hội thoại

**Mô tả:** Xác định hành vi khi gửi đồng thời và chọn cơ chế đảm bảo thứ tự phù hợp với số instance được hỗ trợ.

**Tiêu chí:**
- [ ] Hai yêu cầu đồng thời không tạo thứ tự hoặc ngữ cảnh sai so với chính sách đã chọn.
- [ ] Lỗi AI và xóa hội thoại trong lúc chờ có hành vi rõ ràng.

**Kiểm tra:**
- [ ] Test tích hợp điều khiển thứ tự hai lời gọi AI và kiểm tra lịch sử sau khi hoàn tất.
- [ ] `./mvnw.cmd --batch-mode --no-transfer-progress verify`.

**Phụ thuộc:** Xác định hành vi đồng thời và mô hình triển khai. **Tệp dự kiến:** `ChatService`, lưu trữ hội thoại và test liên quan; chia nhỏ sau khi chọn thiết kế. **Quy mô:** cần phân rã trước khi triển khai.

## Checkpoint độ tin cậy

- [ ] Test hồi quy cho công việc 6–8 đạt; ghi rõ giới hạn môi trường và chính sách đã chọn.

## 9. Phân trang tác vụ lỗi

**Mô tả:** Giới hạn truy vấn và phản hồi danh sách tác vụ lỗi, cập nhật giao diện quản trị theo hợp đồng API mới.

**Tiêu chí:**
- [ ] Có thể xem các trang liên tiếp theo thứ tự ổn định với kích thước tối đa được giới hạn.
- [ ] Giao diện quản trị không bỏ sót việc điều hướng trang.

**Kiểm tra:**
- [ ] Test API/repository với nhiều hơn một trang; `./mvnw.cmd --batch-mode --no-transfer-progress verify` và `node --test`.

**Phụ thuộc:** Không. **Tệp dự kiến:** `AdminController`, `AgentTaskRepository`, `api.js`, `adminView.js` và test liên quan; chia thành lát API và UI nếu cần. **Quy mô:** cần phân rã trước khi triển khai.

## 10. Phân trang lịch sử hội thoại

**Mô tả:** Giới hạn số tin nhắn khi tải chi tiết hội thoại và hỗ trợ tải các trang cũ hơn.

**Tiêu chí:**
- [ ] Tin nhắn được phân trang theo thứ tự ổn định, không trùng hoặc bỏ sót khi có timestamp bằng nhau.
- [ ] Giao diện tải thêm lịch sử và giữ đúng thứ tự hiển thị.

**Kiểm tra:**
- [ ] Test API/repository và giao diện với nhiều trang, timestamp trùng; chạy `./mvnw.cmd --batch-mode --no-transfer-progress verify` và `node --test`.

**Phụ thuộc:** Không. **Tệp dự kiến:** `ChatService`, `ChatMessageRepository`, controller/DTO, `api.js`, `chatView.js` và test liên quan; chia thành lát API và UI. **Quy mô:** cần phân rã trước khi triển khai.

## Checkpoint review hoàn tất

- [ ] Các vấn đề Critical và bắt buộc đã được xử lý hoặc ghi rõ quyết định trì hoãn.
- [ ] Build, test backend và frontend đạt; kiểm tra trình duyệt và môi trường tích hợp phù hợp.
- [ ] Rà soát lại các thay đổi và cập nhật tài liệu API khi hợp đồng thay đổi.
