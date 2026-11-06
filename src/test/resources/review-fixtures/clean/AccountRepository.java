package fixture;
import java.sql.Connection;
class AccountRepository {
    boolean exists(Connection connection, String name) throws Exception {
        try (var statement = connection.prepareStatement("SELECT id FROM accounts WHERE name = ?")) {
            statement.setString(1, name);
            try (var rows = statement.executeQuery()) { return rows.next(); }
        }
    }
}
