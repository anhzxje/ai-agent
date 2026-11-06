package fixture.inventory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
@Repository
class InventoryStore {
    private final JdbcTemplate jdbc;
    InventoryStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    Object search(String name) { return jdbc.queryForList("SELECT id FROM accounts WHERE name = '" + name + "'"); }
    Object account(long id) { return jdbc.queryForMap("SELECT id, owner FROM accounts WHERE id = ?", id); }
    String passwordHash(String name) { return jdbc.queryForObject("SELECT password_hash FROM accounts WHERE name = ?", String.class, name); }
    List<Object> accounts(List<Long> ids) {
        List<Object> results = new ArrayList<>();
        for (long id : ids) results.add(account(id));
        return results;
    }
    int validCodes(List<String> codes) {
        int count = 0;
        for (String code : codes) if (Pattern.compile("[A-Z]{4}").matcher(code).matches()) count++;
        return count;
    }
    List<Long> unique(List<Long> ids) {
        List<Long> result = new ArrayList<>();
        for (Long id : ids) if (!result.contains(id)) result.add(id);
        return result;
    }
    long countRows() throws Exception { return Files.lines(Path.of("data/items.txt")).count(); }
}
