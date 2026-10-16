package prj.anhzxje.aiagent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting.HarmBlockMethod;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting.HarmBlockThreshold;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting.HarmCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import prj.anhzxje.aiagent.dto.chat.ChatConversationDetailResponse;
import prj.anhzxje.aiagent.dto.chat.ChatConversationResponse;
import prj.anhzxje.aiagent.dto.chat.ChatMessageRequest;
import prj.anhzxje.aiagent.dto.chat.ChatMessageResponse;
import prj.anhzxje.aiagent.entity.ChatConversation;
import prj.anhzxje.aiagent.entity.ChatMessage;
import prj.anhzxje.aiagent.entity.Project;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.exception.ResourceNotFoundException;
import prj.anhzxje.aiagent.repository.ChatConversationRepository;
import prj.anhzxje.aiagent.repository.ChatMessageRepository;
import prj.anhzxje.aiagent.repository.ProjectRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dịch vụ Quản lý Hội thoại AI Trực tuyến (ChatService):
 * Chịu trách nhiệm:
 * 1. Khởi tạo, truy xuất, xóa các phiên hội thoại (ChatConversation) theo người dùng và dự án.
 * 2. Lưu trữ lịch sử tin nhắn của người dùng và câu trả lời của AI vào CSDL MySQL.
 * 3. Giao tiếp với Google Gemini thông qua Spring AI ChatClient, nạp ngữ cảnh dự án và gắn các tool duyệt file.
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
    private final ProjectRepository projectRepository;
    private final ToolManagerService toolManagerService;

    /**
     * Safety settings cho Gemini — cấu hình tối ưu để model không từ chối các phân tích mã nguồn/bảo mật.
     */
    private final List<GoogleGenAiSafetySetting> safetySettings = List.of(
            new GoogleGenAiSafetySetting(HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT, HarmBlockThreshold.BLOCK_ONLY_HIGH, HarmBlockMethod.PROBABILITY),
            new GoogleGenAiSafetySetting(HarmCategory.HARM_CATEGORY_HARASSMENT, HarmBlockThreshold.BLOCK_ONLY_HIGH, HarmBlockMethod.PROBABILITY),
            new GoogleGenAiSafetySetting(HarmCategory.HARM_CATEGORY_HATE_SPEECH, HarmBlockThreshold.BLOCK_ONLY_HIGH, HarmBlockMethod.PROBABILITY),
            new GoogleGenAiSafetySetting(HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT, HarmBlockThreshold.BLOCK_ONLY_HIGH, HarmBlockMethod.PROBABILITY)
    );

    /** System prompt định hình phong cách phản hồi của AI trong phiên Chat */
    private static final String CHAT_SYSTEM_PROMPT = """
            Bạn là Soopi - Trợ lý AI Coding Assistant chuyên sâu về lập trình phần mềm, kiến trúc hệ thống và rà soát mã nguồn (Code Review).
            - Trả lời bằng tiếng Việt chuyên nghiệp, súc tích, chính xác và thân thiện.
            - Hướng dẫn giải pháp kỹ thuật, phân tích nguyên nhân lỗi và đề xuất mã nguồn tối ưu có giải thích chi tiết.
            - Khi đưa ra code, hãy định dạng Markdown chuẩn với ngôn ngữ tương ứng (ví dụ: ```java, ```javascript).
            """;

    /**
     * Lấy toàn bộ danh sách các cuộc hội thoại của người dùng hiện tại, sắp xếp theo thời gian mới nhất.
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

        return toConversationDetailResponse(conv, messages);
    }

    /**
     * Khởi tạo một cuộc hội thoại mới với mô hình AI đã chọn hoặc mô hình mặc định (tương thích ngược).
     */
    @Transactional
    public ChatConversationResponse createConversation(User user, String title, String model) {
        return createConversation(user, title, model, null);
    }

    /**
     * Khởi tạo một cuộc hội thoại mới có liên kết với Dự án mã nguồn (Project Context).
     */
    @Transactional
    public ChatConversationResponse createConversation(User user, String title, String model, Long projectId) {
        String activeModel;
        if (model != null && !model.isBlank()) {
            String trimmedModel = model.trim();
            List<String> allowed = systemConfigService.getAllowedModels();
            if (!allowed.contains(trimmedModel)) {
                throw new IllegalArgumentException("Model '" + trimmedModel + "' không nằm trong danh sách model được phép: " + allowed);
            }
            activeModel = trimmedModel;
        } else {
            activeModel = systemConfigService.getActiveModel();
        }

        Project project = null;
        if (projectId != null) {
            project = projectRepository.findById(projectId)
                    .filter(p -> p.getUser().getId().equals(user.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dự án với id: " + projectId));
        }

        String convTitle = (title != null && !title.isBlank()) ? title : (project != null ? "Hội thoại: " + project.getName() : "Cuộc hội thoại mới");

        ChatConversation conv = ChatConversation.builder()
                .user(user)
                .project(project)
                .title(convTitle)
                .model(activeModel)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ChatConversation saved = conversationRepository.save(conv);
        auditLogService.log(user.getUsername(), "CHAT_CONVERSATION_CREATE", "Tạo cuộc hội thoại mới #" + saved.getId() + ": " + saved.getTitle() + (project != null ? " (Project: " + project.getName() + ")" : ""));
        return toConversationResponse(saved);
    }

    /**
     * Xóa cuộc hội thoại và toàn bộ các tin nhắn liên quan.
     */
    @Transactional
    public void deleteConversation(Long convId, User user) {
        ChatConversation conv = findConversationAndVerifyOwner(convId, user);
        messageRepository.deleteByConversationId(conv.getId());
        conversationRepository.delete(conv);
        auditLogService.log(user.getUsername(), "CHAT_CONVERSATION_DELETE", "Xóa cuộc hội thoại #" + convId);
    }

    /**
     * Gửi tin nhắn từ người dùng, nạp context dự án và gọi Google Gemini (kèm Tool Calling nếu có project).
     */
    @Transactional
    public ChatMessageResponse sendMessage(ChatMessageRequest request, User user) {
        ChatConversation conv;
        // Bước 1: Tìm hội thoại hiện có hoặc tự động khởi tạo hội thoại mới
        if (request.getConversationId() != null) {
            conv = findConversationAndVerifyOwner(request.getConversationId(), user);
            // Nếu hội thoại chưa gán project nhưng request có projectId, liên kết bổ sung
            if (conv.getProject() == null && request.getProjectId() != null) {
                projectRepository.findById(request.getProjectId())
                        .filter(p -> p.getUser().getId().equals(user.getId()))
                        .ifPresent(p -> {
                            conv.setProject(p);
                            conversationRepository.save(conv);
                        });
            }
        } else {
            Project proj = null;
            if (request.getProjectId() != null) {
                proj = projectRepository.findById(request.getProjectId())
                        .filter(p -> p.getUser().getId().equals(user.getId()))
                        .orElse(null);
            }
            String initialTitle = request.getMessage().length() > 35
                    ? request.getMessage().substring(0, 35) + "..."
                    : request.getMessage();
            conv = conversationRepository.save(ChatConversation.builder()
                    .user(user)
                    .project(proj)
                    .title(initialTitle)
                    .model(systemConfigService.getActiveModel())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build());
        }

        // Bước 2: Lấy lịch sử tin nhắn trước đó để giữ ngữ cảnh hội thoại đa lượt
        List<ChatMessage> previousMessages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId());

        // Bước 3: Lưu tin nhắn của Người dùng (USER) vào CSDL
        ChatMessage userMsg = ChatMessage.builder()
                .conversation(conv)
                .sender("USER")
                .content(request.getMessage())
                .createdAt(LocalDateTime.now())
                .build();
        messageRepository.save(userMsg);

        // Bước 4: Xây dựng Prompt kèm ngữ cảnh đa lượt
        StringBuilder promptWithContext = new StringBuilder();
        if (!previousMessages.isEmpty()) {
            promptWithContext.append("Đây là lịch sử trao đổi trước đó giữa bạn và người dùng:\n---\n");
            int startIdx = Math.max(0, previousMessages.size() - 10);
            for (int i = startIdx; i < previousMessages.size(); i++) {
                ChatMessage m = previousMessages.get(i);
                String senderLabel = "USER".equalsIgnoreCase(m.getSender()) ? "Người dùng" : "Trợ lý AI";
                promptWithContext.append(senderLabel).append(": ").append(m.getContent()).append("\n\n");
            }
            promptWithContext.append("---\nCâu hỏi/Yêu cầu hiện tại của người dùng:\n");
        }
        promptWithContext.append(request.getMessage());

        // Bước 5: Khởi tạo ChatClient và gọi API Google Gemini
        String chatModel = (conv.getModel() != null && !conv.getModel().isBlank())
                ? conv.getModel()
                : systemConfigService.getActiveModel();

        String systemPrompt = buildSystemPrompt(conv.getProject());

        String aiResponseText;
        try {
            var promptSpec = chatClientBuilder.build()
                    .prompt()
                    .system(systemPrompt)
                    .user(promptWithContext.toString());

            // Gắn Tool Calling nếu hội thoại có project context
            if (conv.getProject() != null && toolManagerService != null) {
                List<Object> dynamicTools = toolManagerService.resolveActiveTools(null);
                if (!dynamicTools.isEmpty()) {
                    promptSpec.tools(dynamicTools.toArray());
                }
            }

            var optionsBuilder = GoogleGenAiChatOptions.builder()
                    .safetySettings(safetySettings);
            if (chatModel != null && !chatModel.isBlank()) {
                optionsBuilder.model(chatModel);
            }
            promptSpec.options(optionsBuilder);

            aiResponseText = promptSpec.call().content();

            if (aiResponseText == null || aiResponseText.isBlank()) {
                aiResponseText = "Xin lỗi, tôi không thể tạo câu trả lời vào lúc này. Vui lòng thử lại.";
            }
        } catch (Exception e) {
            log.error("Lỗi khi gọi AI Chat cho user {}: {}", user.getUsername(), e.getMessage());
            aiResponseText = "Đã xảy ra lỗi khi trao đổi với AI: " + e.getMessage();
        }

        // Bước 6: Lưu tin nhắn phản hồi của AI Agent (AGENT) vào CSDL
        ChatMessage agentMsg = ChatMessage.builder()
                .conversation(conv)
                .sender("AGENT")
                .content(aiResponseText)
                .createdAt(LocalDateTime.now())
                .build();
        ChatMessage savedAgentMsg = messageRepository.save(agentMsg);

        // Bước 7: Cập nhật mốc thời gian hoạt động mới nhất cho cuộc hội thoại
        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conv);

        // Bước 8: Ghi nhật ký Audit Log và trả về phản hồi
        auditLogService.log(user.getUsername(), "CHAT_MESSAGE", "Trao đổi với AI trong hội thoại #" + conv.getId());
        return toMessageResponse(savedAgentMsg);
    }

    /**
     * Xây dựng System Prompt định hướng cho AI, kèm thông tin chi tiết về dự án nếu có.
     */
    private String buildSystemPrompt(Project project) {
        if (project == null) {
            return CHAT_SYSTEM_PROMPT;
        }
        return CHAT_SYSTEM_PROMPT + """

                --- DỰ ÁN MÃ NGUỒN HIỆN TẠI (PROJECT CONTEXT) ---
                - Tên dự án: %s
                - Thư mục gốc: %s
                - Ngôn ngữ chính: %s
                - Mô tả: %s

                --- CHỈ DẪN SỬ DỤNG CÔNG CỤ (TOOLS) ---
                1. Bạn được trang bị các công cụ (list_directory, glob, file_system, grep) để duyệt cấu trúc thư mục và đọc nội dung file mã nguồn thực tế của dự án.
                2. Khi người dùng hỏi về kiến trúc dự án, tìm file, giải thích code, tìm lỗi logic, kiểm tra bảo mật hoặc yêu cầu Code Review: HÃY CHỦ ĐỘNG DÙNG TOOL để đọc mã nguồn thực tế trước khi kết luận.
                3. Tuyệt đối KHÔNG bịa đặt file, phương thức hay nội dung mã nguồn không tồn tại. Mọi nhận định kỹ thuật phải dựa trên code thật bạn đã đọc.
                4. Chỉ thao tác duyệt và đọc tệp trong thư mục gốc của dự án này.

                --- ĐỊNH DẠNG KHI RÀ SOÁT / CODE REVIEW ---
                Khi người dùng yêu cầu Code Review, kiểm tra lỗi hoặc đánh giá chất lượng mã nguồn:
                - Trình bày phát hiện theo cấu trúc chuyên nghiệp, có bằng chứng thực tế:
                  * **Mức độ:** [CRITICAL / HIGH / MEDIUM / LOW]
                  * **Tệp tin:** [đường dẫn tệp]
                  * **Vấn đề phát hiện:** [mô tả ngắn gọn, chính xác dựa trên code thật đã đọc]
                  * **Đề xuất khắc phục:** [hướng dẫn sửa và code snippet minh họa]
                - Nếu người dùng muốn tạo một tác vụ Code Review toàn diện chạy ngầm và lưu trữ vào hệ thống quản lý tác vụ, hãy hướng dẫn người dùng chuyển sang tab 'Review Tasks' trên thanh công cụ.
                """.formatted(
                project.getName(),
                project.getPath() != null ? project.getPath() : "Không xác định",
                project.getLanguage() != null ? project.getLanguage() : "Đa ngôn ngữ",
                project.getDescription() != null ? project.getDescription() : "Không có mô tả"
        );
    }

    /**
     * Xác minh quyền sở hữu cuộc hội thoại của người dùng.
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
                .projectId(conv.getProject() != null ? conv.getProject().getId() : null)
                .projectName(conv.getProject() != null ? conv.getProject().getName() : null)
                .createdAt(conv.getCreatedAt())
                .updatedAt(conv.getUpdatedAt())
                .build();
    }

    private ChatConversationDetailResponse toConversationDetailResponse(ChatConversation conv, List<ChatMessageResponse> messages) {
        return ChatConversationDetailResponse.builder()
                .id(conv.getId())
                .title(conv.getTitle())
                .model(conv.getModel())
                .projectId(conv.getProject() != null ? conv.getProject().getId() : null)
                .projectName(conv.getProject() != null ? conv.getProject().getName() : null)
                .projectPath(conv.getProject() != null ? conv.getProject().getPath() : null)
                .createdAt(conv.getCreatedAt())
                .updatedAt(conv.getUpdatedAt())
                .messages(messages)
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
