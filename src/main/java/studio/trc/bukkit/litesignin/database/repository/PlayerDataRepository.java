package studio.trc.bukkit.litesignin.database.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import studio.trc.bukkit.litesignin.api.SignInResult;
import studio.trc.bukkit.litesignin.database.DatabaseException;
import studio.trc.bukkit.litesignin.database.DatabaseTable;
import studio.trc.bukkit.litesignin.database.engine.SQLiteEngine;
import studio.trc.bukkit.litesignin.util.SignInDate;

/**
 * SQLite persistence boundary for one player's aggregate and sign-in history.
 *
 * <p>The repository owns JDBC resources and transactions but has no Bukkit,
 * reward, cache, or {@code SQLiteStorage} dependency. Callers apply returned
 * immutable snapshots to their in-memory state.</p>
 */
public final class PlayerDataRepository {
    private final SQLiteEngine sqlite;

    public PlayerDataRepository(SQLiteEngine sqlite) {
        this.sqlite = Objects.requireNonNull(sqlite, "sqlite");
    }

    /** Loads one player and migrates legacy History data into the canonical table. */
    public PlayerDataSnapshot load(UUID uuid, String knownName) {
        LoadedPlayer row = sqlite.query(
                "SELECT * FROM " + table(DatabaseTable.PLAYER_DATA) + " WHERE UUID = ?",
                result -> result.next() ? LoadedPlayer.from(result) : null,
                uuid.toString());

        if (row == null) {
            String initialName = knownName != null ? knownName : "";
            sqlite.update("INSERT OR IGNORE INTO " + table(DatabaseTable.PLAYER_DATA)
                    + "(UUID, Name, Year, Month, Day, Hour, Minute, Second, Continuous, RetroactiveCard, History)"
                    + " VALUES(?, ?, 1970, 1, 1, 0, 0, 0, 0, 0, '')",
                    uuid.toString(), initialName);
            row = new LoadedPlayer(initialName, 1970, 1, 1, 0, 0, 0, 0, 0, "");
        }

        List<SignInDate> legacyHistory = parseLegacyHistory(row.history());
        migrateLegacyHistory(uuid, legacyHistory);
        List<SignInDate> canonicalHistory = loadCanonicalHistory(uuid);

        String resolvedName = knownName != null ? knownName : row.name();
        if (knownName != null && !knownName.equals(row.name())) {
            sqlite.update("UPDATE " + table(DatabaseTable.PLAYER_DATA)
                    + " SET Name = ? WHERE UUID = ?", knownName, uuid.toString());
        }
        return new PlayerDataSnapshot(resolvedName, row.year(), row.month(), row.day(),
                row.hour(), row.minute(), row.second(), row.continuous(),
                Math.max(0, row.retroactiveCard()), canonicalHistory);
    }

    /** Migrates valid legacy History entries without duplicating canonical rows. */
    public void migrateLegacyHistory(UUID uuid, List<SignInDate> legacyHistory) {
        if (legacyHistory == null || legacyHistory.isEmpty()) {
            return;
        }
        sqlite.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO " + table(DatabaseTable.SIGN_IN_HISTORY)
                            + "(UUID, SignDate, RecordedAt) VALUES(?, ?, ?)")) {
                for (SignInDate date : legacyHistory) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, dateKey(date));
                    statement.setString(3, date.getDataText(date.hasTimePeriod()));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    /** Loads canonical history in date order. */
    public List<SignInDate> loadCanonicalHistory(UUID uuid) {
        return sqlite.query("SELECT RecordedAt FROM " + table(DatabaseTable.SIGN_IN_HISTORY)
                        + " WHERE UUID = ? ORDER BY SignDate ASC",
                result -> {
                    List<SignInDate> dates = new ArrayList<>();
                    while (result.next()) {
                        SignInDate parsed = SignInDate.getInstance(result.getString("RecordedAt"));
                        if (parsed != null) {
                            dates.add(parsed);
                        }
                    }
                    return dates;
                }, uuid.toString());
    }

    /** Commits one canonical sign-in row and aggregate state atomically. */
    public CommitResult commitSignIn(UUID uuid, SignInDate date,
                                     PlayerDataSnapshot nextState, int virtualCardCost) {
        return commitSignIn(uuid, date, nextState, virtualCardCost, null);
    }

    /** Commits sign-in state and an optional physical-card journal state together. */
    public CommitResult commitSignIn(UUID uuid, SignInDate date,
                                     PlayerDataSnapshot nextState, int virtualCardCost,
                                     UUID physicalCardOperationId) {
        try {
            int remainingCards = sqlite.inTransaction(connection -> {
                int inserted;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT OR IGNORE INTO " + table(DatabaseTable.SIGN_IN_HISTORY)
                                + "(UUID, SignDate, RecordedAt) VALUES(?, ?, ?)")) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, dateKey(date));
                    statement.setString(3, date.getDataText(date.hasTimePeriod()));
                    inserted = statement.executeUpdate();
                }
                if (inserted != 1) {
                    throw new AbortCommitException(SignInResult.ALREADY_SIGNED_IN);
                }

                int databaseCards;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT RetroactiveCard FROM " + table(DatabaseTable.PLAYER_DATA)
                                + " WHERE UUID = ?")) {
                    statement.setString(1, uuid.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("Player row is missing for " + uuid);
                        }
                        databaseCards = Math.max(0, result.getInt("RetroactiveCard"));
                    }
                }
                if (virtualCardCost > 0 && databaseCards < virtualCardCost) {
                    throw new AbortCommitException(SignInResult.INSUFFICIENT_CARDS);
                }
                int remaining = Math.max(0, databaseCards - Math.max(0, virtualCardCost));

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE " + table(DatabaseTable.PLAYER_DATA)
                                + " SET Name = ?, Year = ?, Month = ?, Day = ?, Hour = ?, Minute = ?, Second = ?,"
                                + " Continuous = ?, RetroactiveCard = ?, History = ? WHERE UUID = ?")) {
                    bindSnapshot(statement, nextState, remaining, uuid, 1);
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("Expected one player row update for " + uuid);
                    }
                }

                if (physicalCardOperationId != null) {
                    try (PreparedStatement operation = connection.prepareStatement(
                            "UPDATE " + table(DatabaseTable.PHYSICAL_CARD_OPERATION)
                                    + " SET State = ? WHERE OperationId = ?")) {
                        operation.setString(1, "COMMITTED");
                        operation.setString(2, physicalCardOperationId.toString());
                        if (operation.executeUpdate() != 1) {
                            throw new SQLException("Expected one physical-card operation update for "
                                    + physicalCardOperationId);
                        }
                    }
                }
                return remaining;
            });
            return CommitResult.success(nextState.withRetroactiveCard(remainingCards), remainingCards);
        } catch (AbortCommitException aborted) {
            return CommitResult.failure(aborted.result());
        } catch (DatabaseException ex) {
            return CommitResult.failure(SignInResult.STORAGE_FAILURE);
        }
    }

    /** Saves aggregate state and canonical history atomically. */
    public void save(UUID uuid, PlayerDataSnapshot snapshot) {
        sqlite.inTransaction(connection -> {
            try (PreparedStatement insertPlayer = connection.prepareStatement(
                    "INSERT OR IGNORE INTO " + table(DatabaseTable.PLAYER_DATA)
                            + "(UUID, Name, Year, Month, Day, Hour, Minute, Second, Continuous, RetroactiveCard, History)"
                            + " VALUES(?, ?, 1970, 1, 1, 0, 0, 0, 0, 0, '')")) {
                insertPlayer.setString(1, uuid.toString());
                insertPlayer.setString(2, snapshot.name());
                insertPlayer.executeUpdate();
            }

            try (PreparedStatement deleteHistory = connection.prepareStatement(
                    "DELETE FROM " + table(DatabaseTable.SIGN_IN_HISTORY) + " WHERE UUID = ?")) {
                deleteHistory.setString(1, uuid.toString());
                deleteHistory.executeUpdate();
            }
            try (PreparedStatement insertHistory = connection.prepareStatement(
                    "INSERT INTO " + table(DatabaseTable.SIGN_IN_HISTORY)
                            + "(UUID, SignDate, RecordedAt) VALUES(?, ?, ?)")) {
                for (SignInDate record : snapshot.history()) {
                    insertHistory.setString(1, uuid.toString());
                    insertHistory.setString(2, dateKey(record));
                    insertHistory.setString(3, record.getDataText(record.hasTimePeriod()));
                    insertHistory.addBatch();
                }
                insertHistory.executeBatch();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE " + table(DatabaseTable.PLAYER_DATA)
                            + " SET Name = ?, Year = ?, Month = ?, Day = ?, Hour = ?, Minute = ?, Second = ?,"
                            + " Continuous = ?, RetroactiveCard = ?, History = ? WHERE UUID = ?")) {
                bindSnapshot(statement, snapshot, snapshot.retroactiveCard(), uuid, 1);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Expected one player row update for " + uuid);
                }
            }
            return null;
        });
    }

    /**
     * Updates only the scalar fields of a player row without rewriting the
     * canonical sign-in history table.
     *
     * <p>Most {@code saveData()} callers change scalar columns (name, sign-in
     * time, continuous count, retroactive-card count) without touching the
     * history. The full {@link #save(UUID, PlayerDataSnapshot)} path deletes
     * and re-inserts every history row, which is wasteful for those cases.
     * This method updates the {@code PlayerData} row — including the
     * denormalized {@code History} JSON column — while leaving
     * {@code PlayerData_history} untouched.</p>
     */
    public void updateFields(UUID uuid, PlayerDataSnapshot snapshot) {
        sqlite.inTransaction(connection -> {
            try (PreparedStatement insertPlayer = connection.prepareStatement(
                    "INSERT OR IGNORE INTO " + table(DatabaseTable.PLAYER_DATA)
                            + "(UUID, Name, Year, Month, Day, Hour, Minute, Second, Continuous, RetroactiveCard, History)"
                            + " VALUES(?, ?, 1970, 1, 1, 0, 0, 0, 0, 0, '')")) {
                insertPlayer.setString(1, uuid.toString());
                insertPlayer.setString(2, snapshot.name());
                insertPlayer.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE " + table(DatabaseTable.PLAYER_DATA)
                            + " SET Name = ?, Year = ?, Month = ?, Day = ?, Hour = ?, Minute = ?, Second = ?,"
                            + " Continuous = ?, RetroactiveCard = ?, History = ? WHERE UUID = ?")) {
                bindSnapshot(statement, snapshot, snapshot.retroactiveCard(), uuid, 1);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Expected one player row update for " + uuid);
                }
            }
            return null;
        });
    }

    /** Backs up aggregate and canonical history tables to a standalone SQLite file. */
    public void backup(String filePath) throws SQLException {
        List<PlayerBackupRow> players = sqlite.query(
                "SELECT * FROM " + table(DatabaseTable.PLAYER_DATA),
                result -> {
                    List<PlayerBackupRow> rows = new ArrayList<>();
                    while (result.next()) {
                        rows.add(PlayerBackupRow.from(result));
                    }
                    return rows;
                });
        List<HistoryBackupRow> historyRows = sqlite.query(
                "SELECT UUID, SignDate, RecordedAt FROM " + table(DatabaseTable.SIGN_IN_HISTORY),
                result -> {
                    List<HistoryBackupRow> rows = new ArrayList<>();
                    while (result.next()) {
                        rows.add(new HistoryBackupRow(result.getString("UUID"),
                                result.getString("SignDate"), result.getString("RecordedAt")));
                    }
                    return rows;
                });
        List<OperationBackupRow> operationRows = sqlite.query(
                "SELECT OperationId, UUID, SignDate, State, Snapshot, CardCount, BeforeCount, CreatedAt FROM "
                        + table(DatabaseTable.PHYSICAL_CARD_OPERATION),
                result -> {
                    List<OperationBackupRow> rows = new ArrayList<>();
                    while (result.next()) {
                        rows.add(new OperationBackupRow(
                                result.getString("OperationId"),
                                result.getString("UUID"),
                                result.getString("SignDate"),
                                result.getString("State"),
                                result.getBytes("Snapshot"),
                                result.getInt("CardCount"),
                                result.getInt("BeforeCount"),
                                result.getLong("CreatedAt")));
                    }
                    return rows;
                });

        try (Connection target = DriverManager.getConnection("jdbc:sqlite:" + filePath)) {
            target.setAutoCommit(false);
            try {
                try (PreparedStatement createPlayers = target.prepareStatement(
                        DatabaseTable.PLAYER_DATA.getDefaultCreateTableSyntax());
                     PreparedStatement createHistory = target.prepareStatement(
                        DatabaseTable.SIGN_IN_HISTORY.getDefaultCreateTableSyntax());
                     PreparedStatement createOperations = target.prepareStatement(
                        DatabaseTable.PHYSICAL_CARD_OPERATION.getDefaultCreateTableSyntax())) {
                    createPlayers.executeUpdate();
                    createHistory.executeUpdate();
                    createOperations.executeUpdate();
                }
                try (PreparedStatement clearPlayers = target.prepareStatement("DELETE FROM PlayerData");
                     PreparedStatement clearHistory = target.prepareStatement("DELETE FROM PlayerData_history");
                     PreparedStatement clearOperations = target.prepareStatement("DELETE FROM PhysicalCardOperation")) {
                    clearHistory.executeUpdate();
                    clearPlayers.executeUpdate();
                    clearOperations.executeUpdate();
                }
                try (PreparedStatement statement = target.prepareStatement(
                        "INSERT INTO PlayerData(UUID, Name, Year, Month, Day, Hour, Minute, Second, Continuous, RetroactiveCard, History)"
                                + " VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (PlayerBackupRow row : players) {
                        row.bind(statement);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                try (PreparedStatement statement = target.prepareStatement(
                        "INSERT INTO PlayerData_history(UUID, SignDate, RecordedAt) VALUES(?, ?, ?)")) {
                    for (HistoryBackupRow row : historyRows) {
                        statement.setString(1, row.uuid());
                        statement.setString(2, row.signDate());
                        statement.setString(3, row.recordedAt());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                try (PreparedStatement statement = target.prepareStatement(
                        "INSERT INTO PhysicalCardOperation(OperationId, UUID, SignDate, State, Snapshot, CardCount, BeforeCount, CreatedAt)"
                                + " VALUES(?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (OperationBackupRow row : operationRows) {
                        row.bind(statement);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                target.commit();
            } catch (SQLException ex) {
                target.rollback();
                throw ex;
            } finally {
                target.setAutoCommit(true);
            }
        }
    }

    private String table(DatabaseTable table) {
        return sqlite.getTableSyntax(table);
    }

    private static void bindSnapshot(PreparedStatement statement, PlayerDataSnapshot snapshot,
                                     int retroactiveCard, UUID uuid, int offset) throws SQLException {
        statement.setString(offset, snapshot.name());
        statement.setInt(offset + 1, snapshot.year());
        statement.setInt(offset + 2, snapshot.month());
        statement.setInt(offset + 3, snapshot.day());
        statement.setInt(offset + 4, snapshot.hour());
        statement.setInt(offset + 5, snapshot.minute());
        statement.setInt(offset + 6, snapshot.second());
        statement.setInt(offset + 7, snapshot.continuous());
        statement.setInt(offset + 8, retroactiveCard);
        statement.setString(offset + 9, serializeHistory(snapshot.history()));
        statement.setString(offset + 10, uuid.toString());
    }

    private static List<SignInDate> parseLegacyHistory(String serialized) {
        List<SignInDate> dates = new ArrayList<>();
        if (serialized == null || serialized.isBlank()) {
            return dates;
        }
        for (String token : serialized.split(", ")) {
            SignInDate parsed = SignInDate.getInstance(token);
            if (parsed != null) {
                dates.add(parsed);
            }
        }
        return dates;
    }

    private static String serializeHistory(List<SignInDate> records) {
        StringBuilder builder = new StringBuilder();
        for (SignInDate record : records) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(record);
        }
        return builder.toString();
    }

    private static String dateKey(SignInDate date) {
        return String.format("%04d-%02d-%02d", date.getYear(), date.getMonth(), date.getDay());
    }

    public record PlayerDataSnapshot(String name, int year, int month, int day,
                                     int hour, int minute, int second, int continuous,
                                     int retroactiveCard, List<SignInDate> history) {
        public PlayerDataSnapshot {
            history = List.copyOf(copyDates(history));
        }

        public PlayerDataSnapshot withRetroactiveCard(int amount) {
            return new PlayerDataSnapshot(name, year, month, day, hour, minute, second,
                    continuous, amount, history);
        }
    }

    public record CommitResult(SignInResult result, int remainingCards,
                               PlayerDataSnapshot state) {
        private static CommitResult success(PlayerDataSnapshot state, int remainingCards) {
            return new CommitResult(SignInResult.SUCCESS, remainingCards, state);
        }

        private static CommitResult failure(SignInResult result) {
            return new CommitResult(result, -1, null);
        }
    }

    private record LoadedPlayer(String name, int year, int month, int day, int hour,
                                int minute, int second, int continuous,
                                int retroactiveCard, String history) {
        private static LoadedPlayer from(ResultSet result) throws SQLException {
            return new LoadedPlayer(result.getString("Name"), result.getInt("Year"),
                    result.getInt("Month"), result.getInt("Day"), result.getInt("Hour"),
                    result.getInt("Minute"), result.getInt("Second"),
                    result.getInt("Continuous"), result.getInt("RetroactiveCard"),
                    result.getString("History"));
        }
    }

    private static List<SignInDate> copyDates(List<SignInDate> source) {
        List<SignInDate> copy = new ArrayList<>();
        if (source != null) {
            for (SignInDate date : source) {
                if (date != null) {
                    copy.add(date.copy());
                }
            }
        }
        return copy;
    }

    private static final class AbortCommitException extends RuntimeException {
        private final SignInResult result;

        private AbortCommitException(SignInResult result) {
            this.result = result;
        }

        private SignInResult result() {
            return result;
        }
    }

    private record PlayerBackupRow(String uuid, String name, int year, int month,
                                   int day, int hour, int minute, int second,
                                   int continuous, int retroactiveCard, String history) {
        private static PlayerBackupRow from(ResultSet result) throws SQLException {
            return new PlayerBackupRow(result.getString("UUID"), result.getString("Name"),
                    result.getInt("Year"), result.getInt("Month"), result.getInt("Day"),
                    result.getInt("Hour"), result.getInt("Minute"), result.getInt("Second"),
                    result.getInt("Continuous"), result.getInt("RetroactiveCard"),
                    result.getString("History"));
        }

        private void bind(PreparedStatement statement) throws SQLException {
            statement.setString(1, uuid);
            statement.setString(2, name);
            statement.setInt(3, year);
            statement.setInt(4, month);
            statement.setInt(5, day);
            statement.setInt(6, hour);
            statement.setInt(7, minute);
            statement.setInt(8, second);
            statement.setInt(9, continuous);
            statement.setInt(10, retroactiveCard);
            statement.setString(11, history != null ? history : "");
        }
    }

    private record HistoryBackupRow(String uuid, String signDate, String recordedAt) {}

    /**
     * Backs up a {@link DatabaseTable#PHYSICAL_CARD_OPERATION} row so that a
     * restored database keeps its pending crash-recovery state.
     */
    private record OperationBackupRow(String operationId, String uuid, String signDate,
                                      String state, byte[] snapshot, int cardCount,
                                      int beforeCount, long createdAt) {
        private void bind(PreparedStatement statement) throws SQLException {
            statement.setString(1, operationId);
            statement.setString(2, uuid);
            statement.setString(3, signDate);
            statement.setString(4, state);
            statement.setBytes(5, snapshot);
            statement.setInt(6, cardCount);
            statement.setInt(7, beforeCount);
            statement.setLong(8, createdAt);
        }
    }
}
