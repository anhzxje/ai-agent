package prj.anhzxje.aiagent.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import prj.anhzxje.aiagent.dto.chat.*;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.service.ChatService;

import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 * CHAT CONTROLLER - TRÒ CHUYỆN TRỰC TIẾP VỚI AI AGENT (MULTI-SESSION CHAT)
 * ============================================================================
 * Mục đích:
 * 1. Quản lý danh sách các cuộc hội thoại (ChatConversation) theo từng người dùng.
 * 2. Lưu trữ toàn bộ lịch sử tin nhắn (ChatMessage) vào CSDL MySQL.
 * 3. Tiếp nhận tin nhắn từ người dùng, gọi ChatService để điều phối tương tác với
 *    mô hình Google Gemini (thông qua Spring AI ChatClient), và trả kết quả phản hồi.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * Lấy danh sách các cuộc hội thoại của người dùng đang đăng nhập (sắp xếp theo thời gian cập nhật gần nhất).
     *
     * @param userDetails thông tin xác thực của người dùng hiện tại
     * @return ResponseEntity chứa danh sách ChatConversationResponse
     */
    @GetMapping("/conversations")
    public ResponseEntity<List<ChatConversationResponse>> getMyConversations(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(chatService.getMyConversations(userDetails.getUser()));
    }

    /**
     * Khởi tạo một phiên hội thoại mới với tiêu đề và mô hình AI tùy chọn.
     *
     * @param body        chứa thuộc tính title (tiêu đề cuộc hội thoại) và model (mô hình AI chỉ định)
     * @param userDetails thông tin xác thực của người dùng hiện tại
     * @return ResponseEntity chứa thông tin ChatConversationResponse vừa tạo (HTTP 201 Created)
     */
    @PostMapping("/conversations")
    public ResponseEntity<ChatConversationResponse> createConversation(
            @Valid @RequestBody(required = false) CreateConversationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String title = request != null ? request.getTitle() : null;
        String model = request != null ? request.getModel() : null;
        Long projectId = request != null ? request.getProjectId() : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.createConversation(userDetails.getUser(), title, model, projectId));
    }

    /**
     * Lấy thông tin chi tiết một cuộc hội thoại kèm theo toàn bộ lịch sử tin nhắn hỏi đáp.
     *
     * @param id          mã định danh cuộc hội thoại
     * @param userDetails thông tin xác thực của người dùng hiện tại
     * @return ResponseEntity chứa ChatConversationDetailResponse kèm danh sách tin nhắn
     */
    @GetMapping("/conversations/{id}")
    public ResponseEntity<ChatConversationDetailResponse> getConversationDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(chatService.getConversationDetail(id, userDetails.getUser()));
    }

    /**
     * Xóa vĩnh viễn một cuộc hội thoại và toàn bộ tin nhắn bên trong.
     *
     * @param id          mã định danh cuộc hội thoại cần xóa
     * @param userDetails thông tin xác thực của người dùng hiện tại
     * @return ResponseEntity chứa thông điệp xác nhận đã xóa thành công
     */
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Map<String, String>> deleteConversation(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        chatService.deleteConversation(id, userDetails.getUser());
        return ResponseEntity.ok(Map.of("message", "Đã xóa cuộc hội thoại #" + id + " thành công"));
    }

    /**
     * Cập nhật mô hình AI cho cuộc hội thoại.
     *
     * @param id          mã định danh cuộc hội thoại
     * @param body        chứa thuộc tính model
     * @param userDetails thông tin xác thực của người dùng hiện tại
     * @return ResponseEntity chứa ChatConversationResponse sau khi cập nhật
     */
    @PatchMapping("/conversations/{id}/model")
    public ResponseEntity<ChatConversationResponse> updateConversationModel(
            @PathVariable Long id,
            @Valid @RequestBody UpdateChatModelRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String model = request != null ? request.getModel() : null;
        return ResponseEntity.ok(chatService.updateConversationModel(id, model, userDetails.getUser()));
    }

    /**
     * Gửi tin nhắn và nhận phản hồi trực tiếp từ Google Gemini AI.
     * Tự động khởi tạo phiên hội thoại mới nếu client chưa chỉ định conversationId.
     *
     * @param request     chứa nội dung tin nhắn message và conversationId tùy chọn
     * @param userDetails thông tin xác thực của người dùng hiện tại
     * @return ResponseEntity chứa tin nhắn phản hồi của AI (ChatMessageResponse)
     */
    @PostMapping
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @Valid @RequestBody ChatMessageRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(chatService.sendMessage(request, userDetails.getUser()));
    }

    /**
     * Gửi tin nhắn vào một cuộc hội thoại cụ thể đã tồn tại.
     *
     * @param id          mã định danh cuộc hội thoại mục tiêu
     * @param request     chứa nội dung tin nhắn người dùng
     * @param userDetails thông tin xác thực của người dùng hiện tại
     * @return ResponseEntity chứa tin nhắn phản hồi của AI (ChatMessageResponse)
     */
    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessageInConversation(
            @PathVariable Long id,
            @Valid @RequestBody ChatMessageRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        request.setConversationId(id);
        return ResponseEntity.ok(chatService.sendMessage(request, userDetails.getUser()));
    }
}
