package fixture;
import java.sql.Connection;
import java.sql.ResultSet;
class AccountRepository {
    ResultSet find(Connection connection, String name) throws Exception {
        return connection.createStatement().executeQuery("SELECT id FROM accounts WHERE name = '" + name + "'");
    }
}
