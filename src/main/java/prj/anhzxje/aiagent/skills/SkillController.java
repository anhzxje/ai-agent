package prj.anhzxje.aiagent.skills;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Authenticated metadata catalog; Markdown is never accepted from HTTP requests. */
@RestController
@RequestMapping("/api/skills")
public class SkillController {
    private final SkillRegistry registry;
    public SkillController(SkillRegistry registry) { this.registry = registry; }
    @GetMapping
    public List<SkillDefinition> list() { return registry.list(); }
}
