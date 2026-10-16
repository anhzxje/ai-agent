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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private record PreparedChatContext(
            Long convId,
            String chatModel,
            boolean hasProject,
            String projectPath,
            String systemPrompt,
            String promptText
    ) {}

    private <T> T runInTransaction(java.util.function.Supplier<T> action) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> action.get());
        }
        return action.get();
    }

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
     * Cập nhật mô hình AI cho một cuộc hội thoại cụ thể.
     */
    @Transactional
    public ChatConversationResponse updateConversationModel(Long convId, String model, User user) {
        ChatConversation conv = findConversationAndVerifyOwner(convId, user);
        if (model != null && !model.isBlank()) {
            String trimmed = model.trim();
            List<String> allowed = systemConfigService.getAllowedModels();
            if (!allowed.contains(trimmed)) {
                throw new IllegalArgumentException("Model '" + trimmed + "' không nằm trong danh sách model được phép: " + allowed);
            }
            conv.setModel(trimmed);
            ChatConversation saved = conversationRepository.save(conv);
            return toConversationResponse(saved);
        }
        return toConversationResponse(conv);
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
     * Thiết kế tách giao dịch (Decoupled Transaction):
     * 1. Giai đoạn 1 (DB Transaction): Tạo/tìm conversation, lưu tin nhắn USER vào DB, tải ngữ cảnh, commit ngay.
     * 2. Giai đoạn 2 (Non-Transactional I/O): Gọi API Google Gemini + Tool Calling (5-40s) NGOÀI transaction.
     *    -> Hoàn toàn KHÔNG giữ kết nối CSDL MySQL (Connection Pool), chống cạn kiệt tài nguyên HikariCP.
     * 3. Giai đoạn 3 (DB Transaction): Lưu tin nhắn AGENT, cập nhật updatedAt và ghi Audit Log.
     */
    public ChatMessageResponse sendMessage(ChatMessageRequest request, User user) {
        // Giai đoạn 1: Chuẩn bị ngữ cảnh và lưu tin nhắn USER trong 1 transaction ngắn
        PreparedChatContext payload = runInTransaction(() -> prepareUserChatContext(request, user));

        // Giai đoạn 2: Gọi Google Gemini LLM + Tool Calling hoàn toàn NGOÀI transaction
        String aiResponseText = callAiModel(payload, user.getUsername());

        // Giai đoạn 3: Lưu tin nhắn AGENT và cập nhật mốc thời gian trong transaction ngắn thứ hai
        final String finalResponse = aiResponseText;
        return runInTransaction(() -> saveAgentResponse(payload.convId(), finalResponse, user.getUsername()));
    }

    /**
     * Bước 1-4 trong transaction: Tìm/tạo conversation, nạp lịch sử đa lượt, lưu tin nhắn USER và chuẩn bị payload.
     */
    private PreparedChatContext prepareUserChatContext(ChatMessageRequest request, User user) {
        ChatConversation conv;
        List<String> allowed = systemConfigService.getAllowedModels();

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
            String initialTitle = generateConversationTitle(request.getMessage(), proj);

            String initModel;
            if (request.getModel() != null && !request.getModel().isBlank() && allowed.contains(request.getModel().trim())) {
                initModel = request.getModel().trim();
            } else {
                initModel = systemConfigService.getActiveModel();
            }

            conv = conversationRepository.save(ChatConversation.builder()
                    .user(user)
                    .project(proj)
                    .title(initialTitle)
                    .model(initModel)
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

        // Bước 5: Xác định model hiệu lực
        String chatModel;
        if (request.getModel() != null && !request.getModel().isBlank()) {
            String requestedModel = request.getModel().trim();
            if (allowed.contains(requestedModel)) {
                chatModel = requestedModel;
                if (!requestedModel.equals(conv.getModel())) {
                    conv.setModel(requestedModel);
                    conversationRepository.save(conv);
                }
            } else {
                chatModel = (conv.getModel() != null && !conv.getModel().isBlank() && allowed.contains(conv.getModel()))
                        ? conv.getModel()
                        : systemConfigService.getActiveModel();
            }
        } else {
            chatModel = (conv.getModel() != null && !conv.getModel().isBlank())
                    ? conv.getModel()
                    : systemConfigService.getActiveModel();
            // Auto-fallback: nếu model đã bị Google ngừng hỗ trợ (không còn trong danh sách allowed), chuyển sang active model
            if (!allowed.contains(chatModel)) {
                String fallbackModel = systemConfigService.getActiveModel();
                log.warn("Model '{}' không còn được hỗ trợ, tự động chuyển sang '{}'", chatModel, fallbackModel);
                chatModel = fallbackModel;
                conv.setModel(fallbackModel);
                conversationRepository.save(conv);
            }
        }

        String systemPrompt = buildSystemPrompt(conv.getProject());
        boolean hasProject = conv.getProject() != null;
        String projectPath = (hasProject && conv.getProject() != null) ? conv.getProject().getPath() : null;

        return new PreparedChatContext(conv.getId(), chatModel, hasProject, projectPath, systemPrompt, promptWithContext.toString());
    }

    /**
     * Gọi Google Gemini AI hoàn toàn ngoài transaction để không giữ kết nối Database.
     */
    private String callAiModel(PreparedChatContext payload, String username) {
        String aiResponseText;
        try {
            ChatClient.Builder effectiveBuilder = null;
            if (systemConfigService != null) {
                try {
                    effectiveBuilder = systemConfigService.getChatClientBuilder(chatClientBuilder);
                } catch (Exception e) {
                    log.warn("Lỗi khi lấy ChatClient.Builder từ SystemConfigService: {}", e.getMessage());
                }
            }
            if (effectiveBuilder == null) {
                effectiveBuilder = chatClientBuilder;
            }

            var promptSpec = effectiveBuilder.build()
                    .prompt()
                    .system(payload.systemPrompt())
                    .user(payload.promptText());

            // Gắn Tool Calling nếu hội thoại có project context
            if (payload.hasProject() && toolManagerService != null) {
                List<Object> dynamicTools = toolManagerService.resolveActiveTools(null, payload.projectPath());
                if (!dynamicTools.isEmpty()) {
                    promptSpec.tools(dynamicTools.toArray());
                }
            }

            var optionsBuilder = GoogleGenAiChatOptions.builder()
                    .safetySettings(safetySettings);
            if (payload.chatModel() != null && !payload.chatModel().isBlank()) {
                optionsBuilder.model(payload.chatModel());
            }
            promptSpec.options(optionsBuilder);

            aiResponseText = promptSpec.call().content();

            if (aiResponseText == null || aiResponseText.isBlank()) {
                aiResponseText = "Xin lỗi, tôi không thể tạo câu trả lời vào lúc này. Vui lòng thử lại.";
            }
        } catch (Exception e) {
            log.error("Lỗi khi gọi AI Chat cho user {}: {}", username, e.getMessage(), e);
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            String detailMsg = root.getMessage() != null ? root.getMessage() : e.getMessage();
            aiResponseText = "⚠️ **Đã xảy ra lỗi khi trao đổi với AI Agent:**\n\n" + detailMsg + "\n\n*Gợi ý: Nếu gặp lỗi Quota/Rate limit (429) hoặc tạm thời quá tải, vui lòng chuyển đổi model khác hoặc thử lại sau giây lát.*";
        }
        return aiResponseText;
    }

    /**
     * Bước 6-8 trong transaction thứ hai: Lưu tin nhắn AGENT, cập nhật mốc thời gian và ghi nhật ký Audit Log.
     */
    private ChatMessageResponse saveAgentResponse(Long convId, String aiResponseText, String username) {
        ChatConversation conv = conversationRepository.findById(convId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hội thoại với id: " + convId));

        ChatMessage agentMsg = ChatMessage.builder()
                .conversation(conv)
                .sender("AGENT")
                .content(aiResponseText)
                .createdAt(LocalDateTime.now())
                .build();
        ChatMessage savedAgentMsg = messageRepository.save(agentMsg);

        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conv);

        auditLogService.log(username, "CHAT_MESSAGE", "Trao đổi với AI trong hội thoại #" + conv.getId());
        return toMessageResponse(savedAgentMsg);
    }

    /**
     * Xây dựng System Prompt định hướng cho AI, kèm thông tin chi tiết về dự án nếu có.
     * Cung cấp các tính năng chuyên sâu chuẩn Antigravity:
     * - /review: Rà soát toàn diện mã nguồn, tìm lỗi logic, bảo mật, hiệu năng
     * - /fix: Chẩn đoán lỗi, đề xuất bản vá và tạo mã sửa hoàn chỉnh
     * - /explain: Giải thích cấu trúc kiến trúc và luồng dữ liệu của codebase
     * - /test: Tự động sinh mã kiểm thử đơn vị (Unit Tests)
     */
    private String buildSystemPrompt(Project project) {
        if (project == null) {
            return CHAT_SYSTEM_PROMPT;
        }
        return CHAT_SYSTEM_PROMPT + """

                --- DỰ ÁN MÃ NGUỒN HIỆN TẠI (PROJECT WORKSPACE) ---
                - Tên dự án: %s
                - Thư mục gốc: %s
                - Ngôn ngữ chính: %s
                - Mô tả: %s

                --- NGUYÊN TẮC HOẠT ĐỘNG & CÔNG CỤ (TOOLS) ---
                1. Bạn có các công cụ duyệt tệp (list_directory, glob, file_system, grep) để thao tác trực tiếp trên thư mục dự án `%s`.
                2. Bạn PHẢI LUÔN chủ động dùng các công cụ này để đọc nội dung file mã nguồn thực tế trước khi đưa ra nhận định, giải thích hay sửa code.
                3. Tuyệt đối KHÔNG đoán mò hoặc bịa đặt mã nguồn không tồn tại. Mọi câu trả lời kỹ thuật phải bám sát code thật.
                4. Chỉ đọc và thao tác trong phạm vi thư mục gốc của dự án. Không truy cập các thư mục ngoài dự án.

                --- CÁC TÍNH NĂNG CHUYÊN SÂU CHUẨN ANTIGRAVITY ---
                Khi người dùng sử dụng các lệnh Slash Command hoặc đưa ra yêu cầu tương ứng, hãy thực thi như sau:

                1. **`/review` [trọng tâm tùy chọn: security, performance, bugs, code_quality, all]**
                   - Khám phá cấu trúc dự án, đọc các file cấu hình và lớp xử lý cốt lõi.
                   - Trả về báo cáo Code Review chuyên nghiệp bằng tiếng Việt dạng Markdown gồm:
                     * **Tổng quan (Summary):** Đánh giá chung về chất lượng mã nguồn.
                     * **Vấn đề phát hiện (Issues):** Liệt kê theo mức độ (CRITICAL / HIGH / MEDIUM / LOW), chỉ rõ tệp tin, phân tích lỗi cụ thể và khuyến nghị sửa.
                     * **Đề xuất cải tiến (Suggestions):** Các tối ưu kiến trúc và hiệu năng tiếp theo.

                2. **`/fix` [mô tả lỗi hoặc tệp cần sửa]**
                   - Đọc file mã nguồn bị lỗi, phân tích nguyên nhân gốc rễ (Root Cause).
                   - Đưa ra giải pháp sửa lỗi rõ ràng và cung cấp đoạn code hoàn chỉnh (hoặc code diff) đã được khắc phục, có thể copy & áp dụng ngay.

                3. **`/explain` [tên file, method hoặc tính năng]**
                   - Đọc các tệp mã nguồn liên quan.
                   - Giải thích rõ ràng kiến trúc, luồng thực thi (execution flow), vai trò của các lớp/hàm và mẫu thiết kế (design patterns) được sử dụng.

                4. **`/test` [tên class hoặc chức năng cần viết test]**
                   - Đọc mã nguồn của class/phương thức cần viết kiểm thử.
                   - Tự động sinh file kiểm thử đơn vị hoàn chỉnh (ví dụ: JUnit 5 + Mockito cho Java, Jest/Mocha cho JS/TS, pytest cho Python) với đầy đủ ca thành công, ca biên và ngoại lệ.

                --- ĐỊNH DẠNG ĐẦU RA ---
                - Trả về văn bản định dạng Rich Markdown thuần, thân thiện, rõ ràng và có cấu trúc mạch lạc.
                - Sử dụng khối mã nguồn chuẩn với ngôn ngữ tương ứng (ví dụ: ```java, ```sql, ```bash).
                - Không trả về JSON thô trừ khi người dùng yêu cầu rõ ràng.
                """.formatted(
                project.getName(),
                project.getPath() != null ? project.getPath() : "Không xác định",
                project.getLanguage() != null ? project.getLanguage() : "Đa ngôn ngữ",
                project.getDescription() != null ? project.getDescription() : "Không có mô tả",
                project.getPath() != null ? project.getPath() : "."
        );
    }

    /**
     * Tạo tiêu đề ngắn gọn, chuẩn mực cho cuộc hội thoại, loại bỏ các lệnh slash thô và định dạng markdown.
     */
    private String generateConversationTitle(String rawMessage, Project project) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return project != null ? "Hội thoại: " + project.getName() : "Hỏi đáp chung";
        }
        String text = rawMessage.trim();

        // 1. Nhận diện các lệnh Slash commands Antigravity
        if (text.startsWith("/review")) {
            String rest = text.substring(7).trim();
            if (rest.isBlank() || rest.toLowerCase().contains("toàn diện mã nguồn")) {
                return project != null ? "Code Review: " + project.getName() : "Code Review";
            }
            return "Review: " + cleanTextForTitle(rest, 22);
        } else if (text.startsWith("/fix")) {
            String rest = text.substring(4).trim();
            if (rest.isBlank() || rest.toLowerCase().contains("chẩn đoán") || rest.toLowerCase().contains("file:")) {
                return project != null ? "Sửa lỗi: " + project.getName() : "Sửa lỗi code";
            }
            return "Fix: " + cleanTextForTitle(rest, 24);
        } else if (text.startsWith("/explain")) {
            String rest = text.substring(8).trim();
            if (rest.isBlank() || rest.toLowerCase().contains("giải thích") || rest.toLowerCase().contains("file:")) {
                return project != null ? "Giải thích: " + project.getName() : "Giải thích code";
            }
            return "Explain: " + cleanTextForTitle(rest, 20);
        } else if (text.startsWith("/test")) {
            String rest = text.substring(5).trim();
            if (rest.isBlank() || rest.toLowerCase().contains("bộ unit tests") || rest.toLowerCase().contains("cho:")) {
                return project != null ? "Unit Test: " + project.getName() : "Sinh Unit Test";
            }
            return "Test: " + cleanTextForTitle(rest, 24);
        }

        return cleanTextForTitle(text, 28);
    }

    private String cleanTextForTitle(String text, int maxLength) {
        if (text == null) return "Hội thoại";
        // Bỏ ký tự markdown và khoảng trắng thừa, ngắt dòng
        String clean = text.replaceAll("[#*`_~>\\[\\]()\\n\\r]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (clean.isBlank()) return "Hội thoại";
        // Viết hoa chữ cái đầu
        clean = Character.toUpperCase(clean.charAt(0)) + (clean.length() > 1 ? clean.substring(1) : "");
        if (clean.length() <= maxLength) {
            return clean;
        }
        int cutIndex = clean.lastIndexOf(' ', maxLength);
        if (cutIndex > 10) {
            return clean.substring(0, cutIndex) + "...";
        }
        return clean.substring(0, maxLength) + "...";
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
