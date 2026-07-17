package studio.trc.bukkit.litesignin.database.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Protects the database persistence boundary extracted from SQLiteStorage. */
class PlayerDataRepositoryArchitectureTest {
    @Test
    void repositoryOwnsTransactionsHistoryAndBackupSql() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/database/repository/PlayerDataRepository.java");

        assertTrue(source.contains("sqlite.inTransaction"),
                "the repository must own SQLite transaction execution");
        assertTrue(source.contains("DatabaseTable.SIGN_IN_HISTORY"),
                "the repository must own canonical history table access");
        assertTrue(source.contains("INSERT OR IGNORE"),
                "the repository must preserve database-level idempotency");
        assertTrue(source.contains("void backup(String filePath)"),
                "the repository must own the database backup entry point");
    }

    @Test
    void sqliteStorageDelegatesPersistenceWithoutJdbcImplementationDetails() throws Exception {
        String source = readSource(
                "src/main/java/studio/trc/bukkit/litesignin/database/storage/SQLiteStorage.java");

        assertTrue(source.contains("playerDataRepository.load(uuid, knownName)"),
                "loading must delegate to PlayerDataRepository");
        assertTrue(source.contains("playerDataRepository.commitSignIn"),
                "sign-in commits must delegate to PlayerDataRepository");
        assertTrue(source.contains("playerDataRepository.updateFields(uuid"),
                "scalar saves must delegate to PlayerDataRepository.updateFields without rewriting history");
        assertTrue(source.contains("playerDataRepository.save(uuid"),
                "history-changing saves must still delegate to PlayerDataRepository.save");
        assertFalse(source.contains("PreparedStatement"),
                "SQLiteStorage must not retain JDBC statement handling");
        assertFalse(source.contains("Connection"),
                "SQLiteStorage must not retain JDBC connection handling");
        assertFalse(source.contains("INSERT INTO"),
                "SQLiteStorage must not retain player-data SQL");
    }

    private static String readSource(String relativePath) throws Exception {
        return Files.readString(Path.of(System.getProperty("user.dir"), relativePath));
    }
}
