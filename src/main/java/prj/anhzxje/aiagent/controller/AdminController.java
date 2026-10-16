package prj.anhzxje.aiagent.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import prj.anhzxje.aiagent.dto.admin.*;
import prj.anhzxje.aiagent.dto.project.ProjectResponse;
import prj.anhzxje.aiagent.dto.task.AgentTaskResponse;
import prj.anhzxje.aiagent.entity.AgentTask;
import prj.anhzxje.aiagent.entity.ChatConversation;
import prj.anhzxje.aiagent.entity.Project;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.enums.JobStatus;
import prj.anhzxje.aiagent.enums.Role;
import prj.anhzxje.aiagent.enums.TaskType;
import prj.anhzxje.aiagent.exception.ResourceNotFoundException;
import prj.anhzxje.aiagent.repository.AgentTaskRepository;
import prj.anhzxje.aiagent.repository.ChatConversationRepository;
import prj.anhzxje.aiagent.repository.ChatMessageRepository;
import prj.anhzxje.aiagent.repository.ProjectRepository;
import prj.anhzxje.aiagent.repository.UserPreferencesRepository;
import prj.anhzxje.aiagent.repository.UserRepository;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.service.AuditLogService;
import prj.anhzxje.aiagent.service.SystemConfigService;
import prj.anhzxje.aiagent.service.ToolManagerService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * ADMIN CONTROLLER - TRUNG TÂM ĐIỀU HÀNH & QUẢN TRỊ HỆ THỐNG (ROLE ADMIN)
 * ============================================================================
 * Mục đích:
 * 1. Thống kê toàn diện (Dashboard Stats): số lượng user, admin, dự án, tác vụ AI theo loại và trạng thái.
 * 2. Quản lý người dùng: Xem danh sách, Khóa/Mở khóa tài khoản, Đổi quyền (USER/ADMIN), Xóa người dùng.
 * 3. Bật/Tắt công cụ (Agent Tools): Danh sách công cụ cho phép Agent gọi, bật/tắt động trong CSDL.
 * 4. Cấu hình Model & Gemini API Key: Thay đổi model hoạt động, cập nhật key an toàn với cơ chế che giấu (masking).
 * 5. Giám sát toàn hệ thống: Xem lịch sử mọi tác vụ chạy trên máy chủ kèm thông báo lỗi (Error Log).
 * 6. Nhật ký kiểm toán (Audit Log): Chỉ cho phép Admin truy cập để thanh tra các hành vi nhạy cảm.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final AgentTaskRepository agentTaskRepository;
    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final AuditLogService auditLogService;
    private final ToolManagerService toolManagerService;
    private final SystemConfigService systemConfigService;

    /**
     * Lấy dữ liệu thống kê tổng quan toàn hệ thống cho Dashboard Admin.
     * Sử dụng các câu lệnh count() trực tiếp từ CSDL để tránh tràn RAM (OutOfMemory) khi hệ thống có nhiều tác vụ.
     *
     * @return ResponseEntity chứa AdminStatsResponse với đầy đủ các chỉ số thống kê
     */
    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getStats() {
        long totalUsers = userRepository.count();
        long totalProjects = projectRepository.count();
        long totalTasks = agentTaskRepository.count();

        long totalAdmins = userRepository.countByRole(Role.ADMIN);
        long totalRegularUsers = Math.max(0, totalUsers - totalAdmins);

        long completedTasks = agentTaskRepository.countByStatus(JobStatus.COMPLETED);
        long pendingOrProcessingTasks = agentTaskRepository.countByStatusIn(List.of(JobStatus.PENDING, JobStatus.PROCESSING));
        long failedTasks = agentTaskRepository.countByStatus(JobStatus.ERROR);

        long reviewTasksCount = agentTaskRepository.countByType(TaskType.CODE_REVIEW);
        long fixTasksCount = agentTaskRepository.countByType(TaskType.CODE_FIX);
        long testGenTasksCount = agentTaskRepository.countByType(TaskType.TEST_GENERATION);

        return ResponseEntity.ok(AdminStatsResponse.builder()
                .totalUsers(totalUsers)
                .totalAdmins(totalAdmins)
                .totalRegularUsers(totalRegularUsers)
                .totalProjects(totalProjects)
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .pendingOrProcessingTasks(pendingOrProcessingTasks)
                .failedTasks(failedTasks)
                .reviewTasksCount(reviewTasksCount)
                .fixTasksCount(fixTasksCount)
                .testGenTasksCount(testGenTasksCount)
                .build());
    }

    // ────────────────────────────────────────────────
    // 1. Quản lý Người dùng: Xem, Khóa/Mở khóa, Đổi quyền, Xóa
    // ────────────────────────────────────────────────

    /**
     * Lấy danh sách toàn bộ người dùng trong hệ thống (sắp xếp giảm dần theo ngày tạo).
     * Bao gồm thông tin cơ bản, trạng thái khóa, số lượng dự án và tác vụ đã tạo.
     *
     * @return ResponseEntity chứa danh sách UserSummaryResponse
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryResponse>> getAllUsers() {
        List<UserSummaryResponse> users = userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(u -> UserSummaryResponse.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .email(u.getEmail())
                        .role(u.getRole())
                        .isLocked(Boolean.TRUE.equals(u.getIsLocked()))
                        .createdAt(u.getCreatedAt())
                        .projectCount(projectRepository.countByUserId(u.getId()))
                        .taskCount(agentTaskRepository.countByUserId(u.getId()))
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    /**
     * Cập nhật trạng thái Khóa / Mở khóa tài khoản:
     * - Ngăn chặn Admin tự khóa tài khoản của chính mình (chống lỗi lockout).
     * - Khi bị khóa (locked = true), CustomUserDetails.isAccountNonLocked() sẽ trả về false,
     *   khiến JwtAuthenticationFilter và Spring Security lập tức từ chối mọi yêu cầu tiếp theo.
     */
    @PutMapping("/users/{userId}/status")
    public ResponseEntity<UserSummaryResponse> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam boolean locked,
            @AuthenticationPrincipal CustomUserDetails adminUser) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với id: " + userId));

        // Ràng buộc bảo mật: Quản trị viên không được phép tự khóa chính mình
        if (adminUser != null && adminUser.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Không thể tự khóa tài khoản quản trị của chính mình");
        }

        target.setIsLocked(locked);
        User saved = userRepository.save(target);

        // Ghi nhật ký kiểm toán hành vi quản trị
        String action = locked ? "LOCK_USER" : "UNLOCK_USER";
        auditLogService.log(adminUser != null ? adminUser.getUsername() : "ADMIN", action,
                (locked ? "Đã khóa" : "Đã mở khóa") + " tài khoản: " + target.getUsername() + " (id=" + userId + ")");

        return ResponseEntity.ok(toUserSummaryResponse(saved));
    }

    /**
     * Đảo ngược trạng thái khóa của tài khoản (Toggle Lock).
     */
    @PutMapping("/users/{userId}/lock")
    public ResponseEntity<UserSummaryResponse> toggleUserLock(
            @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails adminUser) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với id: " + userId));

        boolean newLockedState = !Boolean.TRUE.equals(target.getIsLocked());
        return updateUserStatus(userId, newLockedState, adminUser);
    }

    /**
     * Nâng quyền hoặc giáng quyền tài khoản (ROLE USER <-> ROLE ADMIN):
     * - Ngăn Admin tự hạ quyền của chính mình để tránh làm mất người quản trị hệ thống.
     */
    @PutMapping("/users/{userId}/role")
    public ResponseEntity<UserSummaryResponse> updateUserRole(
            @PathVariable Long userId,
            @RequestParam Role role,
            @AuthenticationPrincipal CustomUserDetails adminUser) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với id: " + userId));

        // Ràng buộc bảo vệ: Không cho phép Admin tự giáng quyền của chính mình thành USER
        if (adminUser != null && adminUser.getUser().getId().equals(userId) && role != Role.ADMIN) {
            throw new IllegalArgumentException("Không thể tự hạ quyền của chính tài khoản quản trị hiện tại");
        }

        Role oldRole = target.getRole();
        target.setRole(role);
        User saved = userRepository.save(target);

        auditLogService.log(adminUser != null ? adminUser.getUsername() : "ADMIN", "UPDATE_ROLE",
                "Đổi vai trò người dùng " + target.getUsername() + " từ " + oldRole + " sang " + role);

        return ResponseEntity.ok(toUserSummaryResponse(saved));
    }

    /**
     * Xóa vĩnh viễn tài khoản người dùng và dọn dẹp toàn bộ dữ liệu phụ thuộc (Cascade Delete):
     * 1. Xóa toàn bộ các tác vụ AI (AgentTask) thuộc người dùng này.
     * 2. Xóa toàn bộ các dự án (Project) thuộc người dùng này.
     * 3. Xóa bản ghi người dùng khỏi bảng users.
     * 4. Ghi vết vào bảng nhật ký kiểm toán (AuditLog).
     */
    @DeleteMapping("/users/{userId}")
    @Transactional
    public ResponseEntity<?> deleteUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails adminUser) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với id: " + userId));

        // Ràng buộc bảo vệ: Admin không thể xóa chính tài khoản mình đang đăng nhập
        if (adminUser != null && adminUser.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Không thể xóa chính tài khoản quản trị viên hiện tại");
        }

        // Bước 1: Xóa các tác vụ AI liên quan trước để tránh lỗi khóa ngoại (Foreign Key Constraint)
        List<AgentTask> userTasks = agentTaskRepository.findByUserId(userId);
        if (!userTasks.isEmpty()) {
            agentTaskRepository.deleteAll(userTasks);
        }

        // Bước 2: Xóa toàn bộ tin nhắn và các cuộc hội thoại chat của user
        List<ChatConversation> userConvs = chatConversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        for (ChatConversation conv : userConvs) {
            chatMessageRepository.deleteByConversationId(conv.getId());
        }
        if (!userConvs.isEmpty()) {
            chatConversationRepository.deleteAll(userConvs);
        }

        // Bước 3: Xóa tùy chọn cá nhân của user
        userPreferencesRepository.deleteByUserId(userId);

        // Bước 4: Xóa các dự án mã nguồn liên quan (và task con nếu có)
        List<Project> userProjects = projectRepository.findByUserId(userId);
        for (Project p : userProjects) {
            agentTaskRepository.deleteByProjectId(p.getId());
        }
        if (!userProjects.isEmpty()) {
            projectRepository.deleteAll(userProjects);
        }

        // Bước 5: Xóa bản ghi người dùng
        userRepository.delete(target);

        // Bước 6: Ghi nhật ký kiểm toán hành vi xóa tài khoản
        auditLogService.log(adminUser != null ? adminUser.getUsername() : "ADMIN", "DELETE_USER",
                "Đã xóa tài khoản người dùng " + target.getUsername() + " và toàn bộ dữ liệu liên quan");

        return ResponseEntity.ok(Map.of(
                "message", "Đã xóa tài khoản " + target.getUsername() + " thành công"
        ));
    }

    // ────────────────────────────────────────────────
    // 2. Bật / Tắt Tools của Agent
    // ────────────────────────────────────────────────

    /**
     * Lấy danh sách toàn bộ các công cụ (Agent Tools) có trong hệ thống và trạng thái kích hoạt hiện tại.
     *
     * @return ResponseEntity chứa danh sách AgentToolDto
     */
    @GetMapping("/tools")
    public ResponseEntity<List<AgentToolDto>> getAllTools() {
        return ResponseEntity.ok(toolManagerService.getAllTools());
    }

    /**
     * Cập nhật trạng thái bật/tắt (enabled) cho một công cụ cụ thể của Agent.
     *
     * @param toolName  tên định danh công cụ (VD: file_reader, ast_parser)
     * @param enabled   trạng thái bật (true) hoặc tắt (false)
     * @param adminUser thông tin tài khoản admin thực hiện thao tác
     * @return ResponseEntity chứa thông tin AgentToolDto sau khi cập nhật
     */
    @PutMapping("/tools/{toolName}")
    public ResponseEntity<AgentToolDto> updateToolStatus(
            @PathVariable String toolName,
            @RequestParam boolean enabled,
            @AuthenticationPrincipal CustomUserDetails adminUser) {
        String admin = adminUser != null ? adminUser.getUsername() : "ADMIN";
        return ResponseEntity.ok(toolManagerService.setToolStatus(toolName, enabled, admin));
    }

    /**
     * Đảo ngược trạng thái bật/tắt của một công cụ Agent (Toggle Tool).
     *
     * @param toolName  tên định danh công cụ
     * @param adminUser thông tin tài khoản admin thực hiện thao tác
     * @return ResponseEntity chứa thông tin AgentToolDto sau khi toggle
     */
    @PutMapping("/tools/{toolName}/toggle")
    public ResponseEntity<AgentToolDto> toggleTool(
            @PathVariable String toolName,
            @AuthenticationPrincipal CustomUserDetails adminUser) {
        String admin = adminUser != null ? adminUser.getUsername() : "ADMIN";
        return ResponseEntity.ok(toolManagerService.toggleTool(toolName, admin));
    }

    // ────────────────────────────────────────────────
    // 3. Cấu hình Model và API Key (Gemini)
    // ────────────────────────────────────────────────

    /**
     * Lấy cấu hình hệ thống hiện tại (Model AI và API Key đã được che giấu/masking để bảo mật).
     *
     * @return ResponseEntity chứa SystemSettingsResponse
     */
    @GetMapping("/settings")
    public ResponseEntity<SystemSettingsResponse> getSettings() {
        return ResponseEntity.ok(systemConfigService.getSettingsForAdmin());
    }

    /**
     * Cập nhật cấu hình hệ thống: Tên model AI và/hoặc Gemini API Key.
     * Thao tác được ghi lại vào bảng AuditLog.
     *
     * @param request   yêu cầu cập nhật cấu hình chứa model hoặc apiKey mới
     * @param adminUser thông tin admin thực hiện thao tác
     * @return ResponseEntity chứa SystemSettingsResponse mới nhất
     */
    @PutMapping("/settings")
    public ResponseEntity<SystemSettingsResponse> updateSettings(
            @RequestBody SystemSettingsUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails adminUser) {
        String admin = adminUser != null ? adminUser.getUsername() : "ADMIN";
        systemConfigService.updateSettings(request, admin);
        return ResponseEntity.ok(systemConfigService.getSettingsForAdmin());
    }

    // ────────────────────────────────────────────────
    // 4. Xem Lịch sử Task và Log Lỗi toàn hệ thống
    // ────────────────────────────────────────────────

    /**
     * Lấy danh sách tác vụ AI toàn hệ thống với khả năng lọc theo trạng thái (status) hoặc theo người dùng (userId).
     *
     * @param status lọc theo JobStatus (PENDING, PROCESSING, COMPLETED, ERROR) nếu có
     * @param userId lọc theo mã người dùng nếu có
     * @return ResponseEntity chứa danh sách AdminTaskResponse
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<AdminTaskResponse>> getAllTasks(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) Long userId) {

        List<AgentTask> tasks;
        if (userId != null) {
            tasks = agentTaskRepository.findByUserIdOrderByCreatedAtDesc(userId);
        } else if (status != null) {
            tasks = agentTaskRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            tasks = agentTaskRepository.findAllByOrderByCreatedAtDesc();
        }

        List<AdminTaskResponse> res = tasks.stream()
                .map(this::toAdminTaskResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(res);
    }

    /**
     * Lấy danh sách các tác vụ AI gặp lỗi (errorMessage != null) để admin kiểm tra và khắc phục.
     *
     * @return ResponseEntity chứa danh sách AdminTaskResponse của các task bị lỗi
     */
    @GetMapping("/tasks/errors")
    public ResponseEntity<List<AdminTaskResponse>> getErrorTasks() {
        List<AdminTaskResponse> errors = agentTaskRepository.findByErrorMessageIsNotNullOrderByCreatedAtDesc().stream()
                .map(this::toAdminTaskResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(errors);
    }

    // ────────────────────────────────────────────────
    // 5. Bảng Audit Log (Chỉ cho Admin đọc)
    // ────────────────────────────────────────────────

    /**
     * Lấy danh sách 100 nhật ký kiểm toán (Audit Logs) gần nhất để phục vụ công tác giám sát, bảo mật.
     *
     * @return ResponseEntity chứa danh sách AuditLogResponse
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs() {
        return ResponseEntity.ok(auditLogService.getRecentLogs());
    }

    // ────────────────────────────────────────────────
    // Chi tiết Tác vụ & Dự án của 1 user cụ thể
    // ────────────────────────────────────────────────

    /**
     * Lấy danh sách toàn bộ tác vụ AI của một người dùng cụ thể.
     *
     * @param userId mã định danh người dùng cần tra cứu
     * @return ResponseEntity chứa danh sách AgentTaskResponse
     */
    @GetMapping("/users/{userId}/tasks")
    public ResponseEntity<List<AgentTaskResponse>> getUserTasksForAdmin(@PathVariable Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));

        List<AgentTaskResponse> tasks = agentTaskRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toTaskResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tasks);
    }

    /**
     * Lấy danh sách toàn bộ dự án thuộc quyền sở hữu của một người dùng cụ thể.
     *
     * @param userId mã định danh người dùng cần tra cứu
     * @return ResponseEntity chứa danh sách ProjectResponse
     */
    @GetMapping("/users/{userId}/projects")
    public ResponseEntity<List<ProjectResponse>> getUserProjectsForAdmin(@PathVariable Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));

        List<ProjectResponse> projects = projectRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(p -> ProjectResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .description(p.getDescription())
                        .path(p.getPath())
                        .language(p.getLanguage())
                        .ownerUsername(p.getUser() != null ? p.getUser().getUsername() : null)
                        .createdAt(p.getCreatedAt())
                        .updatedAt(p.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(projects);
    }

    /**
     * Hàm helper chuyển đổi entity User sang DTO UserSummaryResponse kèm số lượng project và task.
     *
     * @param u entity User nguồn
     * @return UserSummaryResponse DTO
     */
    private UserSummaryResponse toUserSummaryResponse(User u) {
        return UserSummaryResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .role(u.getRole())
                .isLocked(Boolean.TRUE.equals(u.getIsLocked()))
                .createdAt(u.getCreatedAt())
                .projectCount(projectRepository.countByUserId(u.getId()))
                .taskCount(agentTaskRepository.countByUserId(u.getId()))
                .build();
    }

    /**
     * Hàm helper chuyển đổi entity AgentTask sang DTO AdminTaskResponse với thông tin người dùng và dự án.
     *
     * @param task entity AgentTask nguồn
     * @return AdminTaskResponse DTO
     */
    private AdminTaskResponse toAdminTaskResponse(AgentTask task) {
        return AdminTaskResponse.builder()
                .id(task.getId())
                .userId(task.getUser() != null ? task.getUser().getId() : null)
                .username(task.getUser() != null ? task.getUser().getUsername() : null)
                .userEmail(task.getUser() != null ? task.getUser().getEmail() : null)
                .projectId(task.getProject() != null ? task.getProject().getId() : null)
                .projectName(task.getProject() != null ? task.getProject().getName() : null)
                .type(task.getType())
                .status(task.getStatus())
                .input(task.getInput())
                .output(task.getOutput())
                .errorMessage(task.getErrorMessage())
                .createdAt(task.getCreatedAt())
                .completedAt(task.getCompletedAt())
                .isPinned(Boolean.TRUE.equals(task.getIsPinned()))
                .build();
    }

    /**
     * Hàm helper chuyển đổi entity AgentTask sang DTO AgentTaskResponse.
     *
     * @param task entity AgentTask nguồn
     * @return AgentTaskResponse DTO
     */
    private AgentTaskResponse toTaskResponse(AgentTask task) {
        return AgentTaskResponse.builder()
                .id(task.getId())
                .projectId(task.getProject() != null ? task.getProject().getId() : null)
                .projectName(task.getProject() != null ? task.getProject().getName() : null)
                .type(task.getType())
                .status(task.getStatus())
                .input(task.getInput())
                .output(task.getOutput())
                .errorMessage(task.getErrorMessage())
                .createdAt(task.getCreatedAt())
                .completedAt(task.getCompletedAt())
                .isPinned(Boolean.TRUE.equals(task.getIsPinned()))
                .build();
    }
}
