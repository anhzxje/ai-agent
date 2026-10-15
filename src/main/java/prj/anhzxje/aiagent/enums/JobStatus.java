package prj.anhzxje.aiagent.enums;

/**
 * Trạng thái vòng đời (Lifecycle Status) của một tác vụ AI Agent:
 * - PENDING: Tác vụ vừa được tạo và đưa vào hàng đợi chờ thực thi.
 * - PROCESSING: Worker Thread đang gọi LLM (Google Gemini) và thực thi các Tool Calling.
 * - COMPLETED: Tác vụ đã hoàn tất thành công, có kết quả phân tích trong trường output JSON.
 * - ERROR: Tác vụ gặp lỗi trong quá trình thực thi (lỗi API, timeout, cú pháp...).
 * - CANCELLED: Tác vụ đã bị người dùng chủ động hủy bỏ.
 */
public enum JobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    ERROR,
    CANCELLED
}
