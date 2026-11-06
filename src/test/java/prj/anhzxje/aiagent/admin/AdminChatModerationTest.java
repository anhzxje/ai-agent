package prj.anhzxje.aiagent.admin;

import prj.anhzxje.aiagent.admin.AdminController;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import prj.anhzxje.aiagent.chat.ChatConversationResponse;
import prj.anhzxje.aiagent.chat.ChatMessageResponse;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.ChatConversation;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.ChatMessage;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.User;
import prj.anhzxje.aiagent.shared.enums.Role;
import prj.anhzxje.aiagent.shared.exception.ResourceNotFoundException;
import prj.anhzxje.aiagent.infrastructure.persistence.repository.ChatConversationRepository;
import prj.anhzxje.aiagent.infrastructure.persistence.repository.ChatMessageRepository;
import prj.anhzxje.aiagent.infrastructure.persistence.repository.UserRepository;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.infrastructure.audit.AuditLogService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử Đơn vị cho các API Quản trị viên Kiểm soát & Kiểm duyệt Hội thoại (AdminChatModerationTest):
 * Bao gồm kiểm thử:
 * 1. Tra cứu danh sách cuộc hội thoại của người dùng bất kỳ.
 * 2. Tra cứu toàn bộ lịch sử tin nhắn trong một cuộc hội thoại.
 * 3. Xóa cuộc hội thoại vi phạm kèm cascading xóa tin nhắn và ghi Audit Log.
 * 4. Xóa một tin nhắn vi phạm riêng lẻ và ghi Audit Log.
 * 5. Bắt ngoại lệ ResourceNotFoundException khi id không tồn tại.
 */
@ExtendWith(MockitoExtension.class)
class AdminChatModerationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatConversationRepository chatConversationRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private AuditLogService auditLogService;

    private AdminController adminController;

    private User adminUser;
    private User regularUser;
    private CustomUserDetails adminUserDetails;
    private ChatConversation sampleConversation;
    private ChatMessage sampleMessage;

    @BeforeEach
    void setUp() {
        adminController = new AdminController(new prj.anhzxje.aiagent.admin.AdminService(userRepository, null, null, chatConversationRepository, chatMessageRepository, auditLogService), auditLogService, null, null, null);
        adminUser = User.builder()
                .id(1L)
                .username("admin")
                .role(Role.ADMIN)
                .build();

        regularUser = User.builder()
                .id(2L)
                .username("user1")
                .role(Role.USER)
                .build();

        adminUserDetails = new CustomUserDetails(adminUser);

        sampleConversation = ChatConversation.builder()
                .id(10L)
                .user(regularUser)
                .title("Cuộc trò chuyện thử nghiệm")
                .model("gemini-3.5-flash-lite")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        sampleMessage = ChatMessage.builder()
                .id(101L)
                .conversation(sampleConversation)
                .sender("USER")
                .content("Tin nhắn vi phạm chính sách")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Admin lấy danh sách hội thoại của người dùng thành công")
    void testGetUserConversationsForAdmin_Success() {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(chatConversationRepository.findByUserIdOrderByUpdatedAtDesc(2L))
                .thenReturn(List.of(sampleConversation));

        ResponseEntity<List<ChatConversationResponse>> response = adminController.getUserConversationsForAdmin(2L);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(10L, response.getBody().get(0).getId());
        assertEquals("Cuộc trò chuyện thử nghiệm", response.getBody().get(0).getTitle());
        verify(chatConversationRepository, times(1)).findByUserIdOrderByUpdatedAtDesc(2L);
    }

    @Test
    @DisplayName("Admin lấy danh sách hội thoại của người dùng không tồn tại ném ResourceNotFoundException")
    void testGetUserConversationsForAdmin_UserNotFound_ThrowsException() {
        when(userRepository.existsById(99L)).thenReturn(false);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                adminController.getUserConversationsForAdmin(99L));

        assertTrue(ex.getMessage().contains("Không tìm thấy người dùng với id: 99"));
        verify(chatConversationRepository, never()).findByUserIdOrderByUpdatedAtDesc(anyLong());
    }

    @Test
    @DisplayName("Admin xem toàn bộ tin nhắn trong một cuộc hội thoại thành công")
    void testGetConversationMessagesForAdmin_Success() {
        when(chatConversationRepository.existsById(10L)).thenReturn(true);
        when(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(10L))
                .thenReturn(List.of(sampleMessage));

        ResponseEntity<List<ChatMessageResponse>> response = adminController.getConversationMessagesForAdmin(10L);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(101L, response.getBody().get(0).getId());
        assertEquals("Tin nhắn vi phạm chính sách", response.getBody().get(0).getContent());
        assertEquals("USER", response.getBody().get(0).getSender());
        verify(chatMessageRepository, times(1)).findByConversationIdOrderByCreatedAtAsc(10L);
    }

    @Test
    @DisplayName("Admin xem tin nhắn của hội thoại không tồn tại ném ResourceNotFoundException")
    void testGetConversationMessagesForAdmin_ConversationNotFound_ThrowsException() {
        when(chatConversationRepository.existsById(999L)).thenReturn(false);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                adminController.getConversationMessagesForAdmin(999L));

        assertTrue(ex.getMessage().contains("Không tìm thấy cuộc hội thoại với id: 999"));
        verify(chatMessageRepository, never()).findByConversationIdOrderByCreatedAtAsc(anyLong());
    }

    @Test
    @DisplayName("Admin xóa toàn bộ cuộc hội thoại vi phạm thành công kèm xóa tin nhắn và ghi Audit Log")
    void testDeleteConversationForAdmin_Success() {
        when(chatConversationRepository.findById(10L)).thenReturn(Optional.of(sampleConversation));

        ResponseEntity<?> response = adminController.deleteConversationForAdmin(10L, adminUserDetails);

        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map);
        verify(chatMessageRepository, times(1)).deleteByConversationId(10L);
        verify(chatConversationRepository, times(1)).delete(sampleConversation);
        verify(auditLogService, times(1))
                .log(eq("admin"), eq("MODERATE_DELETE_CONVERSATION"), contains("Admin xóa cuộc hội thoại vi phạm #10"));
    }

    @Test
    @DisplayName("Admin xóa cuộc hội thoại không tồn tại ném ResourceNotFoundException")
    void testDeleteConversationForAdmin_NotFound_ThrowsException() {
        when(chatConversationRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                adminController.deleteConversationForAdmin(999L, adminUserDetails));

        assertTrue(ex.getMessage().contains("Không tìm thấy cuộc hội thoại với id: 999"));
        verify(chatMessageRepository, never()).deleteByConversationId(anyLong());
        verify(chatConversationRepository, never()).delete(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("Admin xóa 1 tin nhắn vi phạm riêng lẻ thành công và ghi Audit Log")
    void testDeleteMessageForAdmin_Success() {
        when(chatMessageRepository.findById(101L)).thenReturn(Optional.of(sampleMessage));

        ResponseEntity<?> response = adminController.deleteMessageForAdmin(101L, adminUserDetails);

        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map);
        verify(chatMessageRepository, times(1)).delete(sampleMessage);
        verify(auditLogService, times(1))
                .log(eq("admin"), eq("MODERATE_DELETE_MESSAGE"), contains("Admin xóa tin nhắn vi phạm #101"));
    }

    @Test
    @DisplayName("Admin xóa tin nhắn không tồn tại ném ResourceNotFoundException")
    void testDeleteMessageForAdmin_NotFound_ThrowsException() {
        when(chatMessageRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                adminController.deleteMessageForAdmin(999L, adminUserDetails));

        assertTrue(ex.getMessage().contains("Không tìm thấy tin nhắn với id: 999"));
        verify(chatMessageRepository, never()).delete(any());
        verifyNoInteractions(auditLogService);
    }
}
