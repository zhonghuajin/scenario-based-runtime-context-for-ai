package com.example.instrumentor.monitor;

import com.example.instrumentor.InstrumentLog;
import com.example.instrumentor.LogLifecycleHook;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class LogMonitorServer implements LogLifecycleHook {

    private static final int DEFAULT_PORT = 19898;
    private static final String PROP_PORT = "instrumentor.monitor.port";
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    

    @Override
    public void onFirstLog() {
        int port = Integer.getInteger(PROP_PORT, DEFAULT_PORT);
        Thread serverThread = new Thread(() -> startHttpServer(port), "LogMonitor-HttpServer");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    

    private void startHttpServer(int initialPort) {
        int port = initialPort;
        int maxTries = 100; 
        HttpServer server = null;

        
        for (int i = 0; i < maxTries; i++) {
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                break; 
            } catch (BindException e) {
                
                port++;
            } catch (IOException e) {
                
                System.err.printf("[LogMonitor] Exception occurred while starting HTTP service: %s%n", e.getMessage());
                e.printStackTrace();
                return;
            }
        }

        if (server == null) {
            System.err.printf("[LogMonitor] Unable to start HTTP service, port range %d - %d all occupied.%n", initialPort, initialPort + maxTries - 1);
            return;
        }

        try {
            server.createContext("/clear", this::handleClear);
            server.createContext("/flush", this::handleFlush);
            server.createContext("/status", this::handleStatus);
            server.setExecutor(null);
            server.start();
            System.out.printf("[LogMonitor] HTTP monitoring service started: http://localhost:%d%n", port);
        } catch (Exception e) {
            System.err.printf("[LogMonitor] Unable to configure or start HTTP service: %s%n", e.getMessage());
            e.printStackTrace();
        }
    }

    

    private void handleClear(HttpExchange exchange) throws IOException {
        InstrumentLog.clear();
        sendTextResponse(exchange, 200, "[LogMonitor] Logs cleared.\n");
    }

    

    private void handleFlush(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
        String customFile = params.get("file");
        
        String logFilePath, eventFilePath;
        if (customFile != null && !customFile.isEmpty()) {
            logFilePath = customFile;
            int dotIndex = customFile.lastIndexOf('.');
            eventFilePath = dotIndex > 0 ? customFile.substring(0, dotIndex) + "-events" + customFile.substring(dotIndex) : customFile + "-events";
        } else {
            String ts = LocalDateTime.now().format(TS_FMT);
            logFilePath = "instrumentor-log-" + ts + ".txt";
            eventFilePath = "instrumentor-events-" + ts + ".txt";
        }

        LinkedHashMap<Long, List<Integer>> logSnapshot = InstrumentLog.getOrderedSnapshot();
        String logContent = formatLogSnapshot(logSnapshot);
        Path logPath = Path.of(logFilePath);
        Files.writeString(logPath, logContent, StandardCharsets.UTF_8);

        Map<Integer, String> dict = loadDictionary();
        List<InstrumentLog.ThreadEventBuffer> buffers = InstrumentLog.getAllEventBuffers();
        String eventContent = formatEventSnapshot(buffers, dict);
        Path eventPath = Path.of(eventFilePath);
        Files.writeString(eventPath, eventContent, StandardCharsets.UTF_8);

        StringBuilder resp = new StringBuilder();
        resp.append("[LogMonitor] Logs successfully written to files.\n\n");
        resp.append("[Basic Log File]: ").append(logPath.toAbsolutePath()).append("\n");
        resp.append("[Event Log File (AI-Optimized)]: ").append(eventPath.toAbsolutePath()).append("\n");
        sendTextResponse(exchange, 200, resp.toString());
    }

    

    private void handleStatus(HttpExchange exchange) throws IOException {
        LinkedHashMap<Long, List<Integer>> logSnapshot = InstrumentLog.getOrderedSnapshot();
        List<InstrumentLog.ThreadEventBuffer> buffers = InstrumentLog.getAllEventBuffers();
        int totalLogs = countTotalLogs(logSnapshot);
        int totalEvents = countTotalEvents(buffers);
        List<Long> keyOrder = InstrumentLog.getThreadOrder();

        StringBuilder sb = new StringBuilder();
        sb.append("[LogMonitor] Current Status\n");
        sb.append("  Total Threads  : ").append(keyOrder.size()).append("\n");
        sb.append("  Total Basic Log Entries: ").append(totalLogs).append("\n");
        sb.append("  Total Event Log Entries: ").append(totalEvents).append("\n");
        sendTextResponse(exchange, 200, sb.toString());
    }

    

    private Map<Integer, String> loadDictionary() {
        Map<Integer, String> dict = new HashMap<>();
        try {
            Path dictPath = Path.of("event_dictionary.txt");
            if (Files.exists(dictPath)) {
                for (String line : Files.readAllLines(dictPath)) {
                    int idx = line.indexOf('=');
                    if (idx > 0) dict.put(Integer.parseInt(line.substring(0, idx)), line.substring(idx + 1));
                }
            }
        } catch (Exception e) {
            System.err.println("[LogMonitor] Failed to load dictionary: " + e.getMessage());
        }
        return dict;
    }

    
    private String formatLogSnapshot(LinkedHashMap<Long, List<Integer>> snapshot) {
        StringBuilder sb = new StringBuilder();

        // 1. In-memory deduplication: group threads by their canonical key
        //    (the sorted, distinct set of block IDs they visited)
        LinkedHashMap<String, List<Map.Entry<Long, List<Integer>>>> groups = new LinkedHashMap<>();
        for (Map.Entry<Long, List<Integer>> entry : snapshot.entrySet()) {
            String canonicalKey = entry.getValue().stream()
                    .distinct()
                    .sorted()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            groups.computeIfAbsent(canonicalKey, k -> new ArrayList<>()).add(entry);
        }

        // 2. Write header with deduplication stats
        int originalCount = snapshot.size();
        int dedupedCount = groups.size();
        sb.append("# InstrumentLog (Deduplicated) @ ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        sb.append("# Original thread count: ").append(originalCount)
          .append(", Deduplicated group count: ").append(dedupedCount).append("\n\n");

        // 3. Output one representative thread per group
        int order = 1;
        for (Map.Entry<String, List<Map.Entry<Long, List<Integer>>>> groupEntry : groups.entrySet()) {
            List<Map.Entry<Long, List<Integer>>> group = groupEntry.getValue();
            Map.Entry<Long, List<Integer>> representative = group.get(0);
            long threadId = representative.getKey();
            List<Integer> logs = representative.getValue();

            sb.append(String.format("[Thread-%d] (Group Order: #%d, Count: %d)", threadId, order++, logs.size()));

            if (group.size() > 1) {
                String mergedThreads = group.stream()
                        .map(e -> "Thread-" + e.getKey())
                        .collect(Collectors.joining(", "));
                sb.append(String.format("  # Merged from %d threads: %s", group.size(), mergedThreads));
            }
            sb.append("\n");

            if (!logs.isEmpty()) {
                sb.append("  ");
                for (int i = 0; i < logs.size(); i++) {
                    if (i > 0) sb.append(" -> ");
                    sb.append(logs.get(i));
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    
    private static class EventRecord implements Comparable<EventRecord> {
        long threadId;
        long nanoTime;
        int eventId;
        int objId;
        int itemId;
        String actionName;

        public EventRecord(long threadId, long nanoTime, int eventId, int objId, int itemId, String actionName) {
            this.threadId = threadId;
            this.nanoTime = nanoTime;
            this.eventId = eventId;
            this.objId = objId;
            this.itemId = itemId;
            this.actionName = actionName;
        }

        @Override
        public int compareTo(EventRecord o) {
            return Long.compare(this.nanoTime, o.nanoTime);
        }
    }

    
    private String formatEventSnapshot(List<InstrumentLog.ThreadEventBuffer> buffers, Map<Integer, String> dict) {
        
        List<EventRecord> allEvents = new ArrayList<>();
        long minTime = Long.MAX_VALUE;
        
        
        Map<Integer, Set<Long>> itemThreadMap = new HashMap<>();

        for (InstrumentLog.ThreadEventBuffer buf : buffers) {
            for (int i = 0; i < buf.count; i++) {
                long time = buf.nanoTimes[i];
                if (time < minTime) minTime = time;
                
                int eventId = buf.eventIds[i];
                int objId = buf.shareObjectIds[i];
                int itemId = buf.itemIds[i];
                String action = dict.getOrDefault(eventId, "EVT_" + eventId);
                
                allEvents.add(new EventRecord(buf.threadId, time, eventId, objId, itemId, action));
                
                if (itemId != 0) {
                    itemThreadMap.computeIfAbsent(itemId, k -> new HashSet<>()).add(buf.threadId);
                }
            }
        }
        if (minTime == Long.MAX_VALUE) minTime = 0;

        
        Map<Integer, String> objMap = new LinkedHashMap<>();
        Map<Integer, String> itemMap = new LinkedHashMap<>();
        int objCounter = 1, itemCounter = 1;

        for (EventRecord record : allEvents) {
            if (record.objId != 0 && !objMap.containsKey(record.objId)) {
                objMap.put(record.objId, "O" + objCounter++);
            }
            if (record.itemId != 0 && !itemMap.containsKey(record.itemId)) {
                itemMap.put(record.itemId, "I" + itemCounter++);
            }
        }

        
        Collections.sort(allEvents);

        
        List<EventRecord> compressedEvents = new ArrayList<>();
        Map<String, EventRecord> lastActionMap = new HashMap<>(); 

        for (EventRecord current : allEvents) {
            
            if (current.itemId != 0) {
                Set<Long> accessingThreads = itemThreadMap.get(current.itemId);
                if (accessingThreads != null && accessingThreads.size() <= 1) {
                    continue; 
                }
            }

            
            String stateKey = current.threadId + "_" + current.itemId + "_" + current.actionName;
            EventRecord last = lastActionMap.get(stateKey);
            
            
            if (last != null && last.actionName.equals(current.actionName)) {
                continue;
            }
            
            lastActionMap.put(stateKey, current);
            compressedEvents.add(current);
        }

        
        StringBuilder sb = new StringBuilder();
        sb.append("# AI-Optimized Event Log Dump @ ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        sb.append("# BaseTime: ").append(minTime).append("\n");
        sb.append("# Format: DeltaTime, Thread, Action, Object, Item\n");
        sb.append("# Field Descriptions:\n");
        sb.append("#   - DeltaTime: Time elapsed (in nanoseconds) since the first recorded event.\n");
        sb.append("#   - Thread: The identifier of the thread performing the action.\n");
        sb.append("#   - Action: The operation performed (e.g., READ, WRITE, SYNC_ENTER).\n");
        sb.append("#   - Object: The shared resource the thread is operating on (e.g., lock, queue, shared instance).\n");
        sb.append("#   - Item: The specific data object being passed, read, or written during the operation.\n");
        sb.append("# Note: Thread-local items are filtered. Redundant consecutive actions are merged.\n\n");

        for (EventRecord record : compressedEvents) {
            long deltaTime = record.nanoTime - minTime;
            String objAlias = record.objId == 0 ? "-" : objMap.get(record.objId);
            String itemAlias = record.itemId == 0 ? "-" : itemMap.get(record.itemId);

            sb.append(deltaTime).append(", ")
              .append("T").append(record.threadId).append(", ")
              .append(record.actionName).append(", ")
              .append(objAlias).append(", ")
              .append(itemAlias).append("\n");
        }

        return sb.toString();
    }

    private int countTotalLogs(LinkedHashMap<Long, List<Integer>> snapshot) {
        int total = 0;
        for (List<Integer> list : snapshot.values()) total += list.size();
        return total;
    }

    private int countTotalEvents(List<InstrumentLog.ThreadEventBuffer> buffers) {
        int total = 0;
        for (InstrumentLog.ThreadEventBuffer buf : buffers) total += buf.count;
        return total;
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) return params;
        for (String pair : rawQuery.split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return params;
    }

    private void sendTextResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
