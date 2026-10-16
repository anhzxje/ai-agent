package prj.anhzxje.aiagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import prj.anhzxje.aiagent.agent.CodeReviewAgent;
import prj.anhzxje.aiagent.enums.JobStatus;
import prj.anhzxje.aiagent.enums.TaskType;
import prj.anhzxje.aiagent.repository.AgentTaskRepository;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service độc lập chịu trách nhiệm thực thi các AI Agent Task bất đồng bộ (True Async).
 * Sử dụng AgentTaskRepository trực tiếp để tránh phụ thuộc vòng (Circular Dependency).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskExecutor {

    private final CodeReviewAgent codeReviewAgent;
    private final AgentTaskRepository agentTaskRepository;
    private final ObjectMapper objectMapper;

    /**
     * Danh sách thư mục được phép truy cập — chống Path Traversal.
     * Cấu hình qua application.yml: agent.allowed-paths
     * Mặc định cho phép thư mục hiện tại nếu không cấu hình.
     */
    @Value("${agent.allowed-paths:#{T(java.util.Collections).emptyList()}}")
    private List<String> allowedPaths;

    /**
     * Phương thức thực thi bất đồng bộ Tác vụ AI trên luồng riêng (Worker Thread):
     * - @Async("agentThreadPoolExecutor"): Chạy trên Thread Pool riêng biệt đã cấu hình trong AgentConfig,
     *   giúp giải phóng ngay lập tức luồng HTTP Request để trả về mã 201 Created cho Client.
     * - taskId: ID tác vụ trong bảng agent_tasks
     * - projectPath: Đường dẫn thư mục mã nguồn cần phân tích
     * - inputJson: Tham số đầu vào dạng chuỗi JSON
     * - taskType: Loại tác vụ (CODE_REVIEW, CODE_FIX, TEST_GENERATION)
     */
    @Async("agentThreadPoolExecutor")
    public void executeTaskAsync(Long taskId, String projectPath, String inputJson, TaskType taskType) {
        log.info("Bắt đầu thực thi AgentTask [id={}, type={}] trên Thread ngầm...", taskId, taskType);

        // ====================================================================
        // BƯỚC 1: KIỂM TRA HỦY TÁC VỤ SỚM (EARLY CANCELLATION CHECK)
        // Nếu người dùng đã bấm nút 'Hủy tác vụ' trước khi Thread ngầm kịp nhặt
        // task từ hàng đợi (Queue), ta dừng ngay lập tức để tiết kiệm tài nguyên.
        // ====================================================================
        var currentOpt = agentTaskRepository.findById(taskId);
        if (currentOpt.isPresent() && currentOpt.get().getStatus() == JobStatus.CANCELLED) {
            log.info("AgentTask [id={}] đã bị hủy trước khi bắt đầu thực thi.", taskId);
            return;
        }

        // ====================================================================
        // BƯỚC 2: CẬP NHẬT TRẠNG THÁI TASK THÀNH 'PROCESSING'
        // Báo hiệu cho giao diện người dùng hiển thị biểu tượng đang tải (Loading Spinner).
        // ====================================================================
        updateStatus(taskId, JobStatus.PROCESSING);

        try {
            String focus = null;
            String model = null;
            List<String> selectedTools = null;

            // ================================================================
            // BƯỚC 3: PHÂN TÍCH THAM SỐ ĐẦU VÀO TỪ CHUỖI JSON (INPUT PARSING)
            // Trích xuất an toàn các trường: focus, model, selectedTools, projectPath.
            // ================================================================
            if (inputJson != null && !inputJson.isBlank()) {
                try {
                    JsonNode node = objectMapper.readTree(inputJson);
                    if (node.has("focus")) {
                        focus = node.get("focus").asText();
                    }
                    if (node.has("model") && !node.get("model").asText().isBlank()) {
                        model = node.get("model").asText();
                    }
                    // Đọc danh sách công cụ được chọn (hỗ trợ cả key 'selectedTools' và 'tools')
                    if (node.has("selectedTools") && node.get("selectedTools").isArray()) {
                        selectedTools = new java.util.ArrayList<>();
                        for (JsonNode tNode : node.get("selectedTools")) {
                            selectedTools.add(tNode.asText());
                        }
                    } else if (node.has("tools") && node.get("tools").isArray()) {
                        selectedTools = new java.util.ArrayList<>();
                        for (JsonNode tNode : node.get("tools")) {
                            selectedTools.add(tNode.asText());
                        }
                    }

                    // Kiểm tra an toàn: nếu inputJson có chỉ định projectPath riêng,
                    // bắt buộc đường dẫn đó phải nằm trong danh mục sandbox allowed-paths
                    // và phải thuộc phạm vi dự án hiện tại (chống IDOR / đọc lén dự án khác).
                    if (node.has("projectPath") && !node.get("projectPath").asText().isBlank()) {
                        String overridePath = node.get("projectPath").asText();
                        try {
                            Path normOverride = Path.of(overridePath).toAbsolutePath().normalize();
                            Path normBase = Path.of(projectPath).toAbsolutePath().normalize();
                            if ((normOverride.startsWith(normBase) || normOverride.equals(normBase)) && isPathAllowed(overridePath)) {
                                projectPath = overridePath;
                            } else {
                                log.warn("AgentTask [id={}]: projectPath từ input JSON bị từ chối (không thuộc dự án hoặc không nằm trong allowed-paths): {}",
                                        taskId, overridePath);
                            }
                        } catch (Exception e) {
                            log.warn("AgentTask [id={}]: Lỗi kiểm tra projectPath override: {}", taskId, e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.warn("AgentTask [id={}]: Không thể parse input JSON: {}", taskId, e.getMessage());
                }
            }

            // ================================================================
            // BƯỚC 4: KIỂM TRA BẢO MẬT ĐƯỜNG DẪN CUỐI CÙNG (SECURITY PATH VALIDATION)
            // Chống tấn công vượt thư mục (Path Traversal) trước khi trao quyền cho AI đọc file.
            // ================================================================
            if (projectPath == null || projectPath.isBlank()) {
                throw new IllegalArgumentException("projectPath không được để trống");
            }
            if (!isPathAllowed(projectPath)) {
                throw new SecurityException(
                        "projectPath không nằm trong danh sách thư mục được phép truy cập: " + projectPath);
            }

            // ================================================================
            // BƯỚC 5: ĐIỀU PHỐI ĐẾN AGENT PHÙ HỢP (DISPATCH TO AGENT)
            // Gọi CodeReviewAgent thực hiện suy luận, đọc code và sinh kết quả review.
            // ================================================================
            String result = dispatchTask(taskType, projectPath, focus, selectedTools, model);

            // ================================================================
            // BƯỚC 6: KIỂM TRA HỦY LẦN CUỐI (RACE CONDITION GUARD)
            // Đảm bảo không ghi đè kết quả nếu người dùng đã hủy task trong lúc LLM đang sinh text.
            // ================================================================
            var taskCheck = agentTaskRepository.findById(taskId);
            if (taskCheck.isPresent() && taskCheck.get().getStatus() == JobStatus.CANCELLED) {
                log.info("AgentTask [id={}] đã bị người dùng hủy trong khi đang chạy, không lưu kết quả.", taskId);
                return;
            }

            // ================================================================
            // BƯỚC 7: HOÀN TẤT TÁC VỤ VÀ LƯU KẾT QUẢ VÀO CSDL
            // ================================================================
            completeTask(taskId, result);
            log.info("AgentTask [id={}] đã hoàn thành thành công!", taskId);
        } catch (Exception e) {
            // ================================================================
            // BƯỚC 8: BẮT LỖI VÀ GHI NHẬN TRẠNG THÁI ERROR
            // Lưu nội dung lỗi cụ thể vào cột errorMessage để hiển thị trực quan trên giao diện.
            // ================================================================
            log.error("Lỗi khi thực thi AgentTask [id={}]: {}", taskId, e.getMessage(), e);
            failTask(taskId, e.getMessage());
        }
    }

    /**
     * Dispatch task tới agent phù hợp dựa trên TaskType.
     * Hiện tại CODE_REVIEW được hỗ trợ đầy đủ; các type khác sẽ log cảnh báo
     * và fallback sang code review (mở rộng trong tương lai).
     */
    private String dispatchTask(TaskType taskType, String projectPath, String focus, List<String> selectedTools, String model) {
        if ((selectedTools == null || selectedTools.isEmpty()) && (model == null || model.isBlank())) {
            return dispatchTask(taskType, projectPath, focus);
        }
        return switch (taskType) {
            case CODE_REVIEW -> codeReviewAgent.reviewPath(projectPath, focus, selectedTools, model);

            case CODE_FIX -> {
                log.warn("TaskType CODE_FIX chưa có agent riêng — fallback sang CODE_REVIEW");
                yield codeReviewAgent.reviewPath(projectPath, focus, selectedTools, model);
            }
            case CODE_EXPLAIN -> {
                log.warn("TaskType CODE_EXPLAIN chưa có agent riêng — fallback sang CODE_REVIEW");
                yield codeReviewAgent.reviewPath(projectPath, focus, selectedTools, model);
            }
            case TEST_GENERATION -> {
                log.warn("TaskType TEST_GENERATION chưa có agent riêng — fallback sang CODE_REVIEW");
                yield codeReviewAgent.reviewPath(projectPath, focus, selectedTools, model);
            }
        };
    }

    /**
     * Dispatch task tới agent với cấu hình cơ bản (không chỉ định custom tools hoặc custom model).
     *
     * @param taskType    loại tác vụ cần thực hiện (CODE_REVIEW, CODE_FIX, CODE_EXPLAIN, TEST_GENERATION)
     * @param projectPath đường dẫn thư mục dự án
     * @param focus       trọng tâm phân tích (security, performance, bug, code_quality, hoặc null)
     * @return kết quả trả về từ Agent dưới dạng chuỗi JSON
     */
    private String dispatchTask(TaskType taskType, String projectPath, String focus) {
        return switch (taskType) {
            case CODE_REVIEW -> codeReviewAgent.reviewPath(projectPath, focus);

            case CODE_FIX -> {
                log.warn("TaskType CODE_FIX chưa có agent riêng — fallback sang CODE_REVIEW");
                yield codeReviewAgent.reviewPath(projectPath, focus);
            }
            case CODE_EXPLAIN -> {
                log.warn("TaskType CODE_EXPLAIN chưa có agent riêng — fallback sang CODE_REVIEW");
                yield codeReviewAgent.reviewPath(projectPath, focus);
            }
            case TEST_GENERATION -> {
                log.warn("TaskType TEST_GENERATION chưa có agent riêng — fallback sang CODE_REVIEW");
                yield codeReviewAgent.reviewPath(projectPath, focus);
            }
        };
    }

    /**
     * Cơ chế Phòng thủ Chống Tấn công Vượt thư mục (Anti Path Traversal Sandbox):
     * 1. Chuyển đổi đường dẫn mục tiêu thành đường dẫn tuyệt đối chuẩn hóa (toAbsolutePath().normalize()).
     * 2. Rà soát xem sau khi normalize còn sót lại ký tự ".." hay không.
     * 3. So sánh tiền tố với danh mục allowedPaths được khai báo trong application.yml.
     *
     * @param pathStr đường dẫn cần kiểm tra
     * @return true nếu đường dẫn hợp lệ và nằm trọn trong thư mục được cấp phép
     */
    private boolean isPathAllowed(String pathStr) {
        // Nếu chưa cấu hình allowed-paths -> cho phép tất cả (chế độ dev / tương thích ngược)
        if (allowedPaths == null || allowedPaths.isEmpty()) {
            log.warn("agent.allowed-paths chưa được cấu hình — cho phép tất cả đường dẫn (KHÔNG AN TOÀN cho production!)");
            return true;
        }

        try {
            // Bước 1: Chuẩn hóa đường dẫn tuyệt đối (loại bỏ các ký tự tương đối như ./ hoặc ../)
            Path normalizedTarget = Path.of(pathStr).toAbsolutePath().normalize();

            // Bước 2: Phòng thủ chiều sâu — ngăn chặn triệt để nếu chuỗi vẫn còn chứa ".."
            if (normalizedTarget.toString().contains("..")) {
                return false;
            }

            // Bước 3: Kiểm tra xem đường dẫn mục tiêu có bắt đầu bằng một trong các thư mục được cấp phép hay không
            for (String allowed : allowedPaths) {
                Path normalizedAllowed = Path.of(allowed).toAbsolutePath().normalize();
                if (normalizedTarget.startsWith(normalizedAllowed)) {
                    return true;
                }
            }
        } catch (InvalidPathException e) {
            log.warn("Đường dẫn không hợp lệ hoặc chứa ký tự đặc biệt: {}", pathStr);
            return false;
        }

        return false;
    }

    /**
     * Cập nhật trạng thái tiến trình của tác vụ (PENDING -> PROCESSING).
     */
    private void updateStatus(Long taskId, JobStatus status) {
        agentTaskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(status);
            agentTaskRepository.save(task);
        });
    }

    /**
     * Ghi nhận tác vụ đã hoàn thành thành công:
     * - Lưu chuỗi kết quả JSON từ LLM vào cột output.
     * - Cập nhật status thành COMPLETED và ghi nhận mốc thời gian hoàn thành (completedAt).
     */
    private void completeTask(Long taskId, String outputJson) {
        agentTaskRepository.findById(taskId).ifPresent(task -> {
            // Nếu tác vụ đã bị hủy trước đó thì giữ nguyên trạng thái CANCELLED
            if (task.getStatus() == JobStatus.CANCELLED) {
                return;
            }
            task.setOutput(outputJson);
            task.setStatus(JobStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            agentTaskRepository.save(task);
        });
    }

    /**
     * Ghi nhận tác vụ thất bại:
     * - Lưu thông báo lỗi chi tiết vào cột errorMessage.
     * - Cập nhật status thành ERROR để người dùng có thể bấm 'Chạy lại' (Retry).
     */
    private void failTask(Long taskId, String errorMessage) {
        agentTaskRepository.findById(taskId).ifPresent(task -> {
            if (task.getStatus() == JobStatus.CANCELLED) {
                return;
            }
            task.setErrorMessage(errorMessage);
            task.setStatus(JobStatus.ERROR);
            task.setCompletedAt(LocalDateTime.now());
            agentTaskRepository.save(task);
        });
    }
}
