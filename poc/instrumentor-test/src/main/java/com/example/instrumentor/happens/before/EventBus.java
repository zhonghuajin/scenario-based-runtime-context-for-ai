package com.example.instrumentor.happens.before;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.function.Consumer;

public class EventBus {

    private final Map<String, List<Consumer<Event>>> subscribers = new HashMap<>();
    private final ReadWriteLock subscriberLock = new ReentrantReadWriteLock();
    private final ExecutorService dispatchExecutor;
    private final boolean asyncDispatch;

    public EventBus(boolean asyncDispatch) {
        this.asyncDispatch = asyncDispatch;
        if (asyncDispatch) {
            this.dispatchExecutor = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "EventBus-dispatch");
                t.setDaemon(true);
                return t;
            });
        } else {
            this.dispatchExecutor = null;
        }
    }

    public void subscribe(String eventType, Consumer<Event> handler) {
        subscriberLock.writeLock().lock();
        try {
            subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
        } finally {
            subscriberLock.writeLock().unlock();
        }
    }

    public void unsubscribe(String eventType, Consumer<Event> handler) {
        subscriberLock.writeLock().lock();
        try {
            List<Consumer<Event>> handlers = subscribers.get(eventType);
            if (handlers != null) {
                handlers.remove(handler);
            }
        } finally {
            subscriberLock.writeLock().unlock();
        }
    }

    public void publish(Event event) {
        List<Consumer<Event>> handlers = resolveHandlers(event.getType());
        for (Consumer<Event> handler : handlers) {
            dispatchToHandler(handler, event);
        }
    }

    private List<Consumer<Event>> resolveHandlers(String eventType) {
        subscriberLock.readLock().lock();
        try {
            return subscribers.getOrDefault(eventType, Collections.emptyList());
        } finally {
            subscriberLock.readLock().unlock();
        }
    }

    private void dispatchToHandler(Consumer<Event> handler, Event event) {
        if (asyncDispatch && dispatchExecutor != null) {
            dispatchExecutor.submit(() -> handler.accept(event));
        } else {
            handler.accept(event);
        }
    }

    public void shutdown() {
        if (dispatchExecutor != null) {
            dispatchExecutor.shutdown();
            try {
                if (!dispatchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    dispatchExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                dispatchExecutor.shutdownNow();
            }
        }
    }
}