package prj.anhzxje.aiagent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import prj.anhzxje.aiagent.dto.chat.ChatConversationDetailResponse;
import prj.anhzxje.aiagent.dto.chat.ChatConversationResponse;
import prj.anhzxje.aiagent.dto.chat.ChatMessageRequest;
import prj.anhzxje.aiagent.dto.chat.ChatMessageResponse;
import prj.anhzxje.aiagent.entity.ChatConversation;
import prj.anhzxje.aiagent.entity.ChatMessage;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.exception.ResourceNotFoundException;
import prj.anhzxje.aiagent.repository.ChatConversationRepository;
import prj.anhzxje.aiagent.repository.ChatMessageRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dịch vụ Quản lý Hội thoại AI Trực tuyến (ChatService):
 * Chịu trách nhiệm:
 * 1. Khởi tạo, truy xuất, xóa các phiên hội thoại (ChatConversation) theo người dùng.
 * 2. Lưu trữ lịch sử tin nhắn của người dùng và câu trả lời của AI vào CSDL MySQL.
 * 3. Giao tiếp với Google Gemini thông qua Spring AI ChatClient để tạo câu trả lời theo thời gian thực.
 * 4. Tự động ghi nhật ký kiểm toán (AuditLog) cho các tương tác hội thoại.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatClient.Builder chatClientBuilder;
    private final SystemConfigService systemConfigService;
    private final AuditLogService auditLogService;

    /** System prompt định hình phong cách phản hồi của AI trong phiên Chat */
    private static final String CHAT_SYSTEM_PROMPT = """
            Bạn là trợ lý AI chuyên sâu về lập trình phần mềm, thiết kế hệ thống và rà soát mã nguồn (Code Review).
            - Trả lời bằng tiếng Việt chuyên nghiệp, súc tích và thân thiện.
            - Hướng dẫn giải pháp kỹ thuật, phân tích lỗi và đề xuất mã nguồn tối ưu có giải thích chi tiết.
            - Khi đưa ra code, hãy định dạng Markdown chuẩn với ngôn ngữ tương ứng.
            """;

    /**
     * Lấy toàn bộ danh sách các cuộc hội thoại của người dùng hiện tại, sắp xếp theo thời gian mới nhất.
     *
     * @param user Tài khoản người dùng đang đăng nhập
     * @return Danh sách tóm tắt cuộc hội thoại
     */
    @Transactional(readOnly = true)
    public List<ChatConversationResponse> getMyConversations(User user) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .map(this::toConversationResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lấy thông tin chi tiết một cuộc hội thoại kèm theo toàn bộ lịch sử tin nhắn.
     * Có kiểm tra quyền sở hữu: chỉ chủ nhân cuộc hội thoại mới được phép đọc.
     */
    @Transactional(readOnly = true)
    public ChatConversationDetailResponse getConversationDetail(Long convId, User user) {
        ChatConversation conv = findConversationAndVerifyOwner(convId, user);
        List<ChatMessageResponse> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId()).stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());

        return ChatConversationDetailResponse.builder()
                .id(conv.getId())
                .title(conv.getTitle())
                .model(conv.getModel())
                .createdAt(conv.getCreatedAt())
                .updatedAt(conv.getUpdatedAt())
                .messages(messages)
                .build();
    }

    /**
     * Khởi tạo một cuộc hội thoại mới với mô hình AI đã chọn hoặc mô hình mặc định.
     * Quy trình:
     * - Bước 1: Xác định model sử dụng: Nếu client truyền lên model cụ thể thì dùng model đó, ngược lại lấy default model từ SystemConfigService.
     * - Bước 2: Thiết lập tiêu đề mặc định ("Cuộc hội thoại mới") nếu client không truyền title.
     * - Bước 3: Tạo và lưu thực thể ChatConversation vào CSDL với thời điểm tạo và cập nhật ban đầu.
     * - Bước 4: Ghi nhật ký kiểm toán (AuditLog) về việc tạo hội thoại mới.
     * - Bước 5: Chuyển đổi và trả về DTO ChatConversationResponse.
     */
    @Transactional
    public ChatConversationResponse createConversation(User user, String title, String model) {
        // Bước 1: Xác định model AI được chọn hoặc lấy cấu hình hệ thống hiện tại
        String activeModel = (model != null && !model.isBlank()) ? model : systemConfigService.getActiveModel();
        // Bước 2: Đặt tiêu đề cuộc trò chuyện
        String convTitle = (title != null && !title.isBlank()) ? title : "Cuộc hội thoại mới";

        // Bước 3: Khởi tạo và lưu thực thể hội thoại
        ChatConversation conv = ChatConversation.builder()
                .user(user)
                .title(convTitle)
                .model(activeModel)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ChatConversation saved = conversationRepository.save(conv);

        // Bước 4: Ghi nhật ký Audit Log
        auditLogService.log(user.getUsername(), "CHAT_CONVERSATION_CREATE", "Tạo cuộc hội thoại mới #" + saved.getId() + ": " + saved.getTitle());
        return toConversationResponse(saved);
    }

    /**
     * Xóa cuộc hội thoại và toàn bộ các tin nhắn liên quan (Xóa thủ công theo nguyên lý Cascade delete).
     * Quy trình:
     * - Bước 1: Xác thực quyền sở hữu: Kiểm tra xem user có phải chủ nhân của hội thoại này không.
     * - Bước 2: Xóa toàn bộ tin nhắn (ChatMessage) thuộc về cuộc hội thoại này trước để tránh lỗi khóa ngoại (Foreign Key Constraint).
     * - Bước 3: Xóa thực thể cuộc hội thoại (ChatConversation).
     * - Bước 4: Ghi nhật ký kiểm toán hành vi xóa.
     */
    @Transactional
    public void deleteConversation(Long convId, User user) {
        // Bước 1: Xác thực quyền sở hữu
        ChatConversation conv = findConversationAndVerifyOwner(convId, user);
        
        // Bước 2: Xóa tất cả các tin nhắn con trong cuộc trò chuyện này
        messageRepository.deleteByConversationId(conv.getId());
        
        // Bước 3: Xóa cuộc trò chuyện
        conversationRepository.delete(conv);
        
        // Bước 4: Ghi nhật ký Audit Log
        auditLogService.log(user.getUsername(), "CHAT_CONVERSATION_DELETE", "Xóa cuộc hội thoại #" + convId);
    }

    /**
     * Gửi tin nhắn từ người dùng, gọi Google Gemini sinh phản hồi và lưu cả 2 vào CSDL.
     * Quy trình xử lý:
     * - Bước 1: Xác định cuộc hội thoại (ChatConversation):
     *     + Nếu có conversationId: Kiểm tra quyền sở hữu của user.
     *     + Nếu chưa có conversationId: Tự động khởi tạo phiên mới, cắt 35 ký tự đầu của tin nhắn làm tiêu đề.
     * - Bước 2: Lưu ngay tin nhắn của User ("USER") vào CSDL để đảm bảo lịch sử hỏi đáp không bị mất.
     * - Bước 3: Sử dụng Spring AI ChatClient gọi Google Gemini với System Prompt chuyên gia lập trình.
     *     + Bắt lỗi ngoại lệ (Timeout, Quota Exceeded...) để trả về thông báo lỗi thân thiện thay vì làm sập API.
     * - Bước 4: Lưu tin nhắn phản hồi của AI ("AGENT") vào CSDL.
     * - Bước 5: Cập nhật trường updatedAt của hội thoại lên thời điểm hiện tại (để sắp xếp hội thoại gần nhất lên đầu).
     * - Bước 6: Ghi nhật ký kiểm toán (AuditLog) và trả về DTO ChatMessageResponse.
     */
    @Transactional
    public ChatMessageResponse sendMessage(ChatMessageRequest request, User user) {
        ChatConversation conv;
        // Bước 1: Tìm hội thoại hiện có hoặc tự động khởi tạo hội thoại mới
        if (request.getConversationId() != null) {
            conv = findConversationAndVerifyOwner(request.getConversationId(), user);
        } else {
            // Tự động trích xuất 35 ký tự đầu tiên để đặt tiêu đề đại diện
            String initialTitle = request.getMessage().length() > 35
                    ? request.getMessage().substring(0, 35) + "..."
                    : request.getMessage();
            conv = conversationRepository.save(ChatConversation.builder()
                    .user(user)
                    .title(initialTitle)
                    .model(systemConfigService.getActiveModel())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build());
        }

        // Bước 2: Lưu tin nhắn của Người dùng (USER) vào CSDL
        ChatMessage userMsg = ChatMessage.builder()
                .conversation(conv)
                .sender("USER")
                .content(request.getMessage())
                .createdAt(LocalDateTime.now())
                .build();
        messageRepository.save(userMsg);

        // Bước 3: Khởi tạo ChatClient và gọi API Google Gemini
        String aiResponseText;
        try {
            aiResponseText = chatClientBuilder.build()
                    .prompt()
                    .system(CHAT_SYSTEM_PROMPT)
                    .user(request.getMessage())
                    .call()
                    .content();

            // Đảm bảo không trả về chuỗi rỗng nếu AI phản hồi trống
            if (aiResponseText == null || aiResponseText.isBlank()) {
                aiResponseText = "Xin lỗi, tôi không thể tạo câu trả lời vào lúc này. Vui lòng thử lại.";
            }
        } catch (Exception e) {
            // Xử lý ngoại lệ an toàn: ghi log lỗi và cung cấp thông báo rõ ràng cho người dùng
            log.error("Lỗi khi gọi AI Chat cho user {}: {}", user.getUsername(), e.getMessage());
            aiResponseText = "Đã xảy ra lỗi khi trao đổi với AI: " + e.getMessage();
        }

        // Bước 4: Lưu tin nhắn phản hồi của AI Agent (AGENT) vào CSDL
        ChatMessage agentMsg = ChatMessage.builder()
                .conversation(conv)
                .sender("AGENT")
                .content(aiResponseText)
                .createdAt(LocalDateTime.now())
                .build();
        ChatMessage savedAgentMsg = messageRepository.save(agentMsg);

        // Bước 5: Cập nhật mốc thời gian hoạt động mới nhất cho cuộc hội thoại
        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conv);

        // Bước 6: Ghi nhật ký Audit Log và trả về phản hồi
        auditLogService.log(user.getUsername(), "CHAT_MESSAGE", "Trao đổi với AI trong hội thoại #" + conv.getId());
        return toMessageResponse(savedAgentMsg);
    }

    /**
     * Xác minh quyền sở hữu cuộc hội thoại của người dùng.
     * Bảo mật chống IDOR: Trả về ResourceNotFoundException dù cuộc hội thoại có tồn tại ở user khác hay không.
     */
    private ChatConversation findConversationAndVerifyOwner(Long convId, User user) {
        ChatConversation conv = conversationRepository.findById(convId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cuộc hội thoại với id: " + convId));

        if (!conv.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Không tìm thấy cuộc hội thoại với id: " + convId);
        }
        return conv;
    }

    private ChatConversationResponse toConversationResponse(ChatConversation conv) {
        return ChatConversationResponse.builder()
                .id(conv.getId())
                .title(conv.getTitle())
                .model(conv.getModel())
                .createdAt(conv.getCreatedAt())
                .updatedAt(conv.getUpdatedAt())
                .build();
    }

    private ChatMessageResponse toMessageResponse(ChatMessage msg) {
        return ChatMessageResponse.builder()
                .id(msg.getId())
                .conversationId(msg.getConversation().getId())
                .sender(msg.getSender())
                .content(msg.getContent())
                .createdAt(msg.getCreatedAt())
                .build();
    }
}
