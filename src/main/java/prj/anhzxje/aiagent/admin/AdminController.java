package prj.anhzxje.aiagent.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import prj.anhzxje.aiagent.infrastructure.ai.AgentToolDto;
import prj.anhzxje.aiagent.infrastructure.ai.SystemSettingsResponse;
import prj.anhzxje.aiagent.infrastructure.ai.SystemSettingsUpdateRequest;
import prj.anhzxje.aiagent.infrastructure.audit.AuditLogResponse;
import prj.anhzxje.aiagent.users.UserSummaryResponse;
import prj.anhzxje.aiagent.chat.ChatConversationResponse;
import prj.anhzxje.aiagent.chat.ChatMessageResponse;
import prj.anhzxje.aiagent.projects.ProjectResponse;
import prj.anhzxje.aiagent.tasks.AgentTaskResponse;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.AgentTask;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.ChatConversation;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.ChatMessage;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.Project;
import prj.anhzxje.aiagent.shared.enums.JobStatus;
import prj.anhzxje.aiagent.shared.enums.Role;
import org.springframework.beans.factory.annotation.Autowired;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.infrastructure.audit.AuditLogService;
import prj.anhzxje.aiagent.infrastructure.ai.SystemConfigService;
import prj.anhzxje.aiagent.infrastructure.tools.ToolManagerService;
import prj.anhzxje.aiagent.users.UserService;

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
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AuditLogService auditLogService;
    private final ToolManagerService toolManagerService;
    private final SystemConfigService systemConfigService;
    private final UserService userService;

    /**
     * Lấy dữ liệu thống kê tổng quan toàn hệ thống cho Dashboard Admin.
     * Sử dụng các câu lệnh count() trực tiếp từ CSDL để tránh tràn RAM (OutOfMemory) khi hệ thống có nhiều tác vụ.
     *
     * @return ResponseEntity chứa AdminStatsResponse với đầy đủ các chỉ số thống kê
     */
    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getStats() {
        return ResponseEntity.ok(adminService.getStats());
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
        return ResponseEntity.ok(userService.getAllUsersSummary());
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
        return ResponseEntity.ok(userService.updateUserStatus(userId, locked, adminUser));
    }

    /**
     * Đảo ngược trạng thái khóa của tài khoản (Toggle Lock).
     */
    @PutMapping("/users/{userId}/lock")
    public ResponseEntity<UserSummaryResponse> toggleUserLock(
            @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails adminUser) {
        return ResponseEntity.ok(userService.toggleUserLock(userId, adminUser));
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
        return ResponseEntity.ok(userService.updateUserRole(userId, role, adminUser));
    }

    /**
     * Xóa vĩnh viễn tài khoản người dùng và dọn dẹp toàn bộ dữ liệu phụ thuộc (Cascade Delete):
     * 1. Xóa toàn bộ các tác vụ AI (AgentTask) thuộc người dùng này.
     * 2. Xóa toàn bộ các dự án (Project) thuộc người dùng này.
     * 3. Xóa bản ghi người dùng khỏi bảng users.
     * 4. Ghi vết vào bảng nhật ký kiểm toán (AuditLog).
     */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails adminUser) {
        return ResponseEntity.ok(userService.deleteUserCascade(userId, adminUser));
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
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(adminService.getAllTasks(status, userId, page, size));
    }

    /**
     * Lấy danh sách các tác vụ AI gặp lỗi (errorMessage != null) để admin kiểm tra và khắc phục.
     *
     * @return ResponseEntity chứa danh sách AdminTaskResponse của các task bị lỗi
     */
    @GetMapping("/tasks/errors")
    public ResponseEntity<List<AdminTaskResponse>> getErrorTasks() {
        return ResponseEntity.ok(adminService.getErrorTasks());
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
        return ResponseEntity.ok(adminService.getUserTasksForAdmin(userId));
    }

    /**
     * Lấy danh sách toàn bộ dự án thuộc quyền sở hữu của một người dùng cụ thể.
     *
     * @param userId mã định danh người dùng cần tra cứu
     * @return ResponseEntity chứa danh sách ProjectResponse
     */
    @GetMapping("/users/{userId}/projects")
    public ResponseEntity<List<ProjectResponse>> getUserProjectsForAdmin(@PathVariable Long userId) {
        return ResponseEntity.ok(adminService.getUserProjectsForAdmin(userId));
    }

    // ────────────────────────────────────────────────
    // 7. Kiểm duyệt & Quản trị Chat / Hội thoại
    // ────────────────────────────────────────────────

    /**
     * Lấy danh sách toàn bộ cuộc hội thoại của một người dùng cụ thể phục vụ mục đích kiểm duyệt.
     *
     * @param userId mã định danh người dùng cần tra cứu
     * @return danh sách ChatConversationResponse
     */
    @GetMapping("/users/{userId}/conversations")
    public ResponseEntity<List<ChatConversationResponse>> getUserConversationsForAdmin(@PathVariable Long userId) {
        return ResponseEntity.ok(adminService.getUserConversationsForAdmin(userId));
    }

    /**
     * Lấy danh sách toàn bộ tin nhắn trong một cuộc hội thoại cụ thể để thanh tra nội dung.
     *
     * @param conversationId mã định danh cuộc hội thoại
     * @return danh sách ChatMessageResponse
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getConversationMessagesForAdmin(@PathVariable Long conversationId) {
        return ResponseEntity.ok(adminService.getConversationMessagesForAdmin(conversationId));
    }

    /**
     * Quản trị viên xóa toàn bộ cuộc hội thoại vi phạm và toàn bộ tin nhắn bên trong.
     *
     * @param conversationId mã định danh cuộc hội thoại cần xóa
     * @param adminUser thông tin tài khoản admin thực hiện thao tác
     * @return thông báo xóa thành công
     */
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<?> deleteConversationForAdmin(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal CustomUserDetails adminUser) {
        return ResponseEntity.ok(adminService.deleteConversationForAdmin(conversationId, adminUser));
    }

    /**
     * Quản trị viên xóa một tin nhắn vi phạm riêng lẻ.
     *
     * @param messageId mã định danh tin nhắn cần xóa
     * @param adminUser thông tin tài khoản admin thực hiện thao tác
     * @return thông báo xóa thành công
     */
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<?> deleteMessageForAdmin(
            @PathVariable Long messageId,
            @AuthenticationPrincipal CustomUserDetails adminUser) {
        return ResponseEntity.ok(adminService.deleteMessageForAdmin(messageId, adminUser));
    }

    /**
     * Hàm helper chuyển đổi entity ChatConversation sang DTO ChatConversationResponse.
     */

    /**
     * Hàm helper chuyển đổi entity ChatMessage sang DTO ChatMessageResponse.
     */



    /**
     * Hàm helper chuyển đổi entity AgentTask sang DTO AdminTaskResponse với thông tin người dùng và dự án.
     *
     * @param task entity AgentTask nguồn
     * @return AdminTaskResponse DTO
     */


}
