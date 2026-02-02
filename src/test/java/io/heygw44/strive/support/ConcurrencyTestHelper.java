package io.heygw44.strive.support;

import org.slf4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ConcurrencyTestHelper {

    private ConcurrencyTestHelper() {
    }

    public static ExecutionResult runConcurrently(
        List<? extends Runnable> tasks,
        int poolSize,
        Duration startTimeout,
        Duration doneTimeout
    ) {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("tasks must not be empty");
        }
        if (poolSize <= 0) {
            throw new IllegalArgumentException("poolSize must be positive");
        }

        int taskCount = tasks.size();
        int threadPoolSize = Math.min(poolSize, taskCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(taskCount);
        Queue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (Runnable task : tasks) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    if (!start.await(startTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                        errors.add(new TimeoutException("start latch timeout"));
                        return;
                    }
                    task.run();
                } catch (Throwable ex) {
                    errors.add(ex);
                } finally {
                    done.countDown();
                }
            });
        }

        boolean readyOk = awaitLatch(ready, startTimeout, errors, "ready latch timeout");
        start.countDown();
        boolean doneOk = awaitLatch(done, doneTimeout, errors, "done latch timeout");

        shutdownExecutor(executor, !doneOk);

        return new ExecutionResult(errors, taskCount, readyOk, doneOk);
    }

    private static boolean awaitLatch(
        CountDownLatch latch,
        Duration timeout,
        Queue<Throwable> errors,
        String timeoutMessage
    ) {
        try {
            boolean completed = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                errors.add(new TimeoutException(timeoutMessage));
            }
            return completed;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            errors.add(ex);
            return false;
        }
    }

    private static void shutdownExecutor(ExecutorService executor, boolean force) {
        if (force) {
            executor.shutdownNow();
        } else {
            executor.shutdown();
        }
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    public static final class ExecutionResult {
        private final Queue<Throwable> errors;
        private final int taskCount;
        private final boolean ready;
        private final boolean completed;

        private ExecutionResult(Queue<Throwable> errors, int taskCount, boolean ready, boolean completed) {
            this.errors = errors;
            this.taskCount = taskCount;
            this.ready = ready;
            this.completed = completed;
        }

        public Queue<Throwable> errors() {
            return errors;
        }

        public int taskCount() {
            return taskCount;
        }

        public boolean ready() {
            return ready;
        }

        public boolean completed() {
            return completed;
        }

        public void logErrors(Logger log) {
            for (Throwable error : errors) {
                log.error("Unexpected error during concurrent execution", error);
            }
        }
    }
}
