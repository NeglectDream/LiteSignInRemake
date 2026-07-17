package studio.trc.bukkit.litesignin.database.repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import studio.trc.bukkit.litesignin.database.DatabaseTable;
import studio.trc.bukkit.litesignin.database.engine.SQLiteEngine;

/** Persists the crash-recovery journal for physical retroactive-card use. */
public final class PhysicalCardOperationRepository {
    private final SQLiteEngine sqlite;

    public PhysicalCardOperationRepository(SQLiteEngine sqlite) {
        this.sqlite = sqlite;
    }

    public void create(PendingOperation operation) {
        sqlite.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + table()
                            + "(OperationId, UUID, SignDate, State, Snapshot, CardCount, BeforeCount, CreatedAt)"
                            + " VALUES(?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, operation.operationId().toString());
                statement.setString(2, operation.playerId().toString());
                statement.setString(3, operation.signDate());
                statement.setString(4, operation.state().name());
                statement.setBytes(5, operation.snapshot());
                statement.setInt(6, operation.cardCount());
                statement.setInt(7, operation.beforeCount());
                statement.setLong(8, operation.createdAt());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void markRemoved(UUID operationId) {
        updateState(operationId, OperationState.REMOVED);
    }

    public void markCommitted(UUID operationId) {
        updateState(operationId, OperationState.COMMITTED);
    }

    public void delete(UUID operationId) {
        sqlite.update("DELETE FROM " + table() + " WHERE OperationId = ?", operationId.toString());
    }

    public List<PendingOperation> findForPlayer(UUID playerId) {
        return sqlite.query("SELECT OperationId, UUID, SignDate, State, Snapshot, CardCount, BeforeCount, CreatedAt FROM "
                        + table() + " WHERE UUID = ? ORDER BY CreatedAt ASC",
                result -> {
                    List<PendingOperation> operations = new java.util.ArrayList<>();
                    while (result.next()) {
                        operations.add(new PendingOperation(
                                UUID.fromString(result.getString("OperationId")),
                                UUID.fromString(result.getString("UUID")),
                                result.getString("SignDate"),
                                OperationState.valueOf(result.getString("State")),
                                result.getBytes("Snapshot"),
                                result.getInt("CardCount"),
                                result.getInt("BeforeCount"),
                                result.getLong("CreatedAt")));
                    }
                    return operations;
                }, playerId.toString());
    }

    public boolean hasSignIn(UUID playerId, String signDate) {
        Integer count = sqlite.query("SELECT COUNT(*) FROM " + table(DatabaseTable.SIGN_IN_HISTORY)
                        + " WHERE UUID = ? AND SignDate = ?",
                result -> result.next() ? result.getInt(1) : 0,
                playerId.toString(), signDate);
        return count != null && count > 0;
    }

    private void updateState(UUID operationId, OperationState state) {
        sqlite.update("UPDATE " + table() + " SET State = ? WHERE OperationId = ?",
                state.name(), operationId.toString());
    }

    private String table() {
        return table(DatabaseTable.PHYSICAL_CARD_OPERATION);
    }

    private String table(DatabaseTable table) {
        return sqlite.getTableSyntax(table);
    }

    public enum OperationState {
        PREPARED,
        REMOVED,
        COMMITTED
    }

    public record PendingOperation(UUID operationId, UUID playerId, String signDate,
                                   OperationState state, byte[] snapshot, int cardCount,
                                   int beforeCount, long createdAt) {
        public PendingOperation {
            snapshot = snapshot != null ? snapshot.clone() : new byte[0];
        }

        @Override
        public byte[] snapshot() {
            return snapshot.clone();
        }
    }
}
