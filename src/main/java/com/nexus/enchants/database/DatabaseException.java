package com.nexus.enchants.database;

/**
 * Signals that the database could not be used.
 *
 * <p>Callers must degrade gracefully: a database failure never crashes the
 * server and never changes the price a player is charged.</p>
 */
public class DatabaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message explanation
     * @param cause   underlying cause
     */
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates the exception without a cause.
     *
     * @param message explanation
     */
    public DatabaseException(String message) {
        super(message);
    }
}
