package com.example.instrumentor.happens.before;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CompletionTracker {

    private final CountDownLatch latch;
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final long createdAt;

    public CompletionTracker(int expectedTasks) {
        this.latch = new CountDownLatch(expectedTasks);
        this.createdAt = System.nanoTime();
    }

    public void markSuccess() {
        successCount.incrementAndGet();
        latch.countDown();
    }

    public void markFailure() {
        failureCount.incrementAndGet();
        latch.countDown();
    }

    public boolean awaitAll(long timeout, TimeUnit unit) throws InterruptedException {
        return latch.await(timeout, unit);
    }

    public int getSuccessCount() {
        return successCount.get();
    }

    public int getFailureCount() {
        return failureCount.get();
    }

    public int getTotalCompleted() {
        return successCount.get() + failureCount.get();
    }

    public long getElapsedNanos() {
        return System.nanoTime() - createdAt;
    }
}