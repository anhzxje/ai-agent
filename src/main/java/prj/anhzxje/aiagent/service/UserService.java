package prj.anhzxje.aiagent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * USER SERVICE - DỊCH VỤ QUẢN LÝ THÀNH VIÊN VÀ QUẢN TRỊ NGƯỜI DÙNG
 * ============================================================================
 * Tập trung toàn bộ logic nghiệp vụ liên quan đến người dùng:
 * 1. Xem hồ sơ cá nhân (kèm UserPreferences).
 * 2. Đổi mật khẩu tài khoản cá nhân có kiểm tra an toàn đa tầng.
 * 3. Thống kê và danh sách người dùng cho Admin (tối ưu O(1) query thay vì N+1).
 * 4. Khóa / Mở khóa tài khoản người dùng, đổi vai trò (USER <-> ADMIN).
 * 5. Xóa vĩnh viễn tài khoản người dùng kèm dọn dẹp phân cấp (Cascade Delete).
 * ============================================================================
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserPreferencesService userPreferencesService;
    private final AuditLogService auditLogService;
    private final ProjectRepository projectRepository;
    private final AgentTaskRepository agentTaskRepository;
    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserPreferencesRepository userPreferencesRepository;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       UserPreferencesService userPreferencesService,
                       AuditLogService auditLogService) {
        this(userRepository, passwordEncoder, userPreferencesService, auditLogService, null, null, null, null, null);
    }

    @Autowired
    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       UserPreferencesService userPreferencesService,
                       @Autowired(required = false) AuditLogService auditLogService,
                       @Autowired(required = false) ProjectRepository projectRepository,
                       @Autowired(required = false) AgentTaskRepository agentTaskRepository,
                       @Autowired(required = false) ChatConversationRepository chatConversationRepository,
                       @Autowired(required = false) ChatMessageRepository chatMessageRepository,
                       @Autowired(required = false) UserPreferencesRepository userPreferencesRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userPreferencesService = userPreferencesService;
        this.auditLogService = auditLogService;
        this.projectRepository = projectRepository;
        this.agentTaskRepository = agentTaskRepository;
        this.chatConversationRepository = chatConversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userPreferencesRepository = userPreferencesRepository;
    }

    /**
     * Lấy hồ sơ thông tin tài khoản người dùng kèm tùy chọn cá nhân.
     */
    public UserProfileResponse getUserProfile(User user) {
        UserPreferencesResponse prefs = userPreferencesService != null
                ? userPreferencesService.getPreferences(user)
                : UserPreferencesResponse.builder().language("vi").theme("dark").preferredModel("gemini-3.5-flash-lite").build();

        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .isLocked(Boolean.TRUE.equals(user.getIsLocked()))
                .createdAt(user.getCreatedAt())
                .language(prefs != null ? prefs.getLanguage() : "vi")
                .theme(prefs != null ? prefs.getTheme() : "dark")
                .preferredModel(prefs != null ? prefs.getPreferredModel() : "gemini-3.5-flash-lite")
                .build();
    }

    /**
     * Đổi mật khẩu cá nhân với kiểm tra bảo mật đa tầng.
     */
    @Transactional
    public Map<String, String> changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin tài khoản"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không chính xác");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Xác nhận mật khẩu mới không khớp");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới không được trùng với mật khẩu cũ");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        if (auditLogService != null) {
            auditLogService.log(user.getUsername(), "CHANGE_PASSWORD", "Người dùng đã đổi mật khẩu thành công");
        }

        return Map.of("message", "Đổi mật khẩu thành công");
    }

    /**
     * Lấy danh sách tóm tắt toàn bộ người dùng cho Admin Dashboard kèm số lượng Project & Task.
     */
    public List<UserSummaryResponse> getAllUsersSummary() {
        Map<Long, Long> projectCounts = new HashMap<>();
        if (projectRepository != null) {
            for (Object[] row : projectRepository.countProjectsGroupedByUser()) {
                if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                    projectCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
                }
            }
        }

        Map<Long, Long> taskCounts = new HashMap<>();
        if (agentTaskRepository != null) {
            for (Object[] row : agentTaskRepository.countTasksGroupedByUser()) {
                if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                    taskCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
                }
            }
        }

        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(u -> UserSummaryResponse.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .email(u.getEmail())
                        .role(u.getRole())
                        .isLocked(Boolean.TRUE.equals(u.getIsLocked()))
                        .createdAt(u.getCreatedAt())
                        .projectCount(projectCounts.getOrDefault(u.getId(), 0L))
                        .taskCount(taskCounts.getOrDefault(u.getId(), 0L))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Cập nhật trạng thái Khóa / Mở khóa tài khoản người dùng.
     */
    @Transactional
    public UserSummaryResponse updateUserStatus(Long userId, boolean locked, CustomUserDetails adminUser) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với id: " + userId));

        if (adminUser != null && adminUser.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Không thể tự khóa tài khoản quản trị của chính mình");
        }

        target.setIsLocked(locked);
        User saved = userRepository.save(target);

        String action = locked ? "LOCK_USER" : "UNLOCK_USER";
        if (auditLogService != null) {
            auditLogService.log(adminUser != null ? adminUser.getUsername() : "ADMIN", action,
                    (locked ? "Đã khóa" : "Đã mở khóa") + " tài khoản: " + target.getUsername() + " (id=" + userId + ")");
        }

        return toUserSummaryResponse(saved);
    }

    /**
     * Đảo ngược trạng thái khóa tài khoản người dùng (Toggle Lock).
     */
    @Transactional
    public UserSummaryResponse toggleUserLock(Long userId, CustomUserDetails adminUser) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với id: " + userId));

        boolean newLockedState = !Boolean.TRUE.equals(target.getIsLocked());
        return updateUserStatus(userId, newLockedState, adminUser);
    }

    /**
     * Cập nhật vai trò tài khoản (ROLE_USER <-> ROLE_ADMIN).
     */
    @Transactional
    public UserSummaryResponse updateUserRole(Long userId, Role role, CustomUserDetails adminUser) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với id: " + userId));

        if (adminUser != null && adminUser.getUser().getId().equals(userId) && role != Role.ADMIN) {
            throw new IllegalArgumentException("Không thể tự hạ quyền của chính tài khoản quản trị hiện tại");
        }

        Role oldRole = target.getRole();
        target.setRole(role);
        User saved = userRepository.save(target);

        if (auditLogService != null) {
            auditLogService.log(adminUser != null ? adminUser.getUsername() : "ADMIN", "UPDATE_ROLE",
                    "Đổi vai trò người dùng " + target.getUsername() + " từ " + oldRole + " sang " + role);
        }

        return toUserSummaryResponse(saved);
    }

    /**
     * Xóa vĩnh viễn tài khoản người dùng kèm dọn dẹp toàn bộ dữ liệu phụ thuộc (Cascade Delete).
     */
    @Transactional
    public Map<String, String> deleteUserCascade(Long userId, CustomUserDetails adminUser) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với id: " + userId));

        if (adminUser != null && adminUser.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Không thể xóa chính tài khoản quản trị viên hiện tại");
        }

        if (agentTaskRepository != null) {
            List<AgentTask> userTasks = agentTaskRepository.findByUserId(userId);
            if (!userTasks.isEmpty()) {
                agentTaskRepository.deleteAll(userTasks);
            }
        }

        if (chatConversationRepository != null && chatMessageRepository != null) {
            List<ChatConversation> userConvs = chatConversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
            for (ChatConversation conv : userConvs) {
                chatMessageRepository.deleteByConversationId(conv.getId());
            }
            if (!userConvs.isEmpty()) {
                chatConversationRepository.deleteAll(userConvs);
            }
        }

        if (userPreferencesRepository != null) {
            userPreferencesRepository.deleteByUserId(userId);
        }

        if (projectRepository != null) {
            List<Project> userProjects = projectRepository.findByUserId(userId);
            for (Project p : userProjects) {
                if (agentTaskRepository != null) {
                    agentTaskRepository.deleteByProjectId(p.getId());
                }
            }
            if (!userProjects.isEmpty()) {
                projectRepository.deleteAll(userProjects);
            }
        }

        userRepository.delete(target);

        if (auditLogService != null) {
            auditLogService.log(adminUser != null ? adminUser.getUsername() : "ADMIN", "DELETE_USER",
                    "Đã xóa tài khoản người dùng " + target.getUsername() + " và toàn bộ dữ liệu liên quan");
        }

        return Map.of("message", "Đã xóa tài khoản " + target.getUsername() + " thành công");
    }

    public UserSummaryResponse toUserSummaryResponse(User u) {
        return UserSummaryResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .role(u.getRole())
                .isLocked(Boolean.TRUE.equals(u.getIsLocked()))
                .createdAt(u.getCreatedAt())
                .projectCount(0L)
                .taskCount(0L)
                .build();
    }
}
