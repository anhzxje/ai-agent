package prj.anhzxje.aiagent.enums;

/**
 * Phân loại vấn đề được phát hiện trong quá trình code review.
 */
public enum Category {
    /** Lỗi logic, ngoại lệ tiềm ẩn, xử lý Null Pointer Exception hoặc sai luồng dữ liệu */
    BUG,
    /** Lỗ hổng bảo mật: SQL Injection, Path Traversal, lộ lọt thông tin nhạy cảm, CSRF/XSS */
    SECURITY,
    /** Vấn đề hiệu năng: N+1 query, rò rỉ bộ nhớ, xử lý vòng lặp không tối ưu */
    PERFORMANCE,
    /** Chất lượng mã nguồn: Code smell, cấu trúc mã phức tạp, không tuân thủ quy ước Clean Code */
    CODE_QUALITY
}
