package com.example.instrumentor.happens.before;

import java.util.concurrent.*;

public class WorkerCoordinator {

    private final ExecutorService workerPool;
    private final BatchAggregator aggregator;
    private final EventBus eventBus;
    private final CompletionTracker tracker;
    private final AuditLog auditLog;
    private volatile Throwable lastError;

    public WorkerCoordinator(int poolSize, BatchAggregator aggregator,
                             EventBus eventBus, CompletionTracker tracker, AuditLog auditLog) {
        this.workerPool = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "Coordinator-Worker");
            t.setDaemon(true);
            return t;
        });
        this.aggregator = aggregator;
        this.eventBus = eventBus;
        this.tracker = tracker;
        this.auditLog = auditLog;
    }

    /**
     * Submits a task for direct execution. The task result is committed
     * to the aggregator before completion is signalled.
     */
    public void submitTask(int taskId, Callable<Integer> task) {
        workerPool.submit(wrapExecution(taskId, task, false));
    }

    /**
     * Executes a task and publishes the result via the event bus, allowing
     * downstream subscribers to handle result persistence independently.
     * Suitable for decoupled, event-driven architectures.
     */
    public void executeWithNotification(int taskId, Callable<Integer> task) {
        workerPool.submit(wrapExecution(taskId, task, true));
    }

    private Runnable wrapExecution(int taskId, Callable<Integer> task, boolean publishEvent) {
        return () -> {
            boolean success = false;
            try {
                onBeforeExecution(taskId);
                int result = task.call();
                commitResult(taskId, result, publishEvent);
                success = true;
            } catch (Exception e) {
                lastError = e;
                onExecutionError(taskId, e);
            } finally {
                onAfterExecution(taskId, success);
            }
        };
    }

    private void commitResult(int taskId, int result, boolean publishEvent) {
        if (publishEvent) {
            eventBus.publish(new Event("worker.result.computed", new int[]{taskId, result}));
        } else {
            aggregator.submitResult(taskId, result);
        }
    }

    private void onBeforeExecution(int taskId) {
        auditLog.append("STARTED task-" + taskId);
    }

    private void onAfterExecution(int taskId, boolean success) {
        auditLog.append("FINISHED task-" + taskId + " success=" + success);
        if (success) {
            tracker.markSuccess();
        } else {
            tracker.markFailure();
        }
    }

    private void onExecutionError(int taskId, Exception e) {
        auditLog.append("ERROR task-" + taskId + ": " + e.getMessage());
    }

    public void shutdown() {
        workerPool.shutdown();
    }

    public Throwable getLastError() {
        return lastError;
    }
}