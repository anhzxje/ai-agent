package prj.anhzxje.aiagent.enums;

/**
 * Mức độ nghiêm trọng của issue được phát hiện.
 */
public enum Severity {
    /** Mức độ nghiêm trọng cao: Lỗ hổng bảo mật nghiêm trọng hoặc crash hệ thống */
    HIGH,
    /** Mức độ trung bình: Vấn đề hiệu năng, rò rỉ hoặc thiếu kiểm tra ràng buộc */
    MEDIUM,
    /** Mức độ thấp: Code smell, định dạng mã nguồn hoặc gợi ý cải tiến nhỏ */
    LOW
}
