package studio.trc.bukkit.litesignin.database.engine;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;

import lombok.Getter;
import lombok.Setter;

import studio.trc.bukkit.litesignin.configuration.ConfigurationType;
import studio.trc.bukkit.litesignin.database.DatabaseEngine;
import studio.trc.bukkit.litesignin.database.DatabaseException;
import studio.trc.bukkit.litesignin.database.DatabaseTable;
import studio.trc.bukkit.litesignin.database.DatabaseType;
import studio.trc.bukkit.litesignin.message.MessageUtil;

/**
 * Serialized SQLite connection manager.
 *
 * <p>All project-owned queries and transactions execute while holding the same
 * connection lock. Connection failures are propagated as {@link DatabaseException}
 * instead of being converted into valid-looking empty results.</p>
 */
public class SQLiteEngine implements DatabaseEngine {
    @Getter
    @Setter
    private static volatile SQLiteEngine instance;

    private final Object connectionLock = new Object();

    @Getter
    private Connection sqliteConnection;
    @Getter
    private final String folderPath;
    @Getter
    private final String fileName;

    public SQLiteEngine(String folderPath, String fileName) {
        this.folderPath = folderPath;
        this.fileName = fileName;
    }

    @Override
    public void connect() {
        synchronized (connectionLock) {
            if (isConnectionOpenLocked()) {
                return;
            }
            connectLocked();
        }
    }

    private void connectLocked() {
        closeConnectionQuietly();
        try {
            Class.forName("org.sqlite.JDBC");
            File databaseFolder = new File(folderPath);
            if ((!databaseFolder.exists() && !databaseFolder.mkdirs()) || !databaseFolder.isDirectory()) {
                throw new DatabaseException("Unable to create SQLite directory: " + databaseFolder.getAbsolutePath());
            }

            File databaseFile = new File(databaseFolder, fileName);
            sqliteConnection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            try (Statement statement = sqliteConnection.createStatement()) {
                statement.execute("PRAGMA busy_timeout = 5000");
            }
            initializeLocked();

            Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
            placeholders.put("{database}", "SQLite");
            MessageUtil.sendConsoleMessage("Console-Messages.Successfully-Connected", ConfigurationType.MESSAGES, placeholders);
        } catch (ClassNotFoundException ex) {
            closeConnectionQuietly();
            reportDatabaseFailure(ex, "No-Driver-Found");
            throw new DatabaseException("SQLite JDBC driver is not available", ex);
        } catch (SQLException ex) {
            closeConnectionQuietly();
            reportDatabaseFailure(ex, "Connection-Failed");
            throw new DatabaseException("Unable to connect to SQLite database", ex);
        } catch (RuntimeException ex) {
            closeConnectionQuietly();
            if (!(ex instanceof DatabaseException)) {
                reportDatabaseFailure(ex, "Connection-Failed");
            }
            throw ex;
        }
    }

    @Override
    public void disconnect() {
        synchronized (connectionLock) {
            if (sqliteConnection == null) {
                return;
            }
            try {
                sqliteConnection.close();
                Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
                placeholders.put("{database}", "SQLite");
                MessageUtil.sendConsoleMessage("Console-Messages.Disconnected", ConfigurationType.MESSAGES, placeholders);
            } catch (SQLException ex) {
                reportDatabaseFailure(ex, "Connection-Error");
            } finally {
                sqliteConnection = null;
            }
        }
    }

    @Override
    public void checkConnection() throws SQLException {
        synchronized (connectionLock) {
            if (!isConnectionOpenLocked()) {
                connectLocked();
            }
            if (!isConnectionOpenLocked()) {
                throw new SQLException("SQLite connection is unavailable");
            }
        }
    }

    public boolean isConnected() {
        synchronized (connectionLock) {
            return isConnectionOpenLocked();
        }
    }

    @Override
    public int executeUpdate(String sqlSyntax, String... values) {
        return update(sqlSyntax, values);
    }

    public int update(String sqlSyntax, String... values) {
        return withConnection("Execute-Update-Failed", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sqlSyntax)) {
                bindValues(statement, values);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public int[] executeMultiQueries(String sqlSyntax, List<Map<Integer, String>> parameters) {
        return withConnection("Execute-Update-Failed", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sqlSyntax)) {
                for (Map<Integer, String> parameter : parameters) {
                    for (Map.Entry<Integer, String> entry : parameter.entrySet()) {
                        statement.setString(entry.getKey(), entry.getValue());
                    }
                    statement.addBatch();
                }
                return statement.executeBatch();
            }
        });
    }

    /**
     * Executes a query and consumes its ResultSet while the connection lock is
     * held. Callers must not retain the ResultSet after the mapper returns.
     */
    public <T> T query(String sqlSyntax, ResultSetMapper<T> mapper, String... values) {
        return withConnection("Execute-Query-Failed", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sqlSyntax)) {
                bindValues(statement, values);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return mapper.map(resultSet);
                }
            }
        });
    }

    /**
     * Compatibility query API. The returned row set is disconnected from the
     * live JDBC connection, so callers cannot hold the connection lock open.
     */
    @Override
    public SQLQuery executeQuery(String sqlSyntax, String... values) {
        return query(sqlSyntax, resultSet -> {
            CachedRowSet cached = RowSetProvider.newFactory().createCachedRowSet();
            cached.populate(resultSet);
            return new SQLQuery(cached, null);
        }, values);
    }

    /** Executes one callback in a SQLite transaction. */
    public <T> T inTransaction(TransactionCallback<T> callback) {
        synchronized (connectionLock) {
            ensureConnectedLocked();
            boolean previousAutoCommit;
            try {
                previousAutoCommit = sqliteConnection.getAutoCommit();
                sqliteConnection.setAutoCommit(false);
            } catch (SQLException ex) {
                throw databaseFailure(ex, "Execute-Update-Failed");
            }

            try {
                T result = callback.execute(sqliteConnection);
                sqliteConnection.commit();
                return result;
            } catch (SQLException ex) {
                rollbackQuietly(ex);
                throw databaseFailure(ex, "Execute-Update-Failed");
            } catch (RuntimeException ex) {
                rollbackQuietly(ex);
                throw ex;
            } finally {
                try {
                    sqliteConnection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ex) {
                    reportDatabaseFailure(ex, "Connection-Error");
                }
            }
        }
    }

    private <T> T withConnection(String errorPath, ConnectionCallback<T> callback) {
        synchronized (connectionLock) {
            ensureConnectedLocked();
            try {
                return callback.execute(sqliteConnection);
            } catch (SQLException ex) {
                throw databaseFailure(ex, errorPath);
            }
        }
    }

    private void ensureConnectedLocked() {
        if (!isConnectionOpenLocked()) {
            connectLocked();
        }
        if (!isConnectionOpenLocked()) {
            throw new DatabaseException("SQLite connection is unavailable");
        }
    }

    private boolean isConnectionOpenLocked() {
        if (sqliteConnection == null) {
            return false;
        }
        try {
            return !sqliteConnection.isClosed();
        } catch (SQLException ex) {
            return false;
        }
    }

    private void initializeLocked() throws SQLException {
        try (Statement statement = sqliteConnection.createStatement()) {
            for (DatabaseTable table : DatabaseTable.values()) {
                statement.addBatch(table.getCreateTableSyntax(DatabaseType.SQLITE));
            }
            statement.executeBatch();
        }
    }

    @Override
    public void initialize() {
        withConnection("Initialization-Failed", connection -> {
            initializeLocked();
            return null;
        });
    }

    @Override
    public Connection getConnection() {
        synchronized (connectionLock) {
            return sqliteConnection;
        }
    }

    @Override
    public void throwSQLException(Exception exception, String path, boolean reconnect) {
        reportDatabaseFailure(exception, path);
    }

    public String getTableSyntax(DatabaseTable table) {
        return table.getDisplayName();
    }

    private DatabaseException databaseFailure(Exception exception, String path) {
        reportDatabaseFailure(exception, path);
        return new DatabaseException("SQLite operation failed: " + path, exception);
    }

    private void reportDatabaseFailure(Exception exception, String path) {
        Map<String, String> placeholders = MessageUtil.getDefaultPlaceholders();
        placeholders.put("{database}", "SQLite");
        placeholders.put("{error}", exception.getLocalizedMessage() != null ? exception.getLocalizedMessage() : exception.getClass().getSimpleName());
        MessageUtil.sendConsoleMessage("Console-Messages." + path, ConfigurationType.MESSAGES, placeholders);
    }

    private void rollbackQuietly(Throwable originalError) {
        try {
            sqliteConnection.rollback();
        } catch (SQLException rollbackError) {
            originalError.addSuppressed(rollbackError);
            reportDatabaseFailure(rollbackError, "Execute-Update-Failed");
        }
    }

    private void closeConnectionQuietly() {
        if (sqliteConnection == null) {
            return;
        }
        try {
            sqliteConnection.close();
        } catch (SQLException ignored) {
            // A failed candidate connection is already unusable; the original
            // connection error is the actionable failure reported to callers.
        } finally {
            sqliteConnection = null;
        }
    }

    private static void bindValues(PreparedStatement statement, String... values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            statement.setString(index + 1, values[index]);
        }
    }

    @FunctionalInterface
    public interface ResultSetMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    public interface ConnectionCallback<T> {
        T execute(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute(Connection connection) throws SQLException;
    }
}
