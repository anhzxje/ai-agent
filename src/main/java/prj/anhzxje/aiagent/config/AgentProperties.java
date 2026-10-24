package prj.anhzxje.aiagent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Binds both YAML lists and comma-separated environment properties. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {
    private List<String> allowedPaths = new ArrayList<>();
}
