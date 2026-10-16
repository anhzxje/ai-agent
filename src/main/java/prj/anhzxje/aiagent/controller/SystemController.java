package prj.anhzxje.aiagent.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import prj.anhzxje.aiagent.dto.admin.AgentToolDto;
import prj.anhzxje.aiagent.service.SystemConfigService;
import prj.anhzxje.aiagent.service.ToolManagerService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller cung cấp thông số cấu hình công khai của hệ thống:
 * - /api/system/models: Trả về model đang kích hoạt và danh sách model được phép sử dụng.
 * - /api/system/tools: Trả về danh sách các công cụ Agent đang được Admin kích hoạt (enabled).
 * Endpoint này mở công khai (PermitAll) để giao diện người dùng hiển thị danh sách lựa chọn chính xác.
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemConfigService systemConfigService;
    private final ToolManagerService toolManagerService;

    /**
     * Lấy cấu hình các mô hình AI khả dụng cho người dùng lựa chọn:
     * - activeModel: Tên mô hình AI mặc định đang kích hoạt trong hệ thống
     * - allowedModels: Danh sách các mô hình mà Quản trị viên cho phép người dùng chọn
     *
     * @return ResponseEntity chứa Map với activeModel và allowedModels
     */
    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> getAllowedModels() {
        return ResponseEntity.ok(Map.of(
                "activeModel", systemConfigService.getActiveModel(),
                "allowedModels", systemConfigService.getAllowedModels()
        ));
    }

    /**
     * Lấy danh sách các công cụ (Agent Tools) đang được Quản trị viên BẬT (enabled = true) trong hệ thống.
     * Người dùng chỉ được phép chọn và kích hoạt các công cụ nằm trong danh sách này.
     *
     * @return ResponseEntity chứa danh sách AgentToolDto khả dụng
     */
    @GetMapping("/tools")
    public ResponseEntity<List<AgentToolDto>> getAvailableTools() {
        List<AgentToolDto> enabledTools = toolManagerService.getAllTools().stream()
                .filter(t -> Boolean.TRUE.equals(t.getEnabled()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(enabledTools);
    }
}
