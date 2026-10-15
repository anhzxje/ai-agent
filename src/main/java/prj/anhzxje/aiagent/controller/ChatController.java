package prj.anhzxje.aiagent.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import prj.anhzxje.aiagent.dto.chat.ChatConversationDetailResponse;
import prj.anhzxje.aiagent.dto.chat.ChatConversationResponse;
import prj.anhzxje.aiagent.dto.chat.ChatMessageRequest;
import prj.anhzxje.aiagent.dto.chat.ChatMessageResponse;
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
     * Lấy danh sách các cuộc hội thoại của người dùng đang đăng nhập (lọc theo userId).
     */

    @GetMapping("/conversations")
    public ResponseEntity<List<ChatConversationResponse>> getMyConversations(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(chatService.getMyConversations(userDetails.getUser()));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ChatConversationResponse> createConversation(
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String title = body != null ? body.get("title") : null;
        String model = body != null ? body.get("model") : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.createConversation(userDetails.getUser(), title, model));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<ChatConversationDetailResponse> getConversationDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(chatService.getConversationDetail(id, userDetails.getUser()));
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Map<String, String>> deleteConversation(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        chatService.deleteConversation(id, userDetails.getUser());
        return ResponseEntity.ok(Map.of("message", "Đã xóa cuộc hội thoại #" + id + " thành công"));
    }

    /**
     * Gửi tin nhắn và nhận phản hồi từ AI Agent (tự động tạo cuộc hội thoại nếu chưa có).
     */
    @PostMapping
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @Valid @RequestBody ChatMessageRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(chatService.sendMessage(request, userDetails.getUser()));
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessageInConversation(
            @PathVariable Long id,
            @Valid @RequestBody ChatMessageRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        request.setConversationId(id);
        return ResponseEntity.ok(chatService.sendMessage(request, userDetails.getUser()));
    }
}
