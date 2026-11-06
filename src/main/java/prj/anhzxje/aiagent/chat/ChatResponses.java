package prj.anhzxje.aiagent.chat;

import prj.anhzxje.aiagent.infrastructure.persistence.entity.ChatConversation;

/** Canonical response mapping shared by owner and administration entry points. No authorization decisions. */
public final class ChatResponses {
    private ChatResponses() {}

    public static ChatConversationResponse conversation(ChatConversation conv) {
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
}
