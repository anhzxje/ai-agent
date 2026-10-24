package prj.anhzxje.aiagent.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.*;

class AgentPropertiesTest {
    @Test
    void bindsActualYamlAllowlist() throws Exception {
        var environment = new StandardEnvironment();
        var source = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml")).getFirst();
        environment.getPropertySources().addLast(source);
        AgentProperties properties = Binder.get(environment).bind("agent", AgentProperties.class).get();
        assertFalse(properties.getAllowedPaths().isEmpty());
        assertFalse(properties.getAllowedPaths().getFirst().contains("${"));
    }
}
