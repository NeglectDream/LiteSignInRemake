package studio.trc.bukkit.litesignin.database.engine;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import lombok.Getter;

import studio.trc.bukkit.litesignin.database.DatabaseException;

/**
 * Compatibility wrapper for a disconnected query result.
 *
 * <p>Project-owned database code should prefer {@link SQLiteEngine#query}; this
 * wrapper remains for public API compatibility.</p>
 */
public class SQLQuery implements AutoCloseable {
    @Getter
    private final ResultSet result;
    @Getter
    private final PreparedStatement statement;

    public SQLQuery(ResultSet result, PreparedStatement statement) {
        this.result = result;
        this.statement = statement;
    }

    @Override
    public void close() {
        SQLException failure = null;
        if (result != null) {
            try {
                result.close();
            } catch (SQLException ex) {
                failure = ex;
            }
        }
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
        }
        if (failure != null) {
            throw new DatabaseException("Unable to close SQL query resources", failure);
        }
    }
}
