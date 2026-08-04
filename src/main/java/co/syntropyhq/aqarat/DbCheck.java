package co.syntropyhq.aqarat;

import co.syntropyhq.aqarat.util.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

// Phase 0 connectivity check. Deleted in Phase 7.
public class DbCheck {

    public static void main(String[] args) throws SQLException {
        String sql = "SELECT COUNT(*) AS property_count FROM dbo.property";
        try (Connection connection = Db.get();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            System.out.println(resultSet.getInt("property_count"));
        }
    }
}
