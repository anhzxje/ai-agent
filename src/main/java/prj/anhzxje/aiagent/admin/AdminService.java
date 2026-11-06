package prj.anhzxje.aiagent.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import prj.anhzxje.aiagent.tasks.TaskResponses;
import prj.anhzxje.aiagent.projects.ProjectResponses;
import prj.anhzxje.aiagent.chat.ChatResponses;
import prj.anhzxje.aiagent.chat.ChatConversationResponse;
import prj.anhzxje.aiagent.chat.ChatMessageResponse;
import prj.anhzxje.aiagent.projects.ProjectResponse;
import prj.anhzxje.aiagent.tasks.AgentTaskResponse;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.AgentTask;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.ChatConversation;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.ChatMessage;
import prj.anhzxje.aiagent.shared.enums.JobStatus;
import prj.anhzxje.aiagent.shared.enums.Role;
import prj.anhzxje.aiagent.shared.enums.TaskType;
import prj.anhzxje.aiagent.shared.exception.ResourceNotFoundException;
import prj.anhzxje.aiagent.infrastructure.persistence.repository.AgentTaskRepository;
import prj.anhzxje.aiagent.infrastructure.persistence.repository.ChatConversationRepository;
import prj.anhzxje.aiagent.infrastructure.persistence.repository.ChatMessageRepository;
import prj.anhzxje.aiagent.infrastructure.persistence.repository.ProjectRepository;
import prj.anhzxje.aiagent.infrastructure.persistence.repository.UserRepository;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.infrastructure.audit.AuditLogService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/** System-wide administrative queries and atomic moderation operations. */
@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final AgentTaskRepository agentTaskRepository;
    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        long totalUsers = userRepository.count();
        long totalProjects = projectRepository.count();
        long totalTasks = agentTaskRepository.count();

        long totalAdmins = userRepository.countByRole(Role.ADMIN);
        long totalRegularUsers = Math.max(0, totalUsers - totalAdmins);

        long completedTasks = agentTaskRepository.countByStatus(JobStatus.COMPLETED);
        long pendingOrProcessingTasks = agentTaskRepository.countByStatusIn(List.of(JobStatus.PENDING, JobStatus.PROCESSING));
        long failedTasks = agentTaskRepository.countByStatus(JobStatus.ERROR);

        long reviewTasksCount = agentTaskRepository.countByType(TaskType.CODE_REVIEW);
        long fixTasksCount = agentTaskRepository.countByType(TaskType.CODE_FIX);
        long testGenTasksCount = agentTaskRepository.countByType(TaskType.TEST_GENERATION);

        return AdminStatsResponse.builder()
                .totalUsers(totalUsers)
                .totalAdmins(totalAdmins)
                .totalRegularUsers(totalRegularUsers)
                .totalProjects(totalProjects)
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .pendingOrProcessingTasks(pendingOrProcessingTasks)
                .failedTasks(failedTasks)
                .reviewTasksCount(reviewTasksCount)
                .fixTasksCount(fixTasksCount)
                .testGenTasksCount(testGenTasksCount)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AdminTaskResponse> getAllTasks(
            JobStatus status,
            Long userId,
            Integer page,
            Integer size) {

        int pageNum = (page != null && page >= 0) ? page : 0;
        int pageSize = (size != null && size > 0 && size <= 200) ? size : (page != null ? 50 : 100);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                pageNum, pageSize, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        List<AgentTask> tasks;
        if (userId != null && status != null) {
            tasks = agentTaskRepository.findByUserIdAndStatus(userId, status, pageable).getContent();
        } else if (userId != null) {
            tasks = agentTaskRepository.findByUserId(userId, pageable).getContent();
        } else if (status != null) {
            tasks = agentTaskRepository.findByStatus(status, pageable).getContent();
        } else {
            tasks = agentTaskRepository.findAll(pageable).getContent();
        }

        List<AdminTaskResponse> res = tasks.stream()
                .map(this::toAdminTaskResponse)
                .collect(Collectors.toList());
        return res;
    }

    @Transactional(readOnly = true)
    public List<AdminTaskResponse> getErrorTasks() {
        List<AdminTaskResponse> errors = agentTaskRepository.findByErrorMessageIsNotNullOrderByCreatedAtDesc().stream()
                .map(this::toAdminTaskResponse)
                .collect(Collectors.toList());
        return errors;
    }

    @Transactional(readOnly = true)
    public List<AgentTaskResponse> getUserTasksForAdmin(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));

        List<AgentTaskResponse> tasks = agentTaskRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(TaskResponses::from)
                .collect(Collectors.toList());
        return tasks;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getUserProjectsForAdmin(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));

        List<ProjectResponse> projects = projectRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ProjectResponses::from)
                .collect(Collectors.toList());
        return projects;
    }

    @Transactional(readOnly = true)
    public List<ChatConversationResponse> getUserConversationsForAdmin(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId);
        }

        List<ChatConversationResponse> conversations = chatConversationRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(ChatResponses::conversation)
                .collect(Collectors.toList());
        return conversations;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getConversationMessagesForAdmin(Long conversationId) {
        if (!chatConversationRepository.existsById(conversationId)) {
            throw new ResourceNotFoundException("Không tìm thấy cuộc hội thoại với id: " + conversationId);
        }

        List<ChatMessageResponse> messages = chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());
        return messages;
    }

    @Transactional
    public Map<String, String> deleteConversationForAdmin(
            Long conversationId,
            CustomUserDetails adminUser) {
        ChatConversation conv = chatConversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cuộc hội thoại với id: " + conversationId));

        chatMessageRepository.deleteByConversationId(conversationId);
        chatConversationRepository.delete(conv);

        String adminUsername = adminUser != null ? adminUser.getUsername() : "ADMIN";
        auditLogService.log(adminUsername, "MODERATE_DELETE_CONVERSATION", "Admin xóa cuộc hội thoại vi phạm #" + conversationId);

        return Map.of("message", "Đã xóa cuộc hội thoại vi phạm #" + conversationId + " thành công");
    }

    @Transactional
    public Map<String, String> deleteMessageForAdmin(
            Long messageId,
            CustomUserDetails adminUser) {
        ChatMessage msg = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin nhắn với id: " + messageId));

        chatMessageRepository.delete(msg);

        String adminUsername = adminUser != null ? adminUser.getUsername() : "ADMIN";
        auditLogService.log(adminUsername, "MODERATE_DELETE_MESSAGE", "Admin xóa tin nhắn vi phạm #" + messageId);

        return Map.of("message", "Đã xóa tin nhắn vi phạm #" + messageId + " thành công");
    }

    private ChatMessageResponse toMessageResponse(ChatMessage msg) {
        return ChatMessageResponse.builder()
                .id(msg.getId())
                .conversationId(msg.getConversation() != null ? msg.getConversation().getId() : null)
                .sender(msg.getSender())
                .content(msg.getContent())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    private AdminTaskResponse toAdminTaskResponse(AgentTask task) {
        return AdminTaskResponse.builder()
                .id(task.getId())
                .userId(task.getUser() != null ? task.getUser().getId() : null)
                .username(task.getUser() != null ? task.getUser().getUsername() : null)
                .userEmail(task.getUser() != null ? task.getUser().getEmail() : null)
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
