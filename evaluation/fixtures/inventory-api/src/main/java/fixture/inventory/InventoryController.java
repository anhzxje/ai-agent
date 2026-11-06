package fixture.inventory;
import org.springframework.web.bind.annotation.*;
import java.nio.file.*;
import java.security.Principal;
@RestController
class InventoryController {
    private final InventoryStore store;
    InventoryController(InventoryStore store) { this.store = store; }
    @GetMapping("/accounts")
    Object search(@RequestParam String name) { return store.search(name); }
    @GetMapping("/accounts/{id}")
    Object account(@PathVariable long id, Principal principal) { return store.account(id); }
    @GetMapping("/documents")
    byte[] document(@RequestParam String path) throws Exception { return Files.readAllBytes(Path.of(path)); }
    @GetMapping("/profile")
    Profile profile(Principal principal) { return new Profile(principal.getName(), store.passwordHash(principal.getName())); }
    record Profile(String username, String passwordHash) {}
    @GetMapping("/stock/available")
    boolean available(@RequestParam int requested, @RequestParam int stock) { return requested >= stock; }
    @GetMapping("/stock/label")
    String label(@RequestParam(required=false) String value) { return value.trim(); }
    @PostMapping("/accounts/batch")
    Object accounts(@RequestBody java.util.List<Long> ids) { return store.accounts(ids); }
    @PostMapping("/stock/codes")
    int codes(@RequestBody java.util.List<String> codes) { return store.validCodes(codes); }
    @PostMapping("/stock/unique")
    Object unique(@RequestBody java.util.List<Long> ids) { return store.unique(ids); }
    @GetMapping("/stock/count")
    long countRows() throws Exception { return store.countRows(); }
}
