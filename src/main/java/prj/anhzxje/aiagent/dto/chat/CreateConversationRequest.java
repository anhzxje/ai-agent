package prj.anhzxje.aiagent.dto.chat;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ============================================================================
 * CREATE CONVERSATION REQUEST DTO
 * ============================================================================
 * Chuẩn hóa request body cho thao tác tạo mới cuộc hội thoại AI:
 * - title: Tiêu đề cuộc trò chuyện (tùy chọn, tối đa 200 ký tự).
 * - model: Tên model AI định danh (tùy chọn, tối đa 100 ký tự).
 * - projectId: ID dự án gắn kèm để AI hiểu context mã nguồn (tùy chọn).
 * ============================================================================
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationRequest {

    @Size(max = 200, message = "Tiêu đề cuộc hội thoại tối đa 200 ký tự")
    private String title;

    @Size(max = 100, message = "Tên model tối đa 100 ký tự")
    private String model;

    private Long projectId;
}
