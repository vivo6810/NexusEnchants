package com.nexus.enchants.database;

import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bounded worker pool that owns every JDBC operation.
 *
 * <p>Rules enforced here:</p>
 * <ul>
 *     <li>No task ever runs on the server thread.</li>
 *     <li>The queue is bounded; overflow is counted and logged, never retried
 *     forever and never allowed to grow without limit.</li>
 *     <li>Every task is wrapped, so an exception is reported instead of
 *     silently killing a worker.</li>
 *     <li>Shutdown waits for in-flight work with a timeout.</li>
 * </ul>
 */
public final class AsyncDatabaseExecutor implements AutoCloseable {

    /**
     * A task that may throw {@link SQLException}.
     */
    @FunctionalInterface
    public interface SqlTask {

        /**
         * Runs the task.
         *
         * @throws SQLException when the query fails
         */
        void run() throws SQLException;
    }

    private final ThreadPoolExecutor executor;
    private final Logger logger;
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final int queueCapacity;

    /**
     * Creates the executor.
     *
     * @param workers       worker thread count
     * @param queueCapacity bound of the pending task queue
     * @param logger        logger for task failures
     */
    public AsyncDatabaseExecutor(int workers, int queueCapacity, Logger logger) {
        this.logger = logger;
        this.queueCapacity = Math.max(16, queueCapacity);
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "NexusEnchants-DB-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
        // The handler must throw: swallowing the rejection would make
        // ThreadPoolExecutor.execute() return normally and submit() would report
        // a discarded task as accepted.
        RejectedExecutionHandler rejection = (runnable, pool) -> {
            throw new RejectedExecutionException("NexusEnchants database queue is full");
        };
        this.executor = new ThreadPoolExecutor(
                Math.max(1, workers),
                Math.max(1, workers),
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(this.queueCapacity),
                factory,
                rejection);
        this.executor.allowCoreThreadTimeOut(false);
    }

    /**
     * Submits a plain task.
     *
     * @param label short label used in log messages
     * @param task  task to run
     * @return {@code true} when the task was accepted
     */
    public boolean submit(String label, Runnable task) {
        return submitSql(label, task::run);
    }

    /**
     * Submits a JDBC task that may throw {@link SQLException}.
     *
     * <p>The distinct name keeps untyped lambdas unambiguous: {@link Runnable}
     * and {@link SqlTask} have the same shape, so a second overload called
     * {@code submit} would make every lambda call site a compile error.</p>
     *
     * @param label short label used in log messages
     * @param task  task to run
     * @return {@code true} when the task was accepted
     */
    public boolean submitSql(String label, SqlTask task) {
        submitted.incrementAndGet();
        try {
            executor.execute(() -> runGuarded(label, task));
            return true;
        } catch (RuntimeException exception) {
            long count = rejected.incrementAndGet();
            if (count == 1L || count % 100L == 0L) {
                logger.warning("Database queue is full; " + count
                        + " task(s) rejected so far. The server keeps running with degraded persistence.");
            }
            logger.log(Level.FINE, "Rejected database task " + label, exception);
            return false;
        }
    }

    private void runGuarded(String label, SqlTask task) {
        try {
            task.run();
            completed.incrementAndGet();
        } catch (SQLException exception) {
            failed.incrementAndGet();
            logger.log(Level.WARNING, "Database task '" + label + "' failed", exception);
        } catch (RuntimeException exception) {
            failed.incrementAndGet();
            logger.log(Level.SEVERE, "Database task '" + label + "' failed unexpectedly", exception);
        }
    }

    /**
     * Returns the number of tasks waiting in the queue.
     *
     * @return the queue size
     */
    public int queued() {
        return executor.getQueue().size();
    }

    /**
     * Returns the number of worker threads.
     *
     * @return the pool size
     */
    public int workerCount() {
        return executor.getCorePoolSize();
    }

    /**
     * Returns the configured queue bound.
     *
     * @return the maximum number of queued tasks
     */
    public int queueCapacity() {
        return queueCapacity;
    }

    /**
     * Returns how many tasks were submitted.
     *
     * @return the submitted count
     */
    public long submittedCount() {
        return submitted.get();
    }

    /**
     * Returns how many tasks completed successfully.
     *
     * @return the completed count
     */
    public long completedCount() {
        return completed.get();
    }

    /**
     * Returns how many tasks failed.
     *
     * @return the failed count
     */
    public long failedCount() {
        return failed.get();
    }

    /**
     * Returns how many tasks were rejected because the queue was full.
     *
     * @return the rejected count
     */
    public long rejectedCount() {
        return rejected.get();
    }

    /**
     * Returns whether the executor has been shut down.
     *
     * @return {@code true} when shut down
     */
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    @Override
    public void close() {
        close(5_000L);
    }

    /**
     * Shuts the pool down and waits for in-flight work.
     *
     * @param timeoutMillis how long to wait before forcing a shutdown
     */
    public void close(long timeoutMillis) {
        if (executor.isShutdown()) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                int pending = executor.getQueue().size();
                logger.warning("Database pool did not finish " + pending + " task(s) within "
                        + timeoutMillis + " ms; forcing shutdown.");
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
