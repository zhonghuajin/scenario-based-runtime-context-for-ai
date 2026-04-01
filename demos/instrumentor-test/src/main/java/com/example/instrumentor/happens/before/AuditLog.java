package com.example.instrumentor.happens.before;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AuditLog {

    private final List<String> entries = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean sealed = false;

    public void append(String message) {
        if (sealed) {
            throw new IllegalStateException("AuditLog has been sealed");
        }
        long ts = System.nanoTime();
        String threadName = Thread.currentThread().getName();
        entries.add("[" + ts + "] [" + threadName + "] " + message);
    }

    public void seal() {
        sealed = true;
    }

    public List<String> getEntries() {
        return new ArrayList<>(entries);
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}