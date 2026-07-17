package studio.trc.bukkit.litesignin.database;

/**
 * Unrecoverable failure while opening or operating the configured database.
 *
 * <p>The exception is unchecked so storage failures cannot be silently
 * converted into valid-looking empty query results or zero-row updates.</p>
 */
public class DatabaseException extends RuntimeException {
    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
