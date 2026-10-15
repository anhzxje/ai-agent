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
     * Khởi tạo tác vụ AI mới và kích hoạt worker chạy ngầm.
     */
    @PostMapping
    public ResponseEntity<AgentTaskResponse> createTask(
            @Valid @RequestBody AgentTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(agentTaskService.createTask(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgentTaskResponse> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(agentTaskService.getTask(id));
    }

    /**
     * Xem kết quả và lịch sử: CHỈ THẤY TASK CỦA CHÍNH MÌNH (lọc theo userId ở backend).
     */
    @GetMapping
    public ResponseEntity<List<AgentTaskResponse>> getMyTasks() {
        return ResponseEntity.ok(agentTaskService.getMyTasks());
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<AgentTaskResponse>> getTasksByProject(
            @PathVariable Long projectId) {
        return ResponseEntity.ok(agentTaskService.getTasksByProject(projectId));
    }

    @PatchMapping("/{id}/pin")
    public ResponseEntity<AgentTaskResponse> togglePin(@PathVariable Long id) {
        return ResponseEntity.ok(agentTaskService.togglePin(id));
    }

    /**
     * Hủy task của chính mình khi đang ở trạng thái PENDING hoặc PROCESSING.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<AgentTaskResponse> cancelTask(@PathVariable Long id) {
        return ResponseEntity.ok(agentTaskService.cancelTask(id));
    }

    /**
     * Xóa 1 task của chính mình.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteTask(@PathVariable Long id) {
        agentTaskService.deleteTask(id);
        return ResponseEntity.ok(Map.of("message", "Đã xóa tác vụ #" + id + " thành công"));
    }

    /**
     * Xóa toàn bộ lịch sử task của chính mình.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteAllMyTasks() {
        agentTaskService.deleteAllMyTasks();
        return ResponseEntity.ok(Map.of("message", "Đã xóa toàn bộ lịch sử tác vụ thành công"));
    }

    /**
     * Chạy lại task cũ (retry).
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<AgentTaskResponse> retryTask(@PathVariable Long id) {
        return ResponseEntity.ok(agentTaskService.retryTask(id));
    }

}
