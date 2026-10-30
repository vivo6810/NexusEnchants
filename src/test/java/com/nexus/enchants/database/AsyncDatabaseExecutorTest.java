package com.nexus.enchants.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Executor tests: work never runs on the calling thread, the queue is bounded,
 * failures are counted and shutdown drains in-flight work.
 *
 * <p>Every {@code submit} call casts its lambda because {@code Runnable} and
 * {@code SqlTask} are structurally identical and an untyped lambda would be
 * ambiguous.</p>
 */
class AsyncDatabaseExecutorTest {

    private static final Logger LOGGER = Logger.getLogger("NexusEnchantsTest");

    @Test
    @DisplayName("Tasks run on a worker thread, never on the submitting thread")
    void runsOffTheCallingThread() throws Exception {
        try (AsyncDatabaseExecutor executor = new AsyncDatabaseExecutor(1, 16, LOGGER)) {
            CountDownLatch latch = new CountDownLatch(1);
            Thread submitter = Thread.currentThread();
            AtomicInteger sameThread = new AtomicInteger();
            executor.submit("probe", (Runnable) () -> {
                if (Thread.currentThread() == submitter) {
                    sameThread.incrementAndGet();
                }
                latch.countDown();
            });
            assertTrue(latch.await(5, TimeUnit.SECONDS), "the task ran");
            assertEquals(0, sameThread.get(), "the task must not run on the calling thread");
            assertEquals(0, executor.queued());
            assertNotEquals("main", Thread.currentThread().getName());
        }
    }

    @Test
    @DisplayName("Worker threads are named and daemonic")
    void workerThreadsAreDaemon() throws Exception {
        AsyncDatabaseExecutor executor = new AsyncDatabaseExecutor(1, 16, LOGGER);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger daemon = new AtomicInteger();
        AtomicInteger priority = new AtomicInteger(-999);
        executor.submit("probe", (Runnable) () -> {
            daemon.set(Thread.currentThread().isDaemon() ? 1 : 0);
            priority.set(Thread.currentThread().getPriority());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, daemon.get());
        assertEquals(Thread.NORM_PRIORITY - 1, priority.get());
        assertEquals(1, executor.workerCount());
        executor.close();
    }

    @Test
    @DisplayName("A full queue rejects work instead of growing without bound")
    void boundedQueue() throws Exception {
        try (AsyncDatabaseExecutor executor = new AsyncDatabaseExecutor(1, 16, LOGGER)) {
            CountDownLatch blocker = new CountDownLatch(1);
            CountDownLatch started = new CountDownLatch(1);
            assertTrue(executor.submitSql("blocker", () -> {
                started.countDown();
                try {
                    blocker.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(started.await(5, TimeUnit.SECONDS));

            int accepted = 0;
            int rejected = 0;
            for (int index = 0; index < 200; index++) {
                if (executor.submit("filler-" + index, (Runnable) () -> {
                    // No-op: only queue occupancy matters here.
                })) {
                    accepted++;
                } else {
                    rejected++;
                }
            }
            blocker.countDown();

            assertTrue(accepted <= 17, "at most the worker plus the bounded queue accepts work, got " + accepted);
            assertTrue(rejected > 0, "the bounded queue must reject the rest");
            assertEquals(rejected, executor.rejectedCount());
            assertTrue(executor.queueCapacity() >= 16);
        }
    }

    @Test
    @DisplayName("Exceptions are counted and never kill the worker")
    void failuresAreHandled() throws Exception {
        try (AsyncDatabaseExecutor executor = new AsyncDatabaseExecutor(1, 16, LOGGER)) {
            CountDownLatch done = new CountDownLatch(2);
            executor.submitSql("sql-failure", () -> {
                done.countDown();
                throw new SQLException("synthetic failure");
            });
            executor.submit("runtime-failure", (Runnable) () -> {
                done.countDown();
                throw new IllegalStateException("synthetic failure");
            });
            assertTrue(done.await(5, TimeUnit.SECONDS));

            CountDownLatch after = new CountDownLatch(1);
            executor.submit("still-alive", (Runnable) after::countDown);
            assertTrue(after.await(5, TimeUnit.SECONDS), "the worker survived both failures");
            assertEquals(2, executor.failedCount());
            assertEquals(3, executor.submittedCount());
        }
    }

    @Test
    @DisplayName("Shutdown drains pending work and rejects new submissions")
    void shutdownBehaviour() {
        AsyncDatabaseExecutor executor = new AsyncDatabaseExecutor(2, 32, LOGGER);
        AtomicInteger ran = new AtomicInteger();
        for (int index = 0; index < 20; index++) {
            executor.submit("task-" + index, (Runnable) ran::incrementAndGet);
        }
        executor.close(5_000L);
        assertTrue(executor.isShutdown());
        assertEquals(20, ran.get(), "queued work completes before shutdown returns");

        assertFalse(executor.submit("after-shutdown", (Runnable) () -> { }),
                "no work is accepted after shutdown");
        // Closing an already closed executor is a no-op.
        executor.close();
    }

    @Test
    @DisplayName("Close is idempotent and safe without any submissions")
    void closeWithoutWork() {
        AsyncDatabaseExecutor executor = new AsyncDatabaseExecutor(1, 16, LOGGER);
        executor.close();
        executor.close(100L);
        assertTrue(executor.isShutdown());
        assertEquals(0, executor.queued());
    }
}
