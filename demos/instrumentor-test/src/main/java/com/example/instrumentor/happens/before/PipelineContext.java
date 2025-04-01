package com.example.instrumentor.happens.before;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PipelineContext {

    private final String pipelineId;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private volatile String currentStage;
    private int processedCount;

    public PipelineContext(String pipelineId) {
        this.pipelineId = pipelineId;
        this.processedCount = 0;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String stage) {
        this.currentStage = stage;
    }

    public void incrementProcessed() {
        processedCount++;
    }

    public int getProcessedCount() {
        return processedCount;
    }
}