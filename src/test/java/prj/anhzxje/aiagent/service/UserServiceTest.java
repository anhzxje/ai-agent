package prj.anhzxje.aiagent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import prj.anhzxje.aiagent.dto.admin.UserSummaryResponse;
import prj.anhzxje.aiagent.dto.auth.ChangePasswordRequest;
import prj.anhzxje.aiagent.dto.auth.UserProfileResponse;
import prj.anhzxje.aiagent.dto.user.UserPreferencesResponse;
import prj.anhzxje.aiagent.entity.AgentTask;
import prj.anhzxje.aiagent.entity.ChatConversation;
import prj.anhzxje.aiagent.entity.Project;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.enums.Role;
import prj.anhzxje.aiagent.exception.ResourceNotFoundException;
import prj.anhzxje.aiagent.repository.*;
import prj.anhzxje.aiagent.security.CustomUserDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử Đơn vị cho UserService (UserServiceTest):
 * Kiểm tra toàn diện các nghiệp vụ:
 * 1. Lấy thông tin cá nhân (Profile).
 * 2. Đổi mật khẩu tài khoản cá nhân.
 * 3. Quản lý trạng thái và vai trò người dùng (Admin).
 * 4. Ngăn chặn Admin tự khóa, tự hạ quyền, tự xóa tài khoản của chính mình.
 * 5. Xóa tài khoản người dùng kèm dọn dẹp phân cấp (Cascade Delete).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserPreferencesService userPreferencesService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AgentTaskRepository agentTaskRepository;

    @Mock
    private ChatConversationRepository chatConversationRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @InjectMocks
    private UserService userService;

    private User sampleUser;
    private User adminUser;
    private CustomUserDetails adminUserDetails;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(2L)
                .username("john")
                .email("john@example.com")
                .password("encoded_old_pass")
                .role(Role.USER)
                .isLocked(false)
                .build();

        adminUser = User.builder()
                .id(1L)
                .username("admin")
                .email("admin@test.com")
                .role(Role.ADMIN)
                .isLocked(false)
                .build();

        adminUserDetails = new CustomUserDetails(adminUser);
    }

    @Test
    void testGetUserProfile() {
        UserPreferencesResponse prefs = UserPreferencesResponse.builder()
                .language("vi")
                .theme("dark")
                .preferredModel("gemini-3.5-flash-lite")
                .build();
        when(userPreferencesService.getPreferences(sampleUser)).thenReturn(prefs);

        UserProfileResponse res = userService.getUserProfile(sampleUser);

        assertNotNull(res);
        assertEquals("john", res.getUsername());
        assertEquals("vi", res.getLanguage());
        assertEquals("dark", res.getTheme());
    }

    @Test
    void testChangePassword_Success() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("old_pass", "encoded_old_pass")).thenReturn(true);
        when(passwordEncoder.matches("new_pass", "encoded_old_pass")).thenReturn(false);
        when(passwordEncoder.encode("new_pass")).thenReturn("encoded_new_pass");

        ChangePasswordRequest req = ChangePasswordRequest.builder()
                .currentPassword("old_pass")
                .newPassword("new_pass")
                .confirmPassword("new_pass")
                .build();

        Map<String, String> res = userService.changePassword(2L, req);

        assertEquals("Đổi mật khẩu thành công", res.get("message"));
        assertEquals("encoded_new_pass", sampleUser.getPassword());
        verify(userRepository, times(1)).save(sampleUser);
        verify(auditLogService, times(1)).log(eq("john"), eq("CHANGE_PASSWORD"), anyString());
    }

    @Test
    void testChangePassword_WrongCurrentPassword() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong_pass", "encoded_old_pass")).thenReturn(false);

        ChangePasswordRequest req = ChangePasswordRequest.builder()
                .currentPassword("wrong_pass")
                .newPassword("new_pass")
                .confirmPassword("new_pass")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.changePassword(2L, req));
        assertEquals("Mật khẩu hiện tại không chính xác", ex.getMessage());
    }

    @Test
    void testGetAllUsersSummary() {
        List<Object[]> projCounts = new ArrayList<>();
        projCounts.add(new Object[]{2L, 5L});
        when(projectRepository.countProjectsGroupedByUser()).thenReturn(projCounts);

        List<Object[]> taskCounts = new ArrayList<>();
        taskCounts.add(new Object[]{2L, 10L});
        when(agentTaskRepository.countTasksGroupedByUser()).thenReturn(taskCounts);

        when(userRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(sampleUser));

        List<UserSummaryResponse> users = userService.getAllUsersSummary();

        assertEquals(1, users.size());
        assertEquals("john", users.get(0).getUsername());
        assertEquals(5L, users.get(0).getProjectCount());
        assertEquals(10L, users.get(0).getTaskCount());
    }

    @Test
    void testUpdateUserStatus_Success() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        UserSummaryResponse res = userService.updateUserStatus(2L, true, adminUserDetails);

        assertNotNull(res);
        assertTrue(sampleUser.getIsLocked());
        verify(auditLogService, times(1)).log(eq("admin"), eq("LOCK_USER"), anyString());
    }

    @Test
    void testUpdateUserStatus_SelfLockForbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.updateUserStatus(1L, true, adminUserDetails));

        assertEquals("Không thể tự khóa tài khoản quản trị của chính mình", ex.getMessage());
    }

    @Test
    void testUpdateUserRole_SelfDemoteForbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.updateUserRole(1L, Role.USER, adminUserDetails));

        assertEquals("Không thể tự hạ quyền của chính tài khoản quản trị hiện tại", ex.getMessage());
    }

    @Test
    void testDeleteUserCascade_Success() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleUser));
        when(agentTaskRepository.findByUserId(2L)).thenReturn(List.of(new AgentTask()));
        when(chatConversationRepository.findByUserIdOrderByUpdatedAtDesc(2L)).thenReturn(List.of(new ChatConversation()));
        when(projectRepository.findByUserId(2L)).thenReturn(List.of(new Project()));

        Map<String, String> res = userService.deleteUserCascade(2L, adminUserDetails);

        assertTrue(res.get("message").contains("Đã xóa tài khoản john"));
        verify(agentTaskRepository, times(1)).deleteAll(anyList());
        verify(chatMessageRepository, times(1)).deleteByConversationId(any());
        verify(userPreferencesRepository, times(1)).deleteByUserId(2L);
        verify(projectRepository, times(1)).deleteAll(anyList());
        verify(userRepository, times(1)).delete(sampleUser);
    }

    @Test
    void testDeleteUserCascade_SelfDeleteForbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.deleteUserCascade(1L, adminUserDetails));

        assertEquals("Không thể xóa chính tài khoản quản trị viên hiện tại", ex.getMessage());
    }
}
