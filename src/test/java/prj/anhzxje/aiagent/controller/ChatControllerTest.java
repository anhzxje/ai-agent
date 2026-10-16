package prj.anhzxje.aiagent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import prj.anhzxje.aiagent.dto.chat.*;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.enums.Role;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.service.ChatService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử Đơn vị cho ChatController (ChatControllerTest):
 * Kiểm tra các endpoint tạo mới hội thoại có project context, xem danh sách, chi tiết và gửi tin nhắn.
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @InjectMocks
    private ChatController chatController;

    private User currentUser;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().id(1L).username("testuser").role(Role.USER).build();
        userDetails = new CustomUserDetails(currentUser);
    }

    @Test
    void testGetMyConversations() {
        ChatConversationResponse c = ChatConversationResponse.builder()
                .id(1L)
                .title("Test Conv")
                .model("gemini-3.5-flash-lite")
                .build();
        when(chatService.getMyConversations(currentUser)).thenReturn(List.of(c));

        ResponseEntity<List<ChatConversationResponse>> response = chatController.getMyConversations(userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(1L, response.getBody().get(0).getId());
    }

    @Test
    void testCreateConversation_WithProjectId() {
        ChatConversationResponse c = ChatConversationResponse.builder()
                .id(2L)
                .title("Project Chat")
                .projectId(10L)
                .projectName("My App")
                .build();
        when(chatService.createConversation(currentUser, "Project Chat", "gemini-3.5-flash-lite", 10L)).thenReturn(c);

        CreateConversationRequest request = CreateConversationRequest.builder()
                .title("Project Chat")
                .model("gemini-3.5-flash-lite")
                .projectId(10L)
                .build();

        ResponseEntity<ChatConversationResponse> response = chatController.createConversation(request, userDetails);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2L, response.getBody().getId());
        assertEquals(10L, response.getBody().getProjectId());
    }

    @Test
    void testUpdateConversationModel() {
        ChatConversationResponse c = ChatConversationResponse.builder()
                .id(1L)
                .title("Chat")
                .model("gemini-1.5-pro")
                .build();
        when(chatService.updateConversationModel(1L, "gemini-1.5-pro", currentUser)).thenReturn(c);

        UpdateChatModelRequest request = UpdateChatModelRequest.builder()
                .model("gemini-1.5-pro")
                .build();

        ResponseEntity<ChatConversationResponse> response = chatController.updateConversationModel(1L, request, userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("gemini-1.5-pro", response.getBody().getModel());
    }

    @Test
    void testGetConversationDetail() {
        ChatConversationDetailResponse detail = ChatConversationDetailResponse.builder()
                .id(1L)
                .title("Test Conv")
                .messages(List.of())
                .build();
        when(chatService.getConversationDetail(1L, currentUser)).thenReturn(detail);

        ResponseEntity<ChatConversationDetailResponse> response = chatController.getConversationDetail(1L, userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1L, response.getBody().getId());
    }

    @Test
    void testDeleteConversation() {
        doNothing().when(chatService).deleteConversation(1L, currentUser);

        ResponseEntity<Map<String, String>> response = chatController.deleteConversation(1L, userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(chatService, times(1)).deleteConversation(1L, currentUser);
    }

    @Test
    void testSendMessage() {
        ChatMessageRequest request = ChatMessageRequest.builder()
                .message("Hello AI")
                .build();
        ChatMessageResponse msgResponse = ChatMessageResponse.builder()
                .id(100L)
                .sender("AGENT")
                .content("Hello User!")
                .build();
        when(chatService.sendMessage(request, currentUser)).thenReturn(msgResponse);

        ResponseEntity<ChatMessageResponse> response = chatController.sendMessage(request, userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("AGENT", response.getBody().getSender());
        assertEquals("Hello User!", response.getBody().getContent());
    }
}
