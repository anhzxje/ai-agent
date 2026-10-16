package prj.anhzxje.aiagent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import prj.anhzxje.aiagent.dto.chat.ChatConversationDetailResponse;
import prj.anhzxje.aiagent.dto.chat.ChatConversationResponse;
import prj.anhzxje.aiagent.dto.chat.ChatMessageRequest;
import prj.anhzxje.aiagent.dto.chat.ChatMessageResponse;
import prj.anhzxje.aiagent.entity.ChatConversation;
import prj.anhzxje.aiagent.entity.ChatMessage;
import prj.anhzxje.aiagent.entity.Project;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.enums.Role;
import prj.anhzxje.aiagent.exception.ResourceNotFoundException;
import prj.anhzxje.aiagent.repository.ChatConversationRepository;
import prj.anhzxje.aiagent.repository.ChatMessageRepository;
import prj.anhzxje.aiagent.repository.ProjectRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử Đơn vị cho Tầng Dịch vụ Trò chuyện AI (ChatServiceTest):
 * Bao gồm kiểm thử:
 * 1. Khởi tạo cuộc hội thoại với model hợp lệ và model cấm/không hợp lệ.
 * 2. Khởi tạo cuộc hội thoại gắn với Project ngữ cảnh.
 * 3. Gửi tin nhắn và duy trì ngữ cảnh đa lượt (Multi-turn Context) cùng Tool Calling.
 * 4. Xóa cuộc hội thoại kèm tin nhắn con (Cascade Delete).
 * 5. Kiểm tra phòng thủ chống tấn công IDOR khi xem hội thoại người khác.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatConversationRepository conversationRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient.Builder chatClientBuilder;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ToolManagerService toolManagerService;

    @InjectMocks
    private ChatService chatService;

    private User currentUser;
    private User otherUser;
    private ChatConversation conversation;
    private Project project;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().id(1L).username("testuser").role(Role.USER).build();
        otherUser = User.builder().id(2L).username("otheruser").role(Role.USER).build();

        project = Project.builder()
                .id(100L)
                .name("test-project")
                .path("D:/AI Agent/aiagent")
                .language("Java")
                .user(currentUser)
                .build();

        conversation = ChatConversation.builder()
                .id(10L)
                .user(currentUser)
                .project(project)
                .title("Hội thoại mẫu")
                .model("gemini-3.5-flash-lite")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testCreateConversation_Success() {
        when(systemConfigService.getAllowedModels()).thenReturn(List.of("gemini-3.5-flash-lite", "gemini-3.1-flash-lite"));
        when(conversationRepository.save(any(ChatConversation.class))).thenAnswer(i -> {
            ChatConversation c = i.getArgument(0);
            c.setId(10L);
            return c;
        });

        ChatConversationResponse response = chatService.createConversation(currentUser, "Tiêu đề mới", "gemini-3.1-flash-lite");

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("Tiêu đề mới", response.getTitle());
        assertEquals("gemini-3.1-flash-lite", response.getModel());
        verify(auditLogService, times(1)).log(eq("testuser"), eq("CHAT_CONVERSATION_CREATE"), anyString());
    }

    @Test
    void testCreateConversation_WithProject_Success() {
        when(systemConfigService.getAllowedModels()).thenReturn(List.of("gemini-3.5-flash-lite"));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(conversationRepository.save(any(ChatConversation.class))).thenAnswer(i -> {
            ChatConversation c = i.getArgument(0);
            c.setId(10L);
            return c;
        });

        ChatConversationResponse response = chatService.createConversation(currentUser, "Review codebase", "gemini-3.5-flash-lite", 100L);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals(100L, response.getProjectId());
        assertEquals("test-project", response.getProjectName());
        verify(auditLogService, times(1)).log(eq("testuser"), eq("CHAT_CONVERSATION_CREATE"), anyString());
    }

    @Test
    void testCreateConversation_InvalidModel_ThrowsException() {
        when(systemConfigService.getAllowedModels()).thenReturn(List.of("gemini-3.5-flash-lite"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                chatService.createConversation(currentUser, "Tiêu đề", "invalid-model"));

        assertTrue(ex.getMessage().contains("không nằm trong danh sách"));
    }

    @Test
    void testGetConversationDetail_Success() {
        ChatMessage msg1 = ChatMessage.builder().id(101L).conversation(conversation).sender("USER").content("Chào bạn").build();
        ChatMessage msg2 = ChatMessage.builder().id(102L).conversation(conversation).sender("AGENT").content("Chào! Tôi có thể giúp gì?").build();

        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(msg1, msg2));

        ChatConversationDetailResponse detail = chatService.getConversationDetail(10L, currentUser);

        assertNotNull(detail);
        assertEquals(10L, detail.getId());
        assertEquals(100L, detail.getProjectId());
        assertEquals("test-project", detail.getProjectName());
        assertEquals("D:/AI Agent/aiagent", detail.getProjectPath());
        assertEquals(2, detail.getMessages().size());
        assertEquals("Chào bạn", detail.getMessages().get(0).getContent());
    }

    @Test
    void testGetConversationDetail_IDOR_ThrowsResourceNotFoundException() {
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));

        assertThrows(ResourceNotFoundException.class, () ->
                chatService.getConversationDetail(10L, otherUser));
    }

    @Test
    void testDeleteConversation_SuccessWithCascade() {
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));

        chatService.deleteConversation(10L, currentUser);

        verify(messageRepository, times(1)).deleteByConversationId(10L);
        verify(conversationRepository, times(1)).delete(conversation);
        verify(auditLogService, times(1)).log(eq("testuser"), eq("CHAT_CONVERSATION_DELETE"), anyString());
    }

    @Test
    void testSendMessage_WithConversationHistory_MultiTurnSuccess() {
        ChatMessage oldMsg = ChatMessage.builder().id(101L).conversation(conversation).sender("USER").content("Tôi tên Minh").build();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(oldMsg));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(i -> {
            ChatMessage m = i.getArgument(0);
            m.setId(102L);
            return m;
        });

        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call().content()).thenReturn("Chào Minh, tôi nhớ tên bạn rồi!");

        ChatMessageRequest request = ChatMessageRequest.builder()
                .conversationId(10L)
                .message("Tên tôi là gì?")
                .build();

        ChatMessageResponse response = chatService.sendMessage(request, currentUser);

        assertNotNull(response);
        assertEquals("Chào Minh, tôi nhớ tên bạn rồi!", response.getContent());
        assertEquals("AGENT", response.getSender());
        verify(conversationRepository, times(1)).save(conversation);
    }

    @Test
    void testSendMessage_WithProjectContext_AttachesToolsAndResolvesContext() {
        Project project = Project.builder()
                .id(55L)
                .name("Soopi Core")
                .path("/path/to/soopi")
                .language("Java")
                .user(currentUser)
                .build();
        conversation.setProject(project);

        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(i -> {
            ChatMessage m = i.getArgument(0);
            m.setId(103L);
            return m;
        });

        when(toolManagerService.resolveActiveTools(null)).thenReturn(List.of("mockToolInstance"));

        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.tools(any(Object[].class))).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call().content()).thenReturn("Dự án Soopi Core có cấu trúc rất tốt.");

        ChatMessageRequest request = ChatMessageRequest.builder()
                .conversationId(10L)
                .projectId(55L)
                .message("Review kiến trúc dự án")
                .build();

        ChatMessageResponse response = chatService.sendMessage(request, currentUser);

        assertNotNull(response);
        assertEquals("Dự án Soopi Core có cấu trúc rất tốt.", response.getContent());
        verify(toolManagerService, times(1)).resolveActiveTools(null);
        verify(requestSpec, times(1)).tools(any(Object[].class));
    }
}
