package fixture.directory;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.jdbc.core.JdbcTemplate;
import java.security.Principal;
import java.nio.file.*;
@RestController
class DirectoryController {
    private final JdbcTemplate jdbc;
    DirectoryController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @GetMapping("/accounts")
    Object search(@RequestParam String name) { return jdbc.queryForList("SELECT id FROM accounts WHERE name = ?", name); }
    @GetMapping("/accounts/{id}")
    Object account(@PathVariable long id, Principal principal) { return jdbc.queryForMap("SELECT id FROM accounts WHERE id = ? AND owner = ?", id, principal.getName()); }
    record Quantity(@Min(1) int value) {}
    @PostMapping("/quantity")
    int quantity(@Valid @RequestBody Quantity quantity) { return quantity.value(); }
    long countRows(Path path) throws Exception {
        try (var lines = Files.lines(path)) { return lines.count(); }
    }
}
