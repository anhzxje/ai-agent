package prj.anhzxje.aiagent.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting.HarmCategory;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting.HarmBlockThreshold;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting.HarmBlockMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import prj.anhzxje.aiagent.service.SystemConfigService;
import prj.anhzxje.aiagent.service.ToolManagerService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * AI Agent thực hiện code review bằng cách tự khám phá codebase.
 * Trách nhiệm: CHỈ lo reasoning + tool calling + prompt.
 */
@Slf4j
@Component
public class CodeReviewAgent {

    private final ChatClient chatClient;
    private final ChatClient.Builder chatClientBuilder;

    /**
     * Safety settings cho Gemini — hạ threshold để model không từ chối
     * các yêu cầu code review hợp lệ.
     */
    private final List<GoogleGenAiSafetySetting> safetySettings = List.of(
            new GoogleGenAiSafetySetting(
                    HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT,
                    HarmBlockThreshold.BLOCK_ONLY_HIGH,
                    HarmBlockMethod.PROBABILITY
            ),
            new GoogleGenAiSafetySetting(
                    HarmCategory.HARM_CATEGORY_HARASSMENT,
                    HarmBlockThreshold.BLOCK_ONLY_HIGH,
                    HarmBlockMethod.PROBABILITY
            ),
            new GoogleGenAiSafetySetting(
                    HarmCategory.HARM_CATEGORY_HATE_SPEECH,
                    HarmBlockThreshold.BLOCK_ONLY_HIGH,
                    HarmBlockMethod.PROBABILITY
            ),
            new GoogleGenAiSafetySetting(
                    HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT,
                    HarmBlockThreshold.BLOCK_ONLY_HIGH,
                    HarmBlockMethod.PROBABILITY
            )
    );

    private final ToolManagerService toolManagerService;
    private final SystemConfigService systemConfigService;

    /**
     * Constructor tiêu chuẩn (dùng trong Unit Tests hoặc môi trường rút gọn).
     *
     * @param chatClientBuilder builder để khởi tạo Spring AI ChatClient
     */
    public CodeReviewAgent(ChatClient.Builder chatClientBuilder) {
        this(chatClientBuilder, null, null);
    }

    /**
     * Constructor đầy đủ được Spring Boot tiêm phụ thuộc (@Autowired).
     * Khởi tạo ChatClient với System Prompt rà soát mã nguồn và cấu hình Safety Settings tối ưu.
     *
     * @param chatClientBuilder   builder để khởi tạo Spring AI ChatClient
     * @param toolManagerService  service quản lý công cụ duyệt file động
     * @param systemConfigService service cấu hình hệ thống (model AI kích hoạt)
     */
    @org.springframework.beans.factory.annotation.Autowired
    public CodeReviewAgent(ChatClient.Builder chatClientBuilder,
                           @org.springframework.lang.Nullable ToolManagerService toolManagerService,
                           @org.springframework.lang.Nullable SystemConfigService systemConfigService) {
        this.chatClientBuilder = chatClientBuilder;
        this.toolManagerService = toolManagerService;
        this.systemConfigService = systemConfigService;

        // Cấu hình safety settings — hạ threshold để Gemini không từ chối code review
        var optionsBuilder = GoogleGenAiChatOptions.builder()
                .safetySettings(safetySettings);

        this.chatClient = chatClientBuilder
                .defaultSystem(loadSystemPrompt())
                .defaultOptions(optionsBuilder)
                .build();
    }

    /**
     * Review codebase tại projectPath.
     *
     * @param projectPath đường dẫn tuyệt đối tới project cần review
     * @param focus       (nullable) lĩnh vực tập trung
     * @return kết quả review thô dạng JSON string từ LLM
     */
    public String reviewPath(String projectPath, String focus) {
        return reviewPath(projectPath, focus, null, null);
    }

    /**
     * Phương thức điều phối chính để Tác tử AI tiến hành rà soát mã nguồn:
     * - projectPath: Thư mục gốc của dự án cần quét
     * - focus: Trọng tâm rà soát (security, performance, bug, code_quality, all)
     * - requestedTools: Danh sách công cụ người dùng cho phép gọi
     * - modelName: Tên model AI mong muốn (nếu để trống sẽ dùng model mặc định)
     */
    public String reviewPath(String projectPath, String focus, List<String> requestedTools, String modelName) {

        // ====================================================================
        // BƯỚC 1: XÂY DỰNG CHỈ DẪN TRỌNG TÂM REVIEW (FOCUS INSTRUCTION)
        // Dựa vào tham số focus của người dùng, tạo prompt chuyên biệt để định
        // hướng mô hình AI tập trung kiểm tra các khía cạnh kỹ thuật cụ thể.
        // ====================================================================
        String focusInstruction;

        if (focus != null && !focus.isBlank()) {
            focusInstruction = switch (focus.toLowerCase()) {
                case "security" ->
                        """
                        Focus on reviewing code practices related to:
                        authentication flow, authorization checks, token handling,
                        sensitive data in responses, endpoint access control,
                        Spring configuration best practices.
                        """;

                case "performance" ->
                        """
                        Focus on reviewing code for performance:
                        database access patterns, N+1 queries,
                        unnecessary processing, memory usage.
                        """;

                case "bug" ->
                        """
                        Focus on finding logic errors:
                        null handling, exception handling,
                        incorrect behavior, edge cases.
                        """;

                case "code_quality" ->
                        """
                        Focus on code quality:
                        readability, maintainability, duplication,
                        naming, structure and design patterns.
                        """;

                default ->
                        "Focus on: " + focus + ".";
            };
        } else {
            // Mặc định rà soát toàn diện mọi khía cạnh nếu người dùng không chọn cụ thể
            focusInstruction = """
            Perform a comprehensive code review covering:
            bugs, best practices, performance and code quality.
            """;
        }

        // ====================================================================
        // BƯỚC 2: TẠO PROMPT USER HOÀN CHỈNH KÈM QUY TẮC RÀNG BUỘC ĐẦU RA
        // Yêu cầu bắt buộc AI phải tự dùng tool đọc mã nguồn thực tế, không bịa đặt,
        // và trả về duy nhất định dạng JSON thuần không bọc Markdown bằng tiếng Việt.
        // ====================================================================
        String prompt = """
                Review the source code of the project located at:
                %s

                %s

                IMPORTANT INSTRUCTIONS:
                - You MUST use your file system tools to read actual source code files.
                - Start by listing the directory structure.
                - Then read the key source files.
                - Use grep to search for patterns across the codebase.
                - Base your findings ONLY on code you have actually read.
                - Do not guess or invent code that you have not read.
                - Only report issues that have concrete evidence in the source code.
                - ALL textual content in the JSON (summary, description, recommendation, suggestions) MUST be written in Vietnamese language.

                Return ONLY a valid JSON object.
                Do NOT use markdown code blocks.
                Do NOT add any text before or after the JSON.

                The JSON MUST follow exactly this structure:

                {
                  "summary": "Tổng quan ngắn gọn bằng tiếng Việt",
                  "issues": [
                    {
                      "severity": "HIGH | MEDIUM | LOW",
                      "category": "BUG | SECURITY | PERFORMANCE | CODE_QUALITY",
                      "file": "relative file path",
                      "description": "Mô tả chi tiết bằng tiếng Việt",
                      "recommendation": "Đề xuất sửa lỗi bằng tiếng Việt"
                    }
                  ],
                  "suggestions": [
                    "Gợi ý cải thiện 1 bằng tiếng Việt",
                    "Gợi ý cải thiện 2 bằng tiếng Việt"
                  ]
                }

                If no significant issues are found, return an empty issues array.
                """.formatted(projectPath, focusInstruction);

        ChatClient activeChatClient = this.chatClient;
        if (systemConfigService != null && systemConfigService.hasCustomApiKey()) {
            activeChatClient = systemConfigService.getChatClientBuilder(chatClientBuilder)
                    .defaultSystem(loadSystemPrompt())
                    .build();
        }

        var promptSpec = activeChatClient.prompt().user(prompt);

        // ====================================================================
        // BƯỚC 3: PHÂN GIẢI VÀ GẮN CÔNG CỤ THỰC TẾ (DYNAMIC TOOL CALLING)
        // Lấy danh sách công cụ đang được kích hoạt bởi Admin và giao thoa với
        // danh sách công cụ mà người dùng cấp phép khi tạo tác vụ.
        // Ràng buộc sandbox theo projectPath.
        // ====================================================================
        if (toolManagerService != null) {
            List<Object> dynamicTools = toolManagerService.resolveActiveTools(requestedTools, projectPath);
            if (!dynamicTools.isEmpty()) {
                promptSpec.tools(dynamicTools.toArray());
            }
        }

        // ====================================================================
        // BƯỚC 4: XÁC ĐỊNH MÔ HÌNH GEMINI HIỆU LỰC
        // Ưu tiên 1: Model người dùng chọn tại form
        // Ưu tiên 2: Model Quản trị viên kích hoạt trong CSDL
        // Ưu tiên 3: Model mặc định hệ thống (gemini-3.5-flash-lite)
        // ====================================================================
        String effectiveModel = (modelName != null && !modelName.isBlank())
                ? modelName.trim()
                : (systemConfigService != null ? systemConfigService.getActiveModel() : "gemini-3.5-flash-lite");

        if (effectiveModel == null || effectiveModel.isBlank()) {
            effectiveModel = "gemini-3.5-flash-lite";
        }

        // Auto-fallback: chuyển sang active model nếu model được chọn đã bị Google ngừng hỗ trợ
        if (systemConfigService != null) {
            List<String> allowed = systemConfigService.getAllowedModels();
            if (!allowed.contains(effectiveModel)) {
                String fallback = systemConfigService.getActiveModel();
                log.warn("Model '{}' không còn được hỗ trợ, chuyển sang '{}'", effectiveModel, fallback);
                effectiveModel = fallback;
            }
        }

        // Gắn cấu hình an toàn (Safety Settings) và tên Model vào Options của cuộc gọi
        promptSpec.options(GoogleGenAiChatOptions.builder()
                .safetySettings(safetySettings)
                .model(effectiveModel));

        // ====================================================================
        // BƯỚC 5: GỌI GOOGLE GEMINI VÀ XỬ LÝ LỖI HẠN MỨC (QUOTA/RATE LIMIT)
        // Bắt lỗi cụ thể nếu đạt giới hạn 429 hoặc Resource Exhausted để thông
        // báo rõ ràng cho người dùng chuyển đổi mô hình thay vì crash ngầm.
        // ====================================================================
        String result;
        try {
            result = promptSpec.call().content();
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            String err = root.getMessage() != null ? root.getMessage() : (e.getMessage() != null ? e.getMessage() : "");
            log.error("Lỗi khi gọi Gemini với model {}: {}", effectiveModel, err, e);
            if (err.contains("429") || err.contains("RESOURCE_EXHAUSTED") || err.contains("Quota exceeded") || err.contains("limit")) {
                throw new RuntimeException("Mô hình " + effectiveModel + " đã đạt giới hạn hạn mức (Rate limit/Quota). Vui lòng đổi sang mô hình khác hoặc thử lại sau vài giây. Chi tiết: " + err, e);
            }
            throw new RuntimeException("Lỗi từ Gemini AI (" + effectiveModel + "): " + err, e);
        }

        // ====================================================================
        // BƯỚC 6: LÀM SẠCH KẾT QUẢ VÀ TRẢ VỀ CHUỖI JSON HỢP LỆ
        // ====================================================================
        return cleanJsonResponse(result);
    }

    /**
     * Phương thức tiền xử lý chuỗi phản hồi từ LLM:
     * Loại bỏ các ký tự Markdown code fence (ví dụ: ```json ... ```) nếu mô hình AI
     * vô tình bọc kết quả, đảm bảo chuỗi trả về là một JSON thuần có thể parse được.
     */
    private String cleanJsonResponse(String response) {
        if (response == null) return null;
        String cleaned = response.trim();
        // Cắt bỏ tiền tố ```json hoặc ```
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        // Cắt bỏ hậu tố ```
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        // Bóc tách chính xác từ dấu '{' đầu tiên đến dấu '}' cuối cùng để loại bỏ văn bản dạo đầu/kết thúc
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return cleaned.substring(firstBrace, lastBrace + 1).trim();
        }

        return cleaned;
    }

    /**
     * Tải System Prompt từ tệp tài nguyên tĩnh `prompts/code-reviewer.md`.
     * System Prompt này thiết lập danh tính, nguyên tắc suy luận và hướng dẫn
     * công cụ cốt lõi cho Tác tử AI trước khi bắt đầu phiên làm việc.
     */
    private String loadSystemPrompt() {
        try {
            ClassPathResource resource =
                    new ClassPathResource("prompts/code-reviewer.md");

            return resource.getContentAsString(StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot load system prompt: prompts/code-reviewer.md",
                    e
            );
        }
    }
}