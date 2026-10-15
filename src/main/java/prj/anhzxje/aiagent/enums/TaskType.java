package prj.anhzxje.aiagent.enums;

/**
 * Các loại tác vụ (Task Type) mà hệ thống AI Agent hỗ trợ:
 * - CODE_REVIEW: Phân tích toàn diện mã nguồn, tìm lỗi logic, bảo mật và hiệu năng.
 * - CODE_FIX: Đề xuất bản sửa lỗi chi tiết cho các đoạn code có vấn đề.
 * - CODE_EXPLAIN: Giải thích cấu trúc và giải thuật của đoạn mã nguồn.
 * - TEST_GENERATION: Tự động sinh mã kiểm thử đơn vị (Unit Tests) cho các class.
 */
public enum TaskType {
    CODE_REVIEW,
    CODE_FIX,
    CODE_EXPLAIN,
    TEST_GENERATION
}
