package com.nexus.enchants.database;

import com.nexus.enchants.config.DatabaseType;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * A pooled JDBC connection source.
 *
 * <p>Providers hide every dialect detail from the repositories. They are
 * created and closed off the server thread.</p>
 */
public interface DatabaseProvider extends AutoCloseable {

    /**
     * Opens the pool and verifies that the database answers.
     *
     * @throws DatabaseException when the pool cannot be created or verified
     */
    void initialize();

    /**
     * Borrows a pooled connection.
     *
     * <p>The caller owns the connection and must close it, preferably through
     * try-with-resources. Never call this from the server thread.</p>
     *
     * @return a pooled connection
     * @throws SQLException when no connection is available
     */
    Connection getConnection() throws SQLException;

    /**
     * Closes the pool and every pooled connection.
     */
    @Override
    void close();

    /**
     * Returns whether the pool is open and answered its last validation.
     *
     * @return {@code true} when a connection can be borrowed
     */
    boolean isConnected();

    /**
     * Returns the database type served by this provider.
     *
     * @return the database type
     */
    DatabaseType type();

    /**
     * Returns a short, secret-free description for diagnostics.
     *
     * @return the description
     */
    String describe();
}
