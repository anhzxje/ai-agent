package prj.anhzxje.aiagent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import prj.anhzxje.aiagent.dto.admin.AuditLogResponse;
import prj.anhzxje.aiagent.entity.AuditLog;
import prj.anhzxje.aiagent.repository.AuditLogRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử Đơn vị cho AuditLogService:
 * 1. Ghi log thành công với dữ liệu hợp lệ.
 * 2. Cắt ngắn an toàn cho username, action và details nếu vượt quá độ dài cột CSDL.
 * 3. Thiết kế phòng vệ: Khi repository ném ngoại lệ, phương thức không làm văng lỗi ra ngoài.
 * 4. Lấy danh sách 100 log gần nhất cho Admin.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    void testLog_Success() {
        auditLogService.log("user1", "LOGIN", "Đăng nhập thành công", "127.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("user1", saved.getUsername());
        assertEquals("LOGIN", saved.getAction());
        assertEquals("Đăng nhập thành công", saved.getDetails());
        assertEquals("127.0.0.1", saved.getIpAddress());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void testLog_TruncatesLongInputsDefensively() {
        String longUsername = "u".repeat(70);
        String longAction = "A".repeat(80);
        String longDetails = "D".repeat(2500);

        auditLogService.log(longUsername, longAction, longDetails, "192.168.1.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals(50, saved.getUsername().length());
        assertEquals(50, saved.getAction().length());
        assertTrue(saved.getDetails().length() <= 2000);
        assertTrue(saved.getDetails().endsWith("..."));
    }

    @Test
    void testLog_ExceptionSwallowedDefensively() {
        when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("DB Connection Timeout"));

        // Phải không ném ngoại lệ ra ngoài để bảo vệ transaction chính
        assertDoesNotThrow(() -> auditLogService.log("user1", "TASK_CREATE", "Details"));
    }

    @Test
    void testGetRecentLogs() {
        AuditLog log1 = AuditLog.builder().id(1L).username("admin").action("LOCK_USER").details("Khóa user").build();
        when(auditLogRepository.findTop100ByOrderByCreatedAtDesc()).thenReturn(List.of(log1));

        List<AuditLogResponse> res = auditLogService.getRecentLogs();

        assertNotNull(res);
        assertEquals(1, res.size());
        assertEquals("admin", res.get(0).getUsername());
        assertEquals("LOCK_USER", res.get(0).getAction());
    }
}
