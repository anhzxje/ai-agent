package prj.anhzxje.aiagent.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import prj.anhzxje.aiagent.dto.task.AgentTaskRequest;
import prj.anhzxje.aiagent.dto.task.AgentTaskResponse;
import prj.anhzxje.aiagent.service.AgentTaskService;

import java.util.List;
import java.util.Map;

/**
 * Controller Quản lý Tác vụ AI (AgentTaskController):
 * Cung cấp RESTful API cho người dùng:
 * 1. POST /api/tasks: Khởi tạo tác vụ Code Review / Fix / Test mới (chạy nền bất đồng bộ).
 * 2. GET /api/tasks: Lấy danh sách tác vụ của người dùng đang đăng nhập.
 * 3. GET /api/tasks/{id}: Xem chi tiết trạng thái, log và kết quả JSON của tác vụ.
 * 4. GET /api/tasks/project/{projectId}: Lọc tác vụ theo dự án.
 * 5. PATCH /api/tasks/{id}/pin: Ghim hoặc bỏ ghim tác vụ quan trọng lên đầu.
 * 6. POST /api/tasks/{id}/cancel: Hủy tác vụ đang chạy.
 * 7. POST /api/tasks/{id}/retry: Thực thi lại tác vụ thất bại.
 * 8. DELETE /api/tasks/{id} & DELETE /api/tasks: Xóa một hoặc toàn bộ lịch sử tác vụ.
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class AgentTaskController {

    private final AgentTaskService agentTaskService;

    /**
     * Khởi tạo tác vụ AI mới (Review, Fix, Generate Test) và kích hoạt worker chạy ngầm trên ThreadPool riêng.
     *
     * @param request thông tin cấu hình tác vụ gồm projectId, taskType, input JSON, model và danh sách tools
     * @return ResponseEntity chứa thông tin AgentTaskResponse với status ban đầu là PENDING (HTTP 201 Created)
     */
    @PostMapping
    public ResponseEntity<AgentTaskResponse> createTask(
            @Valid @RequestBody AgentTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(agentTaskService.createTask(request));
    }

    /**
     * Lấy thông tin chi tiết của một tác vụ theo ID (trạng thái, kết quả output JSON, nhật ký lỗi).
     * Yêu cầu bảo mật: Chỉ chủ sở hữu tác vụ mới được phép truy xuất.
     *
     * @param id mã định danh tác vụ
     * @return ResponseEntity chứa thông tin chi tiết AgentTaskResponse
     */
    @GetMapping("/{id}")
    public ResponseEntity<AgentTaskResponse> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(agentTaskService.getTask(id));
    }

    /**
     * Lấy danh sách toàn bộ tác vụ của người dùng đang đăng nhập (sắp xếp giảm dần theo thời gian tạo).
     * Đảm bảo tính cô lập dữ liệu (Multi-tenant): Chỉ trả về các task thuộc sở hữu của chính user hiện tại.
     *
     * @return ResponseEntity chứa danh sách AgentTaskResponse của user
     */
    @GetMapping
    public ResponseEntity<List<AgentTaskResponse>> getMyTasks() {
        return ResponseEntity.ok(agentTaskService.getMyTasks());
    }

    /**
     * Lấy danh sách các tác vụ thuộc một dự án cụ thể của người dùng đang đăng nhập.
     *
     * @param projectId mã định danh dự án cần lọc tác vụ
     * @return ResponseEntity chứa danh sách AgentTaskResponse thuộc dự án
     */
    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<AgentTaskResponse>> getTasksByProject(
            @PathVariable Long projectId) {
        return ResponseEntity.ok(agentTaskService.getTasksByProject(projectId));
    }

    /**
     * Đảo ngược trạng thái Ghim / Bỏ ghim (Pinned) của tác vụ lên đầu giao diện.
     *
     * @param id mã định danh tác vụ cần ghim/bỏ ghim
     * @return ResponseEntity chứa AgentTaskResponse với trạng thái isPinned mới
     */
    @PatchMapping("/{id}/pin")
    public ResponseEntity<AgentTaskResponse> togglePin(@PathVariable Long id) {
        return ResponseEntity.ok(agentTaskService.togglePin(id));
    }

    /**
     * Hủy bỏ tác vụ AI của chính mình khi đang ở trạng thái PENDING hoặc PROCESSING.
     *
     * @param id mã định danh tác vụ cần hủy
     * @return ResponseEntity chứa AgentTaskResponse với trạng thái CANCELLED
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<AgentTaskResponse> cancelTask(@PathVariable Long id) {
        return ResponseEntity.ok(agentTaskService.cancelTask(id));
    }

    /**
     * Xóa vĩnh viễn một tác vụ của chính mình khỏi cơ sở dữ liệu.
     *
     * @param id mã định danh tác vụ cần xóa
     * @return ResponseEntity chứa thông điệp xác nhận đã xóa thành công
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteTask(@PathVariable Long id) {
        agentTaskService.deleteTask(id);
        return ResponseEntity.ok(Map.of("message", "Đã xóa tác vụ #" + id + " thành công"));
    }

    /**
     * Xóa toàn bộ lịch sử tác vụ thuộc sở hữu của người dùng đang đăng nhập.
     *
     * @return ResponseEntity chứa thông điệp xác nhận đã xóa toàn bộ lịch sử
     */
    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteAllMyTasks() {
        agentTaskService.deleteAllMyTasks();
        return ResponseEntity.ok(Map.of("message", "Đã xóa toàn bộ lịch sử tác vụ thành công"));
    }

    /**
     * Thực thi lại một tác vụ cũ (Retry) với cấu hình và mã nguồn tương tự.
     *
     * @param id mã định danh tác vụ cần chạy lại
     * @return ResponseEntity chứa thông tin AgentTaskResponse được khởi động lại (status PENDING)
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<AgentTaskResponse> retryTask(@PathVariable Long id) {
        return ResponseEntity.ok(agentTaskService.retryTask(id));
    }

}
