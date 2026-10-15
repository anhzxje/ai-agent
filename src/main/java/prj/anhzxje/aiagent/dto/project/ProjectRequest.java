package prj.anhzxje.aiagent.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Đối tượng DTO yêu cầu tạo hoặc cập nhật Dự án (Project Request):
 * Gửi thông tin thư mục mã nguồn từ client lên server.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectRequest {

    @NotBlank(message = "Tên project không được để trống")
    @Size(max = 100, message = "Tên project tối đa 100 ký tự")
    private String name;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    private String description;

    @NotBlank(message = "Đường dẫn project không được để trống")
    @Size(max = 500, message = "Đường dẫn tối đa 500 ký tự")
    private String path;

    @Size(max = 50, message = "Ngôn ngữ tối đa 50 ký tự")
    private String language;
}
