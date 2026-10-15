package prj.anhzxje.aiagent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Kiểm thử Khởi tạo Ứng dụng chính (Aiagent Application Tests):
 * Kiểm tra tính hợp lệ và khả năng khởi tạo của class cấu hình Spring Boot gốc.
 */
class AiagentApplicationTests {

    @Test
    void testApplicationClassExists() {
        AiagentApplication app = new AiagentApplication();
        assertNotNull(app);
    }
}
