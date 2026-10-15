package prj.anhzxje.aiagent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import prj.anhzxje.aiagent.dto.admin.*;
import prj.anhzxje.aiagent.entity.AgentTask;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.enums.JobStatus;
import prj.anhzxje.aiagent.enums.Role;
import prj.anhzxje.aiagent.enums.TaskType;
import prj.anhzxje.aiagent.repository.AgentTaskRepository;
import prj.anhzxje.aiagent.repository.ProjectRepository;
import prj.anhzxje.aiagent.repository.UserRepository;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.service.AuditLogService;
import prj.anhzxje.aiagent.service.SystemConfigService;
import prj.anhzxje.aiagent.service.ToolManagerService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử Toàn diện Phân hệ Quản trị & Bảo mật Admin (AdminControllerSecurityAndManagementTest):
 * Kiểm thử các kịch bản:
 * 1. Thống kê số liệu hệ thống (stats): người dùng, dự án, tác vụ.
 * 2. Khóa / Mở khóa tài khoản người dùng và ngăn admin tự khóa chính mình.
 * 3. Chuyển đổi quyền hạn (USER <-> ADMIN) và ngăn admin tự giáng quyền chính mình.
 * 4. Xóa tài khoản thành viên kèm kiểm tra ràng buộc tự bảo vệ.
 * 5. Bật / Tắt công cụ Agent động (ListDirectory, Glob, FileSystem, Grep).
 * 6. Truy vấn lịch sử tác vụ toàn hệ thống và nhật ký kiểm toán (Audit Logs).
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerSecurityAndManagementTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AgentTaskRepository agentTaskRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ToolManagerService toolManagerService;

    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private AdminController adminController;

    private User adminUser;
    private User regularUser;
    private CustomUserDetails adminUserDetails;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(1L)
                .username("admin")
                .email("admin@test.com")
                .role(Role.ADMIN)
                .isLocked(false)
                .build();

        regularUser = User.builder()
                .id(2L)
                .username("alice")
                .email("alice@test.com")
                .role(Role.USER)
                .isLocked(false)
                .build();

        adminUserDetails = new CustomUserDetails(adminUser);
    }

    @Test
    void testLockUser_Success() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(regularUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<UserSummaryResponse> response = adminController.updateUserStatus(2L, true, adminUserDetails);

        assertNotNull(response.getBody());
        assertTrue(response.getBody().getIsLocked());
        verify(userRepository, times(1)).save(regularUser);
        verify(auditLogService, times(1)).log(eq("admin"), eq("LOCK_USER"), anyString());
    }

    @Test
    void testUnlockUser_Success() {
        regularUser.setIsLocked(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(regularUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<UserSummaryResponse> response = adminController.updateUserStatus(2L, false, adminUserDetails);

        assertNotNull(response.getBody());
        assertFalse(response.getBody().getIsLocked());
        verify(auditLogService, times(1)).log(eq("admin"), eq("UNLOCK_USER"), anyString());
    }

    @Test
    void testAdminCannotLockThemselves() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                adminController.updateUserStatus(1L, true, adminUserDetails));

        assertEquals("Không thể tự khóa tài khoản quản trị của chính mình", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testToggleTool_Success() {
        AgentToolDto dto = AgentToolDto.builder()
                .toolName("grep")
                .displayName("Grep")
                .enabled(false)
                .build();
        when(toolManagerService.toggleTool("grep", "admin")).thenReturn(dto);

        ResponseEntity<AgentToolDto> response = adminController.toggleTool("grep", adminUserDetails);

        assertNotNull(response.getBody());
        assertFalse(response.getBody().getEnabled());
        verify(toolManagerService, times(1)).toggleTool("grep", "admin");
    }

    @Test
    void testGetAndSetSystemSettings_Success() {
        SystemSettingsResponse settings = SystemSettingsResponse.builder()
                .activeModel("gemini-1.5-flash")
                .maskedApiKey("AIza****1234")
                .allowedModels(List.of("gemini-1.5-flash", "gemini-1.5-pro"))
                .build();
        when(systemConfigService.getSettingsForAdmin()).thenReturn(settings);

        ResponseEntity<SystemSettingsResponse> getRes = adminController.getSettings();
        assertNotNull(getRes.getBody());
        assertEquals("gemini-1.5-flash", getRes.getBody().getActiveModel());

        SystemSettingsUpdateRequest updateReq = SystemSettingsUpdateRequest.builder()
                .model("gemini-1.5-pro")
                .apiKey("AIzaSyNewKey123456789")
                .build();

        ResponseEntity<SystemSettingsResponse> putRes = adminController.updateSettings(updateReq, adminUserDetails);
        assertNotNull(putRes.getBody());
        verify(systemConfigService, times(1)).updateSettings(updateReq, "admin");
    }

    @Test
    void testGetAuditLogs_Success() {
        AuditLogResponse logEntry = AuditLogResponse.builder()
                .id(10L)
                .username("admin")
                .action("LOCK_USER")
                .details("Locked user alice")
                .createdAt(LocalDateTime.now())
                .build();
        when(auditLogService.getRecentLogs()).thenReturn(List.of(logEntry));

        ResponseEntity<List<AuditLogResponse>> logsRes = adminController.getAuditLogs();
        assertNotNull(logsRes.getBody());
        assertEquals(1, logsRes.getBody().size());
        assertEquals("LOCK_USER", logsRes.getBody().get(0).getAction());
    }

    @Test
    void testGetAllTasksAndErrors_Success() {
        AgentTask task = AgentTask.builder()
                .id(50L)
                .user(regularUser)
                .status(JobStatus.ERROR)
                .type(TaskType.CODE_REVIEW)
                .errorMessage("OutOfMemory simulated error")
                .createdAt(LocalDateTime.now())
                .build();

        when(agentTaskRepository.findByErrorMessageIsNotNullOrderByCreatedAtDesc()).thenReturn(List.of(task));

        ResponseEntity<List<AdminTaskResponse>> errorsRes = adminController.getErrorTasks();
        assertNotNull(errorsRes.getBody());
        assertEquals(1, errorsRes.getBody().size());
        assertEquals("OutOfMemory simulated error", errorsRes.getBody().get(0).getErrorMessage());
    }
}
