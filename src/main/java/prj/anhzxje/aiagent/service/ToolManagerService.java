package prj.anhzxje.aiagent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import prj.anhzxje.aiagent.dto.admin.AgentToolDto;
import prj.anhzxje.aiagent.entity.AgentToolConfig;
import prj.anhzxje.aiagent.exception.ResourceNotFoundException;
import prj.anhzxje.aiagent.repository.AgentToolConfigRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dịch vụ Quản lý Công cụ của AI Agent (ToolManagerService):
 * Chịu trách nhiệm:
 * 1. Quản lý trạng thái Bật/Tắt (Enable/Disable) của các Tool duyệt file/mã nguồn (ListDirectory, Glob, FileSystem, Grep).
 * 2. Phân giải danh sách Tool thực tế (Tool Resolution) dựa trên cấu hình hệ thống và lựa chọn của người dùng khi tạo tác vụ.
 * 3. Ghi vết kiểm toán (AuditLog) mỗi khi Quản trị viên thay đổi trạng thái kích hoạt công cụ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolManagerService {

    private final AgentToolConfigRepository toolConfigRepository;
    private final AuditLogService auditLogService;

    private List<String> allowedPaths = List.of();

    @org.springframework.beans.factory.annotation.Autowired
    void configureAgentPaths(prj.anhzxje.aiagent.config.AgentProperties properties) {
        this.allowedPaths = properties.getAllowedPaths();
    }

    /**
     * Lấy toàn bộ danh sách công cụ AI và trạng thái bật/tắt hiện tại trong CSDL.
     */
    @Transactional(readOnly = true)
    public List<AgentToolDto> getAllTools() {
        return toolConfigRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Bật hoặc tắt một công cụ cụ thể theo toolName (Dành cho Quản trị viên).
     * Quy trình:
     * - Bước 1: Tra cứu công cụ theo tên định danh (toolName) trong CSDL.
     * - Bước 2: Cập nhật trạng thái enabled (true/false) và mốc thời gian updatedAt.
     * - Bước 3: Lưu lại thực thể vào CSDL.
     * - Bước 4: Ghi nhật ký kiểm toán (AuditLog) hành vi thay đổi cấu hình công cụ của Admin.
     * - Bước 5: Trả về DTO AgentToolDto phản ánh trạng thái mới.
     */
    @Transactional
    public AgentToolDto setToolStatus(String toolName, boolean enabled, String adminUsername) {
        // Bước 1: Tìm kiếm cấu hình công cụ trong CSDL
        AgentToolConfig tool = toolConfigRepository.findByToolName(toolName)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công cụ: " + toolName));

        // Bước 2: Cập nhật trạng thái kích hoạt và thời gian cập nhật
        tool.setEnabled(enabled);
        tool.setUpdatedAt(LocalDateTime.now());

        // Bước 3: Lưu thay đổi vào CSDL
        AgentToolConfig saved = toolConfigRepository.save(tool);

        // Bước 4: Ghi nhật ký kiểm toán hành vi của Quản trị viên
        String action = enabled ? "ENABLE_TOOL" : "DISABLE_TOOL";
        auditLogService.log(adminUsername, action, "Quản trị viên đã " + (enabled ? "bật" : "tắt") + " công cụ: " + tool.getDisplayName());
        log.info("Admin [{}] changed tool [{}] status to {}", adminUsername, toolName, enabled);
        
        // Bước 5: Chuyển đổi sang DTO trả về cho client
        return toDto(saved);
    }

    /**
     * Đảo trạng thái Bật/Tắt của công cụ (Toggle).
     * Tiện ích gọi lại setToolStatus với giá trị phủ định của trạng thái hiện tại.
     */
    @Transactional
    public AgentToolDto toggleTool(String toolName, String adminUsername) {
        AgentToolConfig tool = toolConfigRepository.findByToolName(toolName)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công cụ: " + toolName));

        return setToolStatus(toolName, !Boolean.TRUE.equals(tool.getEnabled()), adminUsername);
    }

    /**
     * Phân giải danh sách thực thể Tool thực tế (Tool Instances) để chuyển cho Spring AI ChatClient.
     * Quy trình xử lý theo nguyên tắc Bảo mật đa tầng (Defense in depth):
     * - Bước 1: Lấy danh sách tên công cụ đang ĐƯỢC PHÉP hoạt động ở cấp Quản trị viên (Admin Master Switch) từ CSDL.
     * - Bước 2: Giao thoa (Intersection) giữa công cụ người dùng yêu cầu (userSelectedTools) và công cụ Admin cho phép.
     *           -> Người dùng KHÔNG THỂ kích hoạt bất kỳ công cụ nào mà Quản trị viên đã tắt trong hệ thống.
     * - Bước 3: Khởi tạo các đối tượng Tool thực tế từ thư viện Spring AI Community:
     *     + "list_directory": Liệt kê cấu trúc thư mục dự án.
     *     + "glob": Tìm kiếm file theo mẫu wildcard (ví dụ: **\/*.java).
     *     + "file_system": Đọc nội dung tệp tin để phân tích code.
     *     + "grep": Tìm kiếm chuỗi/regex trong nội dung các tệp tin.
     * - Bước 4: Trả về danh sách đối tượng Tool sẵn sàng cho ChatClient thực thi Function Calling.
     */
    /**
     * Kiểm tra đường dẫn mục tiêu có hợp lệ và nằm trong danh sách được cấp phép (Sandbox) hay không.
     */
    public boolean isPathAllowed(Path targetPath) {
        if (targetPath == null) return false;
        try {
            prj.anhzxje.aiagent.security.ProjectPathPolicy.requireAllowedDirectory(targetPath.toString(), allowedPaths);
            return true;
        } catch (IllegalArgumentException | SecurityException e) {
            return false;
        }
    }

    /**
     * Tương thích ngược: Phân giải tool không truyền basePath.
     */
    @Transactional(readOnly = true)
    public List<Object> resolveActiveTools(List<String> userSelectedTools) {
        return resolveActiveTools(userSelectedTools, null);
    }

    /**
     * Phân giải danh sách thực thể Tool thực tế (Tool Instances) để chuyển cho Spring AI ChatClient.
     * Quy trình xử lý theo nguyên tắc Bảo mật đa tầng (Defense in depth):
     * - Bước 1: Kiểm tra Sandbox path đối với basePath của dự án.
     * - Bước 2: Lấy danh sách tên công cụ đang ĐƯỢC PHÉP hoạt động ở cấp Quản trị viên (Admin Master Switch) từ CSDL.
     * - Bước 3: Giao thoa (Intersection) giữa công cụ người dùng yêu cầu (userSelectedTools) và công cụ Admin cho phép.
     * - Bước 4: Khởi tạo các đối tượng Tool thực tế gắn với workingDirectory / allowedDirectory an toàn.
     */
    @Transactional(readOnly = true)
    public List<Object> resolveActiveTools(List<String> userSelectedTools, String basePathStr) {
        Path basePath = prj.anhzxje.aiagent.security.ProjectPathPolicy.requireAllowedDirectory(basePathStr, allowedPaths);

        // Bước 1: Lấy danh sách các công cụ đang bật ở cấp Admin (Master Switch)
        Set<String> enabledNames = toolConfigRepository.findByEnabledTrue().stream()
                .map(AgentToolConfig::getToolName)
                .collect(Collectors.toSet());

        // Bước 2: Lọc các công cụ mà User yêu cầu dựa trên danh sách Admin cho phép
        Set<String> finalAllowed;
        if (userSelectedTools != null) {
            finalAllowed = userSelectedTools.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(name -> name.trim().toLowerCase(java.util.Locale.ROOT))
                    .filter(enabledNames::contains)
                    .collect(Collectors.toSet());
        } else {
            finalAllowed = enabledNames;
        }

        var sandbox = new prj.anhzxje.aiagent.agent.SandboxTools(basePath);
        List<Object> tools = finalAllowed.stream().sorted().map(sandbox::tool).toList();

        // Bước 4: Trả về danh sách công cụ đã giải quyết
        return tools;
    }

    /**
     * Chuyển đổi thực thể cấu hình công cụ AgentToolConfig sang DTO AgentToolDto.
     *
     * @param entity thực thể AgentToolConfig nguồn
     * @return AgentToolDto DTO
     */
    private AgentToolDto toDto(AgentToolConfig entity) {
        return AgentToolDto.builder()
                .toolName(entity.getToolName())
                .displayName(entity.getDisplayName())
                .description(entity.getDescription())
                .enabled(Boolean.TRUE.equals(entity.getEnabled()))
                .category(entity.getCategory())
                .build();
    }
}
