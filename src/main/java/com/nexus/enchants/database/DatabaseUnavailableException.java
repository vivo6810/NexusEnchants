package com.nexus.enchants.database;

/**
 * Thrown when a query is attempted while the database is not ready.
 */
public class DatabaseUnavailableException extends DatabaseException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param reason why the database is unavailable
     */
    public DatabaseUnavailableException(String reason) {
        super("Database is unavailable: " + reason);
    }
}
