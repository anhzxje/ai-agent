package prj.anhzxje.aiagent.projects;

import prj.anhzxje.aiagent.infrastructure.persistence.entity.Project;

/** Canonical response mapping shared by owner and administration entry points. No authorization decisions. */
public final class ProjectResponses {
    private ProjectResponses() {}

    public static ProjectResponse from(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .path(project.getPath())
                .language(project.getLanguage())
                .ownerUsername(project.getUser() != null ? project.getUser().getUsername() : null)
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
