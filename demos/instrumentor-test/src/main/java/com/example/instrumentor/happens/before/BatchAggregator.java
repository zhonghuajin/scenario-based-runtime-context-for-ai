package com.example.instrumentor.happens.before;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class BatchAggregator {

    private final int[] results;
    private final boolean[] committed;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicInteger commitCount = new AtomicInteger(0);

    public BatchAggregator(int capacity) {
        this.results = new int[capacity];
        this.committed = new boolean[capacity];
    }

    public void submitResult(int slot, int value) {
        lock.lock();
        try {
            validateSlot(slot);
            results[slot] = value;
            committed[slot] = true;
            commitCount.incrementAndGet();
        } finally {
            lock.unlock();
        }
    }

    public int getResult(int slot) {
        lock.lock();
        try {
            validateSlot(slot);
            if (!committed[slot]) {
                throw new IllegalStateException("Slot " + slot + " has not been committed");
            }
            return results[slot];
        } finally {
            lock.unlock();
        }
    }

    public int[] getAllResults() {
        lock.lock();
        try {
            return results.clone();
        } finally {
            lock.unlock();
        }
    }

    public boolean isFullyCommitted() {
        return commitCount.get() >= results.length;
    }

    public int getCommitCount() {
        return commitCount.get();
    }

    private void validateSlot(int slot) {
        if (slot < 0 || slot >= results.length) {
            throw new IndexOutOfBoundsException("Slot " + slot + " out of range [0, " + results.length + ")");
        }
    }
}