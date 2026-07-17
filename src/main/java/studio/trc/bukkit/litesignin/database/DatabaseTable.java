package studio.trc.bukkit.litesignin.database;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.Getter;

import studio.trc.bukkit.litesignin.configuration.ConfigurationType;
import studio.trc.bukkit.litesignin.configuration.ConfigurationUtil;

public enum DatabaseTable {
    /** Players' aggregate data and legacy History compatibility field. */
    PLAYER_DATA("PlayerData", Arrays.asList(
        new DatabaseElement("UUID", "VARCHAR(36)", false, true),
        new DatabaseElement("Name", "VARCHAR(16)", true, false),
        new DatabaseElement("Year", "INT", true, false),
        new DatabaseElement("Month", "INT", true, false),
        new DatabaseElement("Day", "INT", true, false),
        new DatabaseElement("Hour", "INT", true, false),
        new DatabaseElement("Minute", "INT", true, false),
        new DatabaseElement("Second", "INT", true, false),
        new DatabaseElement("Continuous", "INT", true, false),
        new DatabaseElement("RetroactiveCard", "INT", true, false),
        new DatabaseElement("History", "LONGTEXT", true, false)
    )),

    /**
     * Canonical per-day sign-in records. The composite primary key is the
     * database-level idempotency boundary for rewards and retroactive cards.
     */
    SIGN_IN_HISTORY("PlayerData_history", Arrays.asList(
        new DatabaseElement("UUID", "VARCHAR(36)", false, true),
        new DatabaseElement("SignDate", "VARCHAR(10)", false, true),
        new DatabaseElement("RecordedAt", "VARCHAR(32)", false, false)
    )),

    /** Durable journal for physical retroactive-card reservations. */
    PHYSICAL_CARD_OPERATION("PhysicalCardOperation", Arrays.asList(
        new DatabaseElement("OperationId", "VARCHAR(36)", false, true),
        new DatabaseElement("UUID", "VARCHAR(36)", false, false),
        new DatabaseElement("SignDate", "VARCHAR(10)", false, false),
        new DatabaseElement("State", "VARCHAR(16)", false, false),
        new DatabaseElement("Snapshot", "BLOB", false, false),
        new DatabaseElement("CardCount", "INT", false, false),
        new DatabaseElement("BeforeCount", "INT", false, false),
        new DatabaseElement("CreatedAt", "BIGINT", false, false)
    ));

    @Getter
    private final String name;
    @Getter
    private final List<DatabaseElement> elements;

    DatabaseTable(String name, List<DatabaseElement> elements) {
        this.name = name;
        this.elements = elements;
    }

    public String getCreateTableSyntax(DatabaseType type) {
        return getCreateTableSyntax(getDisplayName());
    }

    public String getDefaultCreateTableSyntax() {
        return getCreateTableSyntax(name);
    }

    public String getDisplayName() {
        String playerTable = ConfigurationUtil.getConfig(ConfigurationType.CONFIG)
                .getString("SQLite-Storage.Table-Name");
        if (this == PLAYER_DATA) {
            return playerTable;
        }
        if (this == SIGN_IN_HISTORY) {
            return playerTable + "_history";
        }
        return name;
    }

    private String getCreateTableSyntax(String tableName) {
        StringBuilder builder = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(tableName)
                .append("(");
        for (int index = 0; index < elements.size(); index++) {
            DatabaseElement element = elements.get(index);
            builder.append(element.getField()).append(' ').append(element.getType());
            if (!element.isNull()) {
                builder.append(" NOT NULL");
            }
            if (index + 1 < elements.size()) {
                builder.append(',');
            }
        }
        builder.append(getPrimaryKeysSyntax()).append(')');
        return builder.toString();
    }

    private String getPrimaryKeysSyntax() {
        List<String> primaryKeys = new ArrayList<>();
        for (DatabaseElement element : elements) {
            if (element.isPrimaryKey()) {
                primaryKeys.add(element.getField());
            }
        }
        if (primaryKeys.isEmpty()) {
            return "";
        }
        return ", PRIMARY KEY (" + String.join(", ", primaryKeys) + ")";
    }
}
