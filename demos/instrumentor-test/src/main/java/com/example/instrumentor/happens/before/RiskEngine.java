package com.example.instrumentor.happens.before;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RiskEngine {

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Map<String, Double> marketPrices = new HashMap<>();
    private final Map<String, Double> riskScores = new HashMap<>();
    private volatile boolean calibrated = false;

    public void calibrate(Map<String, Double> prices) {
        rwLock.writeLock().lock();
        try {
            marketPrices.clear();
            marketPrices.putAll(prices);
            calibrated = true;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public double computeRisk(String symbol, double quantity) {
        if (!calibrated) {
            throw new IllegalStateException("RiskEngine has not been calibrated");
        }
        rwLock.readLock().lock();
        try {
            Double price = marketPrices.get(symbol);
            if (price == null) return 0.0;
            return price * quantity * 0.015;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void recordRisk(String tradeId, double riskValue) {
        rwLock.writeLock().lock();
        try {
            riskScores.put(tradeId, riskValue);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public Map<String, Double> getAllRiskScores() {
        rwLock.readLock().lock();
        try {
            return new HashMap<>(riskScores);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public boolean isCalibrated() {
        return calibrated;
    }
}