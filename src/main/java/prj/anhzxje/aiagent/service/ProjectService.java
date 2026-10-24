package prj.anhzxje.aiagent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import prj.anhzxje.aiagent.dto.project.ProjectRequest;
import prj.anhzxje.aiagent.dto.project.ProjectResponse;
import prj.anhzxje.aiagent.entity.Project;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.exception.ResourceNotFoundException;
import prj.anhzxje.aiagent.repository.AgentTaskRepository;
import prj.anhzxje.aiagent.repository.ProjectRepository;
import prj.anhzxje.aiagent.security.CustomUserDetails;

import prj.anhzxje.aiagent.entity.ChatConversation;
import prj.anhzxje.aiagent.repository.ChatConversationRepository;
import prj.anhzxje.aiagent.repository.ChatMessageRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // Mặc định tất cả các hàm chỉ đọc dữ liệu (tối ưu hóa hiệu năng DB), các hàm ghi sẽ override bằng @Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final AgentTaskRepository agentTaskRepository;
    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;

    private List<String> allowedPaths = List.of();

    @org.springframework.beans.factory.annotation.Autowired
    void configureAgentPaths(prj.anhzxje.aiagent.config.AgentProperties properties) {
        this.allowedPaths = properties.getAllowedPaths();
    }

    /**
     * Lấy danh sách toàn bộ project thuộc quyền sở hữu của người dùng đang đăng nhập.
     * Quy trình:
     * - Bước 1: Lấy thông tin user hiện tại từ Security Context.
     * - Bước 2: Truy vấn CSDL lọc theo userId và sắp xếp giảm dần theo thời gian tạo (mới nhất lên đầu).
     * - Bước 3: Chuyển đổi (map) danh sách thực thể Project sang DTO ProjectResponse trả về cho Client.
     */
    public List<ProjectResponse> getMyProjects() {
        User currentUser = getCurrentUser();
        return projectRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lấy chi tiết thông tin của 1 dự án theo ID.
     * Quy trình:
     * - Bước 1: Tìm dự án trong CSDL và kiểm tra quyền sở hữu của user hiện tại (tránh lỗi IDOR).
     * - Bước 2: Đóng gói dữ liệu sang DTO ProjectResponse để trả về client.
     */
    public ProjectResponse getProject(Long projectId) {
        Project project = findProjectAndVerifyOwner(projectId);
        return toResponse(project);
    }

    /**
     * Tạo mới một dự án và gán quyền sở hữu cho người dùng hiện tại.
     * Kiểm tra chặt chẽ:
     * 1. Thư mục mã nguồn phải có thật trên hệ thống (Files.exists & Files.isDirectory).
     * 2. Thư mục phải nằm trong danh mục Sandbox cho phép (agent.allowed-paths).
     * 3. Tên dự án không được trùng lặp trong tài khoản người dùng.
     * 4. Đường dẫn dự án không được trùng lặp với dự án đã liên kết trước đó.
     */
    @Transactional
    public ProjectResponse createProject(ProjectRequest request) {
        User currentUser = getCurrentUser();

        // Chuẩn hóa và kiểm tra đường dẫn thư mục
        String rawPath = request.getPath() != null ? request.getPath().trim() : "";
        if (rawPath.isBlank()) {
            throw new IllegalArgumentException("Đường dẫn thư mục dự án không được để trống");
        }

        Path dirPath;
        try {
            dirPath = Path.of(rawPath).toAbsolutePath().normalize();
        } catch (Exception e) {
            throw new IllegalArgumentException("Đường dẫn thư mục dự án không hợp lệ: " + rawPath);
        }

        if (!Files.exists(dirPath)) {
            throw new IllegalArgumentException("Thư mục dự án không tồn tại trên hệ thống: " + rawPath);
        }
        if (!Files.isDirectory(dirPath)) {
            throw new IllegalArgumentException("Đường dẫn cung cấp không phải là một thư mục hợp lệ: " + rawPath);
        }

        // Kiểm tra an toàn Sandbox
        dirPath = prj.anhzxje.aiagent.security.ProjectPathPolicy.requireAllowedDirectory(rawPath, allowedPaths);

        // Kiểm tra trùng lặp tên dự án
        String trimmedName = request.getName() != null ? request.getName().trim() : "";
        if (trimmedName.isBlank()) {
            throw new IllegalArgumentException("Tên dự án không được để trống");
        }
        if (projectRepository.existsByUserIdAndNameIgnoreCase(currentUser.getId(), trimmedName)) {
            throw new IllegalArgumentException("Dự án với tên '" + trimmedName + "' đã tồn tại trong tài khoản của bạn. Vui lòng chọn tên khác để tránh nhầm lẫn.");
        }

        // Kiểm tra trùng lặp đường dẫn dự án
        String normPathStr = dirPath.toString().replace('\\', '/');
        if (projectRepository.existsByUserIdAndPath(currentUser.getId(), normPathStr) ||
            projectRepository.existsByUserIdAndPath(currentUser.getId(), rawPath)) {
            throw new IllegalArgumentException("Thư mục mã nguồn này đã được liên kết với một dự án khác trong tài khoản của bạn.");
        }

        // Tạo entity Project liên kết với user hiện tại
        Project project = Project.builder()
                .name(trimmedName)
                .description(request.getDescription())
                .path(normPathStr)
                .language(request.getLanguage() != null && !request.getLanguage().isBlank() ? request.getLanguage().trim() : "Đa ngôn ngữ")
                .user(currentUser)
                .build();

        Project saved = projectRepository.save(project);
        log.info("Đã tạo project '{}' (path={}) cho user '{}'", saved.getName(), saved.getPath(), currentUser.getUsername());
        return toResponse(saved);
    }

    /**
     * Cập nhật thông tin dự án hiện có.
     */
    @Transactional
    public ProjectResponse updateProject(Long projectId, ProjectRequest request) {
        Project project = findProjectAndVerifyOwner(projectId);
        User currentUser = getCurrentUser();

        String rawPath = request.getPath() != null ? request.getPath().trim() : "";
        if (rawPath.isBlank()) {
            throw new IllegalArgumentException("Đường dẫn thư mục dự án không được để trống");
        }

        Path dirPath;
        try {
            dirPath = Path.of(rawPath).toAbsolutePath().normalize();
        } catch (Exception e) {
            throw new IllegalArgumentException("Đường dẫn thư mục dự án không hợp lệ: " + rawPath);
        }

        if (!Files.exists(dirPath)) {
            throw new IllegalArgumentException("Thư mục dự án không tồn tại trên hệ thống: " + rawPath);
        }
        if (!Files.isDirectory(dirPath)) {
            throw new IllegalArgumentException("Đường dẫn cung cấp không phải là một thư mục: " + rawPath);
        }

        dirPath = prj.anhzxje.aiagent.security.ProjectPathPolicy.requireAllowedDirectory(rawPath, allowedPaths);

        String trimmedName = request.getName() != null ? request.getName().trim() : "";
        if (trimmedName.isBlank()) {
            throw new IllegalArgumentException("Tên dự án không được để trống");
        }
        if (projectRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(currentUser.getId(), trimmedName, projectId)) {
            throw new IllegalArgumentException("Dự án với tên '" + trimmedName + "' đã được sử dụng ở dự án khác của bạn.");
        }

        String normPathStr = dirPath.toString().replace('\\', '/');
        if (projectRepository.existsByUserIdAndPathAndIdNot(currentUser.getId(), normPathStr, projectId)) {
            throw new IllegalArgumentException("Đường dẫn này đã được liên kết với một dự án khác của bạn.");
        }

        project.setName(trimmedName);
        project.setDescription(request.getDescription());
        project.setPath(normPathStr);
        project.setLanguage(request.getLanguage() != null && !request.getLanguage().isBlank() ? request.getLanguage().trim() : "Đa ngôn ngữ");

        Project saved = projectRepository.save(project);
        log.info("Đã cập nhật project id={} ('{}')", projectId, saved.getName());
        return toResponse(saved);
    }

    /**
     * Xóa vĩnh viễn dự án khỏi hệ thống.
     * Cascade sạch sẽ:
     * 1. Xóa toàn bộ Agent Tasks của dự án này.
     * 2. Xóa toàn bộ Chat Conversations và tin nhắn thuộc dự án này.
     * 3. Xóa bản ghi Project.
     */
    @Transactional
    public void deleteProject(Long projectId) {
        Project project = findProjectAndVerifyOwner(projectId);

        // Bước 1: Xóa toàn bộ các tác vụ AI liên quan
        List<prj.anhzxje.aiagent.entity.AgentTask> tasks = agentTaskRepository.findByProjectId(projectId);
        if (!tasks.isEmpty()) {
            agentTaskRepository.deleteAll(tasks);
        }

        // Bước 2: Xóa toàn bộ các phiên chat và tin nhắn liên quan đến dự án này
        List<ChatConversation> convs = chatConversationRepository.findByProjectId(projectId);
        for (ChatConversation c : convs) {
            chatMessageRepository.deleteByConversationId(c.getId());
        }
        if (!convs.isEmpty()) {
            chatConversationRepository.deleteAll(convs);
        }

        // Bước 3: Xóa entity khỏi database
        projectRepository.delete(project);
        log.info("Đã xóa hoàn tất project id={} cùng {} tác vụ và {} phiên chat liên quan",
                projectId, tasks.size(), convs.size());
    }


    // ────────────────────────────────────────────────
    // Phương thức phụ trợ (Helpers)
    // ────────────────────────────────────────────────

    /**
     * Tìm dự án theo ID và kiểm tra tính hợp lệ của quyền sở hữu.
     * Điểm quan trọng về bảo mật:
     * Nếu dự án không tồn tại HOẶC thuộc về user khác, hệ thống đều ném ra cùng ngoại lệ ResourceNotFoundException.
     * Điều này ngăn chặn kẻ tấn công dò quét (Enumeration/IDOR) để biết ID dự án nào đang tồn tại trong hệ thống.
     */
    private Project findProjectAndVerifyOwner(Long projectId) {
        User currentUser = getCurrentUser();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy project với id: " + projectId));

        // Kiểm tra xem ID người tạo có khớp với ID người dùng đang đăng nhập không
        if (!project.getUser().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Không tìm thấy project với id: " + projectId);
        }
        return project;
    }

    /**
     * Lấy thực thể User hiện tại từ Security Context của Spring Security.
     */
    private User getCurrentUser() {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return userDetails.getUser();
    }

    /**
     * Chuyển đổi từ thực thể Project sang đối tượng DTO ProjectResponse.
     */
    private ProjectResponse toResponse(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .path(project.getPath())
                .language(project.getLanguage())
                .ownerUsername(project.getUser() != null ? project.getUser().getUsername() : null)
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
