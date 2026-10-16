package prj.anhzxje.aiagent.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import prj.anhzxje.aiagent.dto.project.ProjectRequest;
import prj.anhzxje.aiagent.dto.project.ProjectResponse;
import prj.anhzxje.aiagent.service.ProjectService;

import java.util.List;

/**
 * Controller quản lý Dự án mã nguồn (Project Management):
 * - Cho phép người dùng tạo, xem, cập nhật, xóa dự án của chính mình.
 * - Tự động gắn quyền sở hữu theo userId của người dùng hiện tại (Multi-tenant).
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /**
     * Lấy danh sách tất cả các dự án mã nguồn thuộc quyền sở hữu của người dùng đang đăng nhập.
     * Sắp xếp giảm dần theo thời gian tạo mới nhất.
     *
     * @return ResponseEntity chứa danh sách ProjectResponse
     */
    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getMyProjects() {
        return ResponseEntity.ok(projectService.getMyProjects());
    }

    /**
     * Lấy thông tin chi tiết một dự án mã nguồn theo ID.
     * Yêu cầu bảo mật: Chỉ cho phép truy xuất nếu dự án thuộc quyền sở hữu của user hiện tại (chống IDOR).
     *
     * @param id mã định danh dự án
     * @return ResponseEntity chứa thông tin ProjectResponse
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProject(id));
    }

    /**
     * Đăng ký tạo mới một dự án mã nguồn vào hệ thống.
     * Tự động liên kết quyền sở hữu với tài khoản người dùng đang đăng nhập.
     *
     * @param request dữ liệu khởi tạo dự án gồm tên, mô tả, đường dẫn thư mục mã nguồn và ngôn ngữ
     * @return ResponseEntity chứa ProjectResponse vừa tạo (HTTP 201 Created)
     */
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.createProject(request));
    }

    /**
     * Cập nhật thông tin dự án hiện có (tên dự án, đường dẫn thư mục, ngôn ngữ lập trình, mô tả).
     *
     * @param id      mã định danh dự án cần cập nhật
     * @param request dữ liệu cập nhật dự án mới
     * @return ResponseEntity chứa ProjectResponse sau khi đã lưu
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.ok(projectService.updateProject(id, request));
    }

    /**
     * Xóa vĩnh viễn dự án mã nguồn khỏi hệ thống.
     *
     * @param id mã định danh dự án cần xóa
     * @return ResponseEntity trả về HTTP 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }
}
