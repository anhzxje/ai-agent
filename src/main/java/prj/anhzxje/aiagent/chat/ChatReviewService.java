package prj.anhzxje.aiagent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import prj.anhzxje.aiagent.infrastructure.ai.SystemConfigService;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.ChatConversation;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.ChatReviewLink;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.User;
import prj.anhzxje.aiagent.infrastructure.persistence.repository.AgentTaskRepository;
import prj.anhzxje.aiagent.infrastructure.persistence.repository.ChatConversationRepository;
import prj.anhzxje.aiagent.infrastructure.persistence.repository.ChatReviewLinkRepository;
import prj.anhzxje.aiagent.infrastructure.tools.ToolManagerService;
import prj.anhzxje.aiagent.shared.enums.JobStatus;
import prj.anhzxje.aiagent.shared.enums.TaskType;
import prj.anhzxje.aiagent.shared.exception.ResourceNotFoundException;
import prj.anhzxje.aiagent.tasks.AgentTaskRequest;
import prj.anhzxje.aiagent.tasks.AgentTaskResponse;
import prj.anhzxje.aiagent.tasks.AgentTaskService;

@Service
@RequiredArgsConstructor
public class ChatReviewService {
    private final ChatConversationRepository conversations;
    private final ChatReviewLinkRepository links;
    private final AgentTaskRepository tasks;
    private final AgentTaskService taskService;
    private final SystemConfigService config;
    private final ToolManagerService tools;
    private final ObjectMapper mapper;

    @Transactional
    public AgentTaskResponse submit(Long conversationId, ChatReviewRequest request, User user) {
        if (request.getRequestId() == null || request.getRequestId().isBlank() || request.getRequestId().length() > 64)
            throw new IllegalArgumentException("requestId là bắt buộc (tối đa 64 ký tự)");
        if (!ReviewCommand.isReview(request.getMessage()) || request.getMessage().length() > 10000)
            throw new IllegalArgumentException("Cần lệnh /review không quá 10000 ký tự");
        // Serialize duplicate submissions for the same conversation, including concurrent HTTP requests.
        ChatConversation conversation = conversations.lockOwned(conversationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cuộc hội thoại"));
        if (conversation.getProject() == null || !conversation.getProject().getUser().getId().equals(user.getId()))
            throw new ResourceNotFoundException("Không tìm thấy dự án của cuộc hội thoại");
        String fingerprint = fingerprint(request);
        var previous = links.findByConversationIdAndRequestId(conversationId, request.getRequestId());
        if (previous.isPresent()) {
            if (!fingerprint.equals(previous.get().getRequestFingerprint()))
                throw new IllegalArgumentException("requestId đã được sử dụng cho yêu cầu khác");
            return taskService.getTask(previous.get().getTask().getId());
        }
        String model = request.getModel() == null || request.getModel().isBlank() ? conversation.getModel() : request.getModel().trim();
        if (model == null || !config.getAllowedModels().contains(model))
            throw new IllegalArgumentException("Model không được phép; hãy chọn model một cách tường minh");
        if (request.getSelectedTools() != null) {
            Set<String> enabled = tools.getAllTools().stream().filter(t -> Boolean.TRUE.equals(t.getEnabled()))
                    .map(t -> t.getToolName()).collect(Collectors.toSet());
            if (request.getSelectedTools().stream().anyMatch(t -> t == null || !enabled.contains(t)))
                throw new IllegalArgumentException("Công cụ được chọn không khả dụng");
        }
        AgentTaskRequest taskRequest = new AgentTaskRequest();
        taskRequest.setProjectId(conversation.getProject().getId());
        taskRequest.setType(TaskType.CODE_REVIEW);
        ObjectNode input = mapper.createObjectNode().put("focus", ReviewCommand.focus(request.getMessage()));
        taskRequest.setInput(input.toString());
        taskRequest.setPrompt(request.getMessage().strip().substring(7).strip());
        taskRequest.setModel(model);
        taskRequest.setSelectedTools(request.getSelectedTools());
        taskRequest.setSkills(request.getSkills());
        AgentTaskResponse task = taskService.createTask(taskRequest);
        links.save(ChatReviewLink.builder().conversation(conversation)
                .task(tasks.getReferenceById(task.getId())).requestId(request.getRequestId()).requestFingerprint(fingerprint).build());
        return task;
    }

    @Transactional(readOnly = true)
    public List<AgentTaskResponse> list(Long conversationId, User user) {
        owned(conversationId, user);
        return links.findByConversationIdOrderByIdAsc(conversationId).stream()
                .map(link -> taskService.getTask(link.getTask().getId())).toList();
    }

    @Transactional(readOnly = true)
    public String reportContext(Long conversationId, Long taskId, User user) {
        ChatConversation conv = owned(conversationId, user);
        ChatReviewLink link = links.findByConversationIdAndTaskId(conversationId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo"));
        var task = link.getTask();
        if (!task.getUser().getId().equals(user.getId()) || conv.getProject() == null
                || !conv.getProject().getUser().getId().equals(user.getId())
                || !task.getProject().getId().equals(conv.getProject().getId()))
            throw new ResourceNotFoundException("Không tìm thấy báo cáo");
        if (task.getStatus() != JobStatus.COMPLETED || task.getOutput() == null)
            throw new IllegalStateException("Báo cáo chưa hoàn thành");
        try {
            var report = mapper.readTree(task.getOutput());
            ObjectNode selected = mapper.createObjectNode();
            for (String field : List.of("summary", "issues", "limitations", "suggestions", "appliedSkills"))
                if (report.has(field)) selected.set(field, report.get(field));
            String context = mapper.writeValueAsString(selected);
            return context.length() <= 12000 ? context : context.substring(0, 12000) + "\n[Report context truncated]";
        } catch (Exception e) { throw new IllegalStateException("Không thể đọc báo cáo đã lưu", e); }
    }

    String fingerprint(ChatReviewRequest request) {
        try {
            var payload = mapper.createObjectNode();
            payload.put("message", request.getMessage());
            payload.put("model", request.getModel());
            payload.set("selectedTools", mapper.valueToTree(request.getSelectedTools()));
            payload.set("skills", mapper.valueToTree(request.getSkills()));
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(payload));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) { throw new IllegalArgumentException("Không thể xác thực yêu cầu", e); }
    }

    private ChatConversation owned(Long id, User user) {
        return conversations.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cuộc hội thoại"));
    }
}
