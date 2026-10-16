package prj.anhzxje.aiagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import prj.anhzxje.aiagent.dto.task.AgentTaskRequest;
import prj.anhzxje.aiagent.dto.task.AgentTaskResponse;
import prj.anhzxje.aiagent.entity.AgentTask;
import prj.anhzxje.aiagent.entity.Project;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.enums.JobStatus;
import prj.anhzxje.aiagent.enums.TaskType;
import prj.anhzxje.aiagent.exception.ResourceNotFoundException;
import prj.anhzxje.aiagent.repository.AgentTaskRepository;
import prj.anhzxje.aiagent.repository.ProjectRepository;
import prj.anhzxje.aiagent.security.CustomUserDetails;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AgentTaskService quản lý lifecycle của AgentTask (persistence layer).
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class AgentTaskService {

    private final AgentTaskRepository agentTaskRepository;
    private final ProjectRepository projectRepository;
    private final AgentTaskExecutor agentTaskExecutor;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /**
     * Constructor tiêu chuẩn (dùng trong Unit Tests hoặc khi không cần AuditLogService).
     *
     * @param agentTaskRepository repository quản lý entity AgentTask
     * @param projectRepository   repository quản lý entity Project
     * @param agentTaskExecutor   thành phần thực thi tác vụ bất đồng bộ
     */
    public AgentTaskService(AgentTaskRepository agentTaskRepository,
                            ProjectRepository projectRepository,
                            AgentTaskExecutor agentTaskExecutor) {
        this(agentTaskRepository, projectRepository, agentTaskExecutor, null, new ObjectMapper());
    }

    /**
     * Constructor đầy đủ được Spring Boot tiêm phụ thuộc tự động (@Autowired).
     *
     * @param agentTaskRepository repository quản lý entity AgentTask
     * @param projectRepository   repository quản lý entity Project
     * @param agentTaskExecutor   thành phần thực thi tác vụ bất đồng bộ
     * @param auditLogService     service ghi nhật ký kiểm toán hệ thống (tùy chọn)
     * @param objectMapper        bộ chuyển đổi Jackson ObjectMapper (tùy chọn)
     */
    @org.springframework.beans.factory.annotation.Autowired
    public AgentTaskService(AgentTaskRepository agentTaskRepository,
                            ProjectRepository projectRepository,
                            AgentTaskExecutor agentTaskExecutor,
                            @org.springframework.beans.factory.annotation.Autowired(required = false) AuditLogService auditLogService,
                            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper) {
        this.agentTaskRepository = agentTaskRepository;
        this.projectRepository = projectRepository;
        this.agentTaskExecutor = agentTaskExecutor;
        this.auditLogService = auditLogService;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
    }

    /**
     * Tạo task mới (trạng thái PENDING) và kích hoạt AI Agent chạy ngầm trên Thread riêng.
     */
    @Transactional
    public AgentTaskResponse createTask(AgentTaskRequest request) {
        // Bước 1: Lấy thông tin người dùng đang thực hiện request từ Spring Security Context
        User currentUser = getCurrentUser();

        // Bước 2: Kiểm tra sự tồn tại của dự án trong CSDL
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy project với id: " + request.getProjectId()));

        // Bước 3: Kiểm tra tính toàn vẹn quyền sở hữu (Data Isolation):
        // Người dùng chỉ được phép giao việc cho Agent trên các dự án do chính mình sở hữu.
        if (!project.getUser().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException(
                    "Không tìm thấy project với id: " + request.getProjectId());
        }

        // Bước 4: Chuẩn hóa và gộp các trường cấu hình (selectedTools, model, prompt)
        // thành một đối tượng JSON thống nhất để lưu vào cột input của bảng agent_tasks.
        String finalInput = request.getInput();
        try {
            ObjectNode objectNode;
            if (finalInput != null && !finalInput.isBlank()) {
                var tree = objectMapper.readTree(finalInput);
                objectNode = tree.isObject() ? (ObjectNode) tree : objectMapper.createObjectNode();
            } else {
                objectNode = objectMapper.createObjectNode();
            }

            // Gắn danh sách công cụ được phép sử dụng vào JSON
            if (request.getSelectedTools() != null && !request.getSelectedTools().isEmpty()) {
                var toolsArr = objectNode.putArray("selectedTools");
                for (String t : request.getSelectedTools()) {
                    toolsArr.add(t);
                }
            }
            // Gắn tên model chỉ định vào JSON
            if (request.getModel() != null && !request.getModel().isBlank()) {
                objectNode.put("model", request.getModel().trim());
            }
            // Gắn prompt bổ sung vào JSON
            if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
                objectNode.put("prompt", request.getPrompt().trim());
            }
            finalInput = objectMapper.writeValueAsString(objectNode);
        } catch (Exception e) {
            log.warn("Không thể chuẩn hóa input JSON: {}", e.getMessage());
        }

        // Bước 5: Khởi tạo bản ghi AgentTask ở trạng thái chờ xử lý (PENDING)
        AgentTask task = AgentTask.builder()
                .user(currentUser)
                .project(project)
                .type(request.getType())
                .status(JobStatus.PENDING)
                .input(finalInput)
                .build();

        AgentTask saved = agentTaskRepository.save(task);
        log.info("Đã tạo AgentTask [id={}, type={}, project={}]",
                saved.getId(), saved.getType(), project.getName());

        // Bước 6: Ghi nhật ký kiểm toán hành vi tạo tác vụ
        if (auditLogService != null) {
            auditLogService.log(currentUser.getUsername(), "CREATE_TASK",
                    "Khởi tạo task #" + saved.getId() + " [type=" + saved.getType() + ", project=" + project.getName() + "]");
        }

        // Bước 7: Kích hoạt Agent xử lý ngầm (True Async via AgentTaskExecutor Spring Bean)
        // Chống race condition: Đảm bảo transaction đã commit xuống CSDL trước khi thread ngầm truy vấn findById
        final String effectiveInput = saved.getInput();
        final TaskType effectiveType = saved.getType();
        final String effectiveProjectPath = project.getPath();
        final Long effectiveTaskId = saved.getId();

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    agentTaskExecutor.executeTaskAsync(effectiveTaskId, effectiveProjectPath, effectiveInput, effectiveType);
                }
            });
        } else {
            agentTaskExecutor.executeTaskAsync(effectiveTaskId, effectiveProjectPath, effectiveInput, effectiveType);
        }

        // Bước 8: Chuyển đổi sang Response DTO và trả về ngay lập tức cho Client
        return toResponse(saved);
    }

    /**
     * Hủy task của chính mình khi đang ở trạng thái PENDING hoặc PROCESSING.
     */
    @Transactional
    public AgentTaskResponse cancelTask(Long taskId) {
        // Bước 1: Tìm task và xác thực quyền sở hữu
        AgentTask task = findTaskAndVerifyOwner(taskId);

        // Bước 2: Kiểm tra ràng buộc trạng thái: không thể hủy nếu task đã hoàn thành hoặc đã lỗi
        if (task.getStatus() == JobStatus.COMPLETED || task.getStatus() == JobStatus.ERROR) {
            throw new IllegalStateException("Không thể hủy tác vụ đã ở trạng thái: " + task.getStatus());
        }

        // Bước 3: Đánh dấu trạng thái CANCELLED và lưu thời điểm hủy
        task.setStatus(JobStatus.CANCELLED);
        task.setCompletedAt(LocalDateTime.now());
        task.setErrorMessage("Tác vụ đã bị người dùng hủy theo yêu cầu.");
        AgentTask saved = agentTaskRepository.save(task);

        // Bước 4: Ghi nhật ký kiểm toán hành vi hủy
        if (auditLogService != null) {
            auditLogService.log(getCurrentUser().getUsername(), "CANCEL_TASK", "Đã hủy tác vụ #" + taskId);
        }
        log.info("User {} đã hủy task #{}", getCurrentUser().getUsername(), taskId);
        return toResponse(saved);
    }

    /**
     * Xóa 1 task của chính mình.
     */
    @Transactional
    public void deleteTask(Long taskId) {
        AgentTask task = findTaskAndVerifyOwner(taskId);
        agentTaskRepository.delete(task);
        if (auditLogService != null) {
            auditLogService.log(getCurrentUser().getUsername(), "DELETE_TASK", "Đã xóa tác vụ #" + taskId);
        }
        log.info("User {} đã xóa task #{}", getCurrentUser().getUsername(), taskId);
    }

    /**
     * Xóa toàn bộ lịch sử task của chính mình.
     */
    @Transactional
    public void deleteAllMyTasks() {
        User currentUser = getCurrentUser();
        List<AgentTask> myTasks = agentTaskRepository.findByUserId(currentUser.getId());
        if (!myTasks.isEmpty()) {
            agentTaskRepository.deleteAll(myTasks);
        }
        if (auditLogService != null) {
            auditLogService.log(currentUser.getUsername(), "DELETE_ALL_TASKS", "Đã xóa toàn bộ lịch sử tác vụ (" + myTasks.size() + " tasks)");
        }
        log.info("User {} đã xóa toàn bộ lịch sử task ({})", currentUser.getUsername(), myTasks.size());
    }

    /**
     * Chạy lại task cũ (retry).
     */
    @Transactional
    public AgentTaskResponse retryTask(Long taskId) {
        AgentTask task = findTaskAndVerifyOwner(taskId);
        task.setStatus(JobStatus.PENDING);
        task.setOutput(null);
        task.setErrorMessage(null);
        task.setCompletedAt(null);
        AgentTask saved = agentTaskRepository.save(task);

        if (auditLogService != null) {
            auditLogService.log(getCurrentUser().getUsername(), "RETRY_TASK", "Thực thi lại tác vụ #" + taskId);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    agentTaskExecutor.executeTaskAsync(saved.getId(), saved.getProject().getPath(), saved.getInput(), saved.getType());
                }
            });
        } else {
            agentTaskExecutor.executeTaskAsync(saved.getId(), saved.getProject().getPath(), saved.getInput(), saved.getType());
        }
        return toResponse(saved);
    }



    /**
     * Lấy chi tiết task (kiểm tra quyền sở hữu).
     */
    public AgentTaskResponse getTask(Long taskId) {
        AgentTask task = findTaskAndVerifyOwner(taskId);
        return toResponse(task);
    }

    /**
     * Lấy tất cả task của user hiện tại (mới nhất lên đầu).
     */
    public List<AgentTaskResponse> getMyTasks() {
        User currentUser = getCurrentUser();
        return agentTaskRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Ghim / Bỏ ghim task (PINNED).
     */
    @Transactional
    public AgentTaskResponse togglePin(Long taskId) {
        AgentTask task = findTaskAndVerifyOwner(taskId);
        task.setIsPinned(!Boolean.TRUE.equals(task.getIsPinned()));
        AgentTask updated = agentTaskRepository.save(task);
        log.info("User {} đã {} task #{}", getCurrentUser().getUsername(),
                Boolean.TRUE.equals(updated.getIsPinned()) ? "ghim" : "bỏ ghim", taskId);
        return toResponse(updated);
    }

    /**
     * Lấy tất cả task thuộc một project.
     */
    public List<AgentTaskResponse> getTasksByProject(Long projectId) {
        User currentUser = getCurrentUser();

        // Kiểm tra project tồn tại và thuộc về user
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy project với id: " + projectId));

        if (!project.getUser().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException(
                    "Không tìm thấy project với id: " + projectId);
        }

        return agentTaskRepository.findByProjectId(projectId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }


    // ────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────

    /**
     * Tra cứu tác vụ theo ID và xác minh quyền sở hữu của người dùng hiện tại.
     * Chống tấn công IDOR: ném ngoại lệ ResourceNotFoundException nếu task không tồn tại hoặc thuộc người khác.
     *
     * @param taskId mã định danh tác vụ
     * @return thực thể AgentTask nếu hợp lệ
     * @throws ResourceNotFoundException nếu không tìm thấy hoặc không thuộc quyền sở hữu
     */
    private AgentTask findTaskAndVerifyOwner(Long taskId) {
        User currentUser = getCurrentUser();
        AgentTask task = agentTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy task với id: " + taskId));

        if (!task.getUser().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException(
                    "Không tìm thấy task với id: " + taskId);
        }
        return task;
    }

    /**
     * Lấy thông tin thực thể User đang đăng nhập từ SecurityContextHolder của Spring Security.
     *
     * @return thực thể User hiện tại
     */
    private User getCurrentUser() {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return userDetails.getUser();
    }

    /**
     * Chuyển đổi thực thể AgentTask sang DTO AgentTaskResponse để trả về cho Client.
     *
     * @param task thực thể AgentTask nguồn
     * @return AgentTaskResponse DTO
     */
    private AgentTaskResponse toResponse(AgentTask task) {
        return AgentTaskResponse.builder()
                .id(task.getId())
                .projectId(task.getProject() != null ? task.getProject().getId() : null)
                .projectName(task.getProject() != null ? task.getProject().getName() : null)
                .type(task.getType())
                .status(task.getStatus())
                .input(task.getInput())
                .output(task.getOutput())
                .errorMessage(task.getErrorMessage())
                .createdAt(task.getCreatedAt())
                .completedAt(task.getCompletedAt())
                .isPinned(Boolean.TRUE.equals(task.getIsPinned()))
                .build();
    }
}
