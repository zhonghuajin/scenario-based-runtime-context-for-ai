package com.example.instrumentor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;


public class InstrumentLog {

    

    
    private static final ConcurrentHashMap<Long, LinkedHashSet<Integer>> BLOCK_MAP =
            new ConcurrentHashMap<>();

    
    public static class ThreadEventBuffer {
        public final long threadId;
        public int[] eventIds;
        public int[] shareObjectIds;
        public int[] itemIds;
        public long[] nanoTimes;
        public int count;

        public ThreadEventBuffer(long threadId) {
            this.threadId = threadId;
            int initialCapacity = 2048; 
            this.eventIds = new int[initialCapacity];
            this.shareObjectIds = new int[initialCapacity];
            this.itemIds = new int[initialCapacity];
            this.nanoTimes = new long[initialCapacity];
            this.count = 0;
        }

        public void append(int eventId, int shareObjectID, int itemID, long nanoTime) {
            if (count == eventIds.length) {
                int newCap = eventIds.length * 2;
                eventIds = Arrays.copyOf(eventIds, newCap);
                shareObjectIds = Arrays.copyOf(shareObjectIds, newCap);
                itemIds = Arrays.copyOf(itemIds, newCap);
                nanoTimes = Arrays.copyOf(nanoTimes, newCap);
            }
            eventIds[count] = eventId;
            shareObjectIds[count] = shareObjectID;
            itemIds[count] = itemID;
            nanoTimes[count] = nanoTime;
            count++;
        }
    }

    
    private static final ConcurrentLinkedQueue<ThreadEventBuffer> ALL_BUFFERS = new ConcurrentLinkedQueue<>();

    
    private static final ThreadLocal<ThreadEventBuffer> LOCAL_BUFFER = ThreadLocal.withInitial(() -> {
        long tid = Thread.currentThread().getId();
        ThreadEventBuffer buffer = new ThreadEventBuffer(tid);
        ALL_BUFFERS.add(buffer);
        registerThreadOrder(tid);
        return buffer;
    });

    
    private static final List<Long> KEY_ORDER = Collections.synchronizedList(new ArrayList<>());

    
    private static final AtomicBoolean FIRST_LOG = new AtomicBoolean(true);

    

    
    public static void staining(int id) {
        checkFirstLog();
        long threadId = Thread.currentThread().getId();
        LinkedHashSet<Integer> set = BLOCK_MAP.computeIfAbsent(threadId, k -> {
            registerThreadOrder(k);
            return new LinkedHashSet<>();
        });
        synchronized (set) {
            set.add(id);
        }
    }

    
    public static void staining(int eventId, int shareObjectID, int itemID, long nanoTime) {
        checkFirstLog();
        LOCAL_BUFFER.get().append(eventId, shareObjectID, itemID, nanoTime);
    }

    public static int getObjectHash(Object obj) {
        return obj == null ? 0 : System.identityHashCode(obj);
    }

    private static void checkFirstLog() {
        if (FIRST_LOG.compareAndSet(true, false)) {
            fireFirstLogHooks();
        }
    }

    private static void registerThreadOrder(long threadId) {
        synchronized (KEY_ORDER) {
            if (!KEY_ORDER.contains(threadId)) {
                KEY_ORDER.add(threadId);
            }
        }
    }

    private static void fireFirstLogHooks() {
        try {
            ServiceLoader<LogLifecycleHook> loader =
                    ServiceLoader.load(LogLifecycleHook.class, InstrumentLog.class.getClassLoader());
            for (LogLifecycleHook hook : loader) {
                try {
                    hook.onFirstLog();
                } catch (Exception e) {
                    System.err.println("[InstrumentLog] Failed to invoke hook: " + hook.getClass().getName());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            
        }
    }

    

    
    public static class FutureHolder {
        private volatile Object ref;
        public void set(Object f) { this.ref = f; }
        public Object get() { return ref; }
    }

    

    
    public static <T> Future<T> trackedSubmit(
            ExecutorService exec, Callable<T> task,
            int submitEventId, int taskCompleteEventId) {
        staining(submitEventId, System.identityHashCode(exec), 0, System.nanoTime());
        FutureHolder holder = new FutureHolder();
        Future<T> future = exec.submit(() -> {
            try {
                return task.call();
            } finally {
                emitTaskComplete(holder, taskCompleteEventId);
            }
        });
        holder.set(future);
        return future;
    }

    

    public static Future<?> trackedSubmit(
            ExecutorService exec, Runnable task,
            int submitEventId, int taskCompleteEventId) {
        staining(submitEventId, System.identityHashCode(exec), 0, System.nanoTime());
        FutureHolder holder = new FutureHolder();
        Future<?> future = exec.submit(() -> {
            try {
                task.run();
            } finally {
                emitTaskComplete(holder, taskCompleteEventId);
            }
        });
        holder.set(future);
        return future;
    }

    

    public static <T> Future<T> trackedSubmitWithResult(
            ExecutorService exec, Runnable task, T result,
            int submitEventId, int taskCompleteEventId) {
        staining(submitEventId, System.identityHashCode(exec), 0, System.nanoTime());
        FutureHolder holder = new FutureHolder();
        Future<T> future = exec.submit(() -> {
            try {
                task.run();
            } finally {
                emitTaskComplete(holder, taskCompleteEventId);
            }
        }, result);
        holder.set(future);
        return future;
    }

    

    public static <T> CompletableFuture<T> trackedSupplyAsync(
            Supplier<T> supplier, int taskCompleteEventId) {
        FutureHolder holder = new FutureHolder();
        CompletableFuture<T> cf = CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } finally {
                emitTaskComplete(holder, taskCompleteEventId);
            }
        });
        holder.set(cf);
        return cf;
    }

    public static <T> CompletableFuture<T> trackedSupplyAsync(
            Supplier<T> supplier, Executor executor, int taskCompleteEventId) {
        FutureHolder holder = new FutureHolder();
        CompletableFuture<T> cf = CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } finally {
                emitTaskComplete(holder, taskCompleteEventId);
            }
        }, executor);
        holder.set(cf);
        return cf;
    }

    

    public static CompletableFuture<Void> trackedRunAsync(
            Runnable action, int taskCompleteEventId) {
        FutureHolder holder = new FutureHolder();
        CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
            try {
                action.run();
            } finally {
                emitTaskComplete(holder, taskCompleteEventId);
            }
        });
        holder.set(cf);
        return cf;
    }

    public static CompletableFuture<Void> trackedRunAsync(
            Runnable action, Executor executor, int taskCompleteEventId) {
        FutureHolder holder = new FutureHolder();
        CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
            try {
                action.run();
            } finally {
                emitTaskComplete(holder, taskCompleteEventId);
            }
        }, executor);
        holder.set(cf);
        return cf;
    }

    

    private static void emitTaskComplete(FutureHolder holder, int eventId) {
        
        
        Object f = holder.get();
        if (f == null) {
            for (int i = 0; i < 200; i++) {
                Thread.onSpinWait();
                f = holder.get();
                if (f != null) break;
            }
        }
        staining(eventId, f != null ? System.identityHashCode(f) : 0, 0, System.nanoTime());
    }

    

    public static List<Long> getThreadOrder() {
        synchronized (KEY_ORDER) {
            return new ArrayList<>(KEY_ORDER);
        }
    }

    public static int getTotalOldLogCount() {
        int total = 0;
        for (LinkedHashSet<Integer> set : BLOCK_MAP.values()) {
            synchronized (set) {
                total += set.size();
            }
        }
        return total;
    }

    public static int getTotalEventCount() {
        int total = 0;
        for (ThreadEventBuffer buffer : ALL_BUFFERS) {
            total += buffer.count;
        }
        return total;
    }

    

    
    private static Map<Integer, String> loadDictionary() {
        Map<Integer, String> dict = new HashMap<>();
        try {
            Path dictPath = Path.of("event_dictionary.txt");
            if (Files.exists(dictPath)) {
                List<String> lines = Files.readAllLines(dictPath);
                for (String line : lines) {
                    int idx = line.indexOf('=');
                    if (idx > 0) {
                        int id = Integer.parseInt(line.substring(0, idx));
                        String msg = line.substring(idx + 1);
                        dict.put(id, msg);
                    }
                }
            } else {
                System.err.println("[InstrumentLog] Warning: event_dictionary.txt not found.");
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("[InstrumentLog] Failed to load dictionary: " + e.getMessage());
        }
        return dict;
    }

    public static void dump() {
        Map<Integer, String> dict = loadDictionary();
        StringBuilder sb = new StringBuilder();

        List<Long> threadOrder = getThreadOrder();

        sb.append("========== InstrumentLog Dump ==========\n");
        int order = 1;
        int totalOldLogs = getTotalOldLogCount();
        int totalNewEvents = getTotalEventCount();

        
        Map<Long, ThreadEventBuffer> bufferMap = new HashMap<>();
        for (ThreadEventBuffer buf : ALL_BUFFERS) {
            bufferMap.put(buf.threadId, buf);
        }

        for (Long tid : threadOrder) {
            sb.append(String.format(">>> [Thread ID: %d] (Appearance Order: #%d)\n", tid, order++));

            
            LinkedHashSet<Integer> oldSet = BLOCK_MAP.get(tid);
            if (oldSet != null && !oldSet.isEmpty()) {
                List<Integer> oldLogs;
                synchronized (oldSet) {
                    oldLogs = new ArrayList<>(oldSet);
                }
                sb.append(String.format("  [Old Log Count: %d]\n    ", oldLogs.size()));
                for (int i = 0; i < oldLogs.size(); i++) {
                    if (i > 0) sb.append(" -> ");
                    sb.append(oldLogs.get(i));
                }
                sb.append("\n");
            }

            
            ThreadEventBuffer buf = bufferMap.get(tid);
            if (buf != null && buf.count > 0) {
                sb.append(String.format("  [New Event Count: %d]\n", buf.count));
                for (int i = 0; i < buf.count; i++) {
                    int eventId = buf.eventIds[i];
                    String eventInfo = dict.getOrDefault(eventId, "UNKNOWN_EVENT_ID_" + eventId);
                    sb.append(String.format("    [Thread-%d] time=%d | %s | shareObjectID=%d | itemID=%d\n",
                            tid, buf.nanoTimes[i], eventInfo, buf.shareObjectIds[i], buf.itemIds[i]));
                }
            }
            sb.append("\n");
        }

        sb.append("========================================\n");
        sb.append(String.format("Total Threads: %d, Total Old Logs: %d, Total New Events: %d\n",
                threadOrder.size(), totalOldLogs, totalNewEvents));

        System.out.print(sb);
    }

    

    public static void clear() {
        BLOCK_MAP.clear();
        ALL_BUFFERS.clear();
        
        LOCAL_BUFFER.remove();
        synchronized (KEY_ORDER) {
            KEY_ORDER.clear();
        }
    }

    

    
    public static List<ThreadEventBuffer> getAllEventBuffers() {
        return new ArrayList<>(ALL_BUFFERS);
    }

    
    public static LinkedHashMap<Long, List<Integer>> getOrderedSnapshot() {
        LinkedHashMap<Long, List<Integer>> snapshot = new LinkedHashMap<>();
        synchronized (KEY_ORDER) {
            for (Long tid : KEY_ORDER) {
                LinkedHashSet<Integer> set = BLOCK_MAP.get(tid);
                if (set != null) {
                    synchronized (set) {
                        snapshot.put(tid, new ArrayList<>(set));
                    }
                }
            }
        }
        return snapshot;
    }
}
