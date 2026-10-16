package prj.anhzxje.aiagent.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ============================================================================
 * UPDATE CHAT MODEL REQUEST DTO
 * ============================================================================
 * Chuẩn hóa request body cho thao tác thay đổi model AI trong cuộc hội thoại:
 * - model: Tên model AI định danh mới (bắt buộc, tối đa 100 ký tự).
 * ============================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateChatModelRequest {

    @NotBlank(message = "Tên model không được để trống")
    @Size(max = 100, message = "Tên model tối đa 100 ký tự")
    private String model;
}
