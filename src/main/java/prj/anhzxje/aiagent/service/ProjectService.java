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
import prj.anhzxje.aiagent.repository.ProjectRepository;
import prj.anhzxje.aiagent.security.CustomUserDetails;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // Mặc định tất cả các hàm chỉ đọc dữ liệu (tối ưu hóa hiệu năng DB), các hàm ghi sẽ override bằng @Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;

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
     * Quy trình:
     * - Bước 1: Trích xuất user đang thao tác từ Security Context.
     * - Bước 2: Khởi tạo đối tượng Project với các trường name, description, path (đường dẫn thư mục mã nguồn) và language.
     * - Bước 3: Gán user vào trường quan hệ Many-to-One (@ManyToOne) của Project.
     * - Bước 4: Lưu xuống CSDL thông qua projectRepository và ghi log thông tin.
     * - Bước 5: Chuyển đổi sang ProjectResponse trả về client.
     */
    @Transactional
    public ProjectResponse createProject(ProjectRequest request) {
        User currentUser = getCurrentUser();

        // Bước 2 & 3: Tạo entity Project liên kết với user hiện tại
        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .path(request.getPath())
                .language(request.getLanguage())
                .user(currentUser)
                .build();

        // Bước 4: Lưu project vào database
        Project saved = projectRepository.save(project);
        log.info("Đã tạo project '{}' cho user '{}'", saved.getName(), currentUser.getUsername());
        
        // Bước 5: Trả về DTO kết quả
        return toResponse(saved);
    }

    /**
     * Cập nhật thông tin dự án hiện có.
     * Quy trình:
     * - Bước 1: Tìm kiếm dự án và xác minh quyền sở hữu của user hiện tại.
     * - Bước 2: Cập nhật các trường thông tin mới (name, description, path, language).
     * - Bước 3: Lưu thay đổi xuống CSDL và ghi nhận log hệ thống.
     */
    @Transactional
    public ProjectResponse updateProject(Long projectId, ProjectRequest request) {
        // Bước 1: Xác minh user sở hữu dự án này
        Project project = findProjectAndVerifyOwner(projectId);

        // Bước 2: Cập nhật các giá trị mới từ Request
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setPath(request.getPath());
        project.setLanguage(request.getLanguage());

        // Bước 3: Lưu cập nhật vào CSDL
        Project saved = projectRepository.save(project);
        log.info("Đã cập nhật project '{}'", saved.getName());
        return toResponse(saved);
    }

    /**
     * Xóa một dự án khỏi hệ thống.
     * Quy trình:
     * - Bước 1: Tìm kiếm dự án và xác minh quyền sở hữu của user hiện tại.
     * - Bước 2: Xóa thực thể Project khỏi CSDL (các task liên quan nếu có cấu hình cascade sẽ được xử lý tương ứng).
     * - Bước 3: Ghi log xác nhận đã xóa thành công.
     */
    @Transactional
    public void deleteProject(Long projectId) {
        // Bước 1: Xác minh quyền sở hữu
        Project project = findProjectAndVerifyOwner(projectId);
        
        // Bước 2: Xóa entity khỏi database
        projectRepository.delete(project);
        log.info("Đã xóa project id={}", projectId);
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
