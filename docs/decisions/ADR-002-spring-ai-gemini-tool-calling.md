# ADR-002: Lựa chọn Spring AI và Google Gemini API với Cơ chế Dynamic Tool Calling

## Trạng thái
Đã chấp thuận (Accepted)

## Ngày quyết định
2026-10-24

## Bối cảnh & Vấn đề (Context)
Nhiệm vụ trọng tâm của Soopi AI Agent là tự động rà soát mã nguồn (Code Review) trên toàn bộ dự án của người dùng. Một mô hình LLM đơn thuần chỉ có thể nhận input văn bản cố định, không thể tự mình duyệt cây thư mục hoặc tìm kiếm tệp tin. Cần một cơ chế cho phép mô hình:
1. Tự động nhận biết các công cụ hệ thống tệp khả dụng.
2. Tự quyết định gọi công cụ nào (`list_directory`, `glob`, `file_system`, `grep`) với tham số cụ thể.
3. Nhận kết quả thực thi và tiếp tục chu trình suy luận cho đến khi hoàn thành báo cáo đánh giá.

## Quyết định (Decision)
Sử dụng **Spring AI 2.0.0** (`spring-ai-starter-model-google-genai`) kết hợp với dòng mô hình **Google Gemini** (`gemini-3.5-flash-lite`, `gemini-2.5-flash`, `gemini-2.5-pro`):
- Định nghĩa công cụ bằng chú thích chuẩn `@Tool` của Spring AI trong lớp `SandboxTools`.
- Khởi tạo `ChatClient` của Spring AI và truyền các công cụ khả dụng một cách linh hoạt theo từng tác vụ.
- Cấu hình hạ ngưỡng kiểm duyệt an toàn (`BLOCK_ONLY_HIGH` cho các danh mục Harm Category) để tránh việc mô hình từ chối phân tích các đoạn mã chứa lỗ hổng bảo mật hợp lệ.

## Các giải pháp thay thế đã xem xét (Alternatives Considered)

### 1. OpenAI GPT-4o với OpenAI Java SDK
- **Ưu điểm:** Khả năng suy luận mạnh, tài liệu phổ biến.
- **Nhược điểm:** Chi phí token cao hơn đáng kể, độ trễ phản hồi lớn hơn khi xử lý tác vụ quét mã nguồn nhiều tệp.
- **Lý do từ chối:** Google Gemini cung cấp cửa sổ ngữ cảnh cực lớn (Context Window lên tới 1M+ tokens) và model `gemini-3.5-flash-lite` có tốc độ xử lý vượt trội, chi phí cực thấp, phù hợp cho việc gọi tool liên tục.

### 2. Triển khai mô hình Open-Source cục bộ (Ollama / Llama 3)
- **Ưu điểm:** Hoàn toàn offline, không phụ thuộc vào kết nối mạng.
- **Nhược điểm:** Đòi hỏi phần cứng GPU máy chủ rất đắt đỏ để chạy suy luận và độ chính xác của Function Calling trên các model nhỏ không đủ tin cậy cho bài toán rà soát mã nguồn phức tạp.
- **Lý do từ chối:** Chưa khả thi với cấu hình máy chủ thông thường và không tối ưu chi phí hạ tầng.

## Hệ quả & Lợi ích (Consequences)
- Tác tử hoạt động hoàn toàn tự chủ mà không cần viết các kịch bản cứng (Hardcoded Scripts).
- Khả năng thay đổi mô hình AI linh hoạt trong lúc chạy (`PATCH /api/chat/conversations/{id}/model` hoặc qua cài đặt của Quản trị viên).
- Dễ dàng mở rộng thêm các công cụ mới mà không cần can thiệp vào tầng giao tiếp mạng với mô hình ngôn ngữ.
