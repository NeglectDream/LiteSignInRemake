package studio.trc.bukkit.litesignin.database;

import studio.trc.bukkit.litesignin.database.engine.SQLiteEngine;

public enum DatabaseType
{
    SQLITE;

    public static String getTableSyntax(DatabaseType type) {
        return SQLiteEngine.getInstance().getTableSyntax(DatabaseTable.PLAYER_DATA);
    }
}
