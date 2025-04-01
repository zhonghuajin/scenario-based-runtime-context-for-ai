package com.example.instrumentor.happens.before;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

public class MarketDataCache {

    private final Map<String, Double> store = new HashMap<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public void computeAndCache(String symbol, Supplier<Double> computation) {
        Double value = computation.get();
        rwLock.writeLock().lock();
        try {
            store.put(symbol, value);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public Double getIfCached(String symbol) {
        rwLock.readLock().lock();
        try {
            return store.get(symbol);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Double getOrCompute(String symbol, Supplier<Double> computation) {
        rwLock.readLock().lock();
        try {
            Double cached = store.get(symbol);
            if (cached != null) return cached;
        } finally {
            rwLock.readLock().unlock();
        }

        rwLock.writeLock().lock();
        try {
            Double cached = store.get(symbol);
            if (cached != null) return cached;
            Double computed = computation.get();
            store.put(symbol, computed);
            return computed;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public int size() {
        rwLock.readLock().lock();
        try {
            return store.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }
}