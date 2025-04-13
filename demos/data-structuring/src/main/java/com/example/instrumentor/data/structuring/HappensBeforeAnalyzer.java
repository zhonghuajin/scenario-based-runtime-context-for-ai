package com.example.instrumentor.data.structuring;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HappensBeforeAnalyzer {

    static class VectorClock {
        Map<String, Integer> clocks = new HashMap<>();

        public void increment(String threadId) {
            clocks.put(threadId, clocks.getOrDefault(threadId, 0) + 1);
        }

        public void merge(VectorClock other) {
            for (Map.Entry<String, Integer> entry : other.clocks.entrySet()) {
                this.clocks.put(entry.getKey(), Math.max(this.clocks.getOrDefault(entry.getKey(), 0), entry.getValue()));
            }
        }

        public boolean happensBefore(VectorClock other) {
            for (Map.Entry<String, Integer> entry : this.clocks.entrySet()) {
                if (entry.getValue() > other.clocks.getOrDefault(entry.getKey(), 0)) {
                    return false;
                }
            }
            return true;
        }

        public VectorClock copy() {
            VectorClock clone = new VectorClock();
            clone.clocks.putAll(this.clocks);
            return clone;
        }
    }

    static class SyncState {
        String lastThread;
        String lastAction;
        String time;
        VectorClock clock;
        String item; 

        SyncState(String thread, String action, String time, VectorClock clock, String item) {
            this.lastThread = thread;
            this.lastAction = action;
            this.time = time;
            this.clock = clock.copy();
            this.item = item;
        }
    }

    public static JsonObject analyzeEvents(String logData) {
        JsonObject result = new JsonObject();
        JsonArray syncRelations = new JsonArray();
        JsonArray dataRaces = new JsonArray();
        
        JsonObject possibleTaintFlows = new JsonObject();
        possibleTaintFlows.addProperty("description", "These are only potential propagation paths based on happens-before relationships and read/write order analysis. Treating inter-thread communication points as sources and sinks, they can be used for further single-thread or cross-thread taint propagation analysis.");
        JsonArray flowsArray = new JsonArray();

        try (BufferedReader reader = new BufferedReader(new StringReader(logData))) {
            String line;
            Map<String, VectorClock> threadClocks = new HashMap<>();
            Map<String, SyncState> objectSyncStates = new HashMap<>();
            
            Map<String, SyncState> lastWriteStates = new HashMap<>();
            Map<String, Map<String, SyncState>> itemWriteStates = new HashMap<>();
            Map<String, Set<String>> threadReadTaints = new HashMap<>();

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split(", ");
                if (parts.length < 4) continue;

                String time = parts[0];
                String thread = parts[1];
                String action = parts[2];
                String obj = parts[3];
                String item = parts.length > 4 ? parts[4] : "-";

                threadClocks.putIfAbsent(thread, new VectorClock());
                threadReadTaints.putIfAbsent(thread, new HashSet<>());
                
                VectorClock currentClock = threadClocks.get(thread);
                currentClock.increment(thread);

                if (action.contains("SYNC_EXIT") || action.contains("LOCK_RELEASE") ||
                    action.contains("CONDITION_SIGNAL") || action.contains("LATCH_COUNT_DOWN") ||
                    action.contains("TASK_COMPLETE") ||
                    (action.contains("WRITE") && action.contains("volatile"))) {

                    String syncKey = obj + (action.contains("volatile") ? "_volatile" : "");
                    objectSyncStates.put(syncKey, new SyncState(thread, action, time, currentClock, item));
                }
                
                else if (action.contains("SYNC_ENTER") || action.contains("LOCK_ACQUIRE") ||
                         action.contains("CONDITION_AWAIT") || action.contains("LATCH_AWAIT") ||
                         action.contains("FUTURE_GET") ||
                         (action.contains("READ") && action.contains("volatile"))) {

                    String syncKey = obj + (action.contains("volatile") ? "_volatile" : "");
                    SyncState releaseState = objectSyncStates.get(syncKey);

                    if (releaseState != null && !releaseState.lastThread.equals(thread)) {
                        
                        currentClock.merge(releaseState.clock);

                        JsonObject edge = new JsonObject();
                        edge.addProperty("sync_object", syncKey);
                        
                        JsonObject from = new JsonObject();
                        from.addProperty("thread", releaseState.lastThread);
                        from.addProperty("action", releaseState.lastAction.split(" ")[0]);
                        from.addProperty("time", releaseState.time);
                        edge.add("from", from);
                        
                        JsonObject to = new JsonObject();
                        to.addProperty("thread", thread);
                        to.addProperty("action", action.split(" ")[0]);
                        to.addProperty("time", time);
                        edge.add("to", to);
                        
                        edge.addProperty("description", String.format("%s (%s) ----Happens-Before----> %s (%s)", 
                                releaseState.lastThread, from.get("action").getAsString(), 
                                thread, to.get("action").getAsString()));
                        
                        syncRelations.add(edge);
                    }
                }

                if (action.contains("SHARED_VARIABLE")) {
                    String varName = action.contains("field=") ? action.split("field=")[1].split(" ")[0] : obj;

                    if (action.contains("WRITE")) {
                        SyncState currentWriteState = new SyncState(thread, action, time, currentClock, item);
                        
                        SyncState prevWrite = lastWriteStates.get(varName);
                        if (!action.contains("volatile") && prevWrite != null && !prevWrite.lastThread.equals(thread)) {
                            if (!prevWrite.clock.happensBefore(currentClock)) {
                                JsonObject race = new JsonObject();
                                race.addProperty("variable", varName);
                                
                                JsonObject writer1 = new JsonObject();
                                writer1.addProperty("thread", prevWrite.lastThread);
                                writer1.addProperty("time", prevWrite.time);
                                race.add("writer", writer1);
                                
                                JsonObject writer2 = new JsonObject();
                                writer2.addProperty("thread", thread);
                                writer2.addProperty("time", time);
                                race.add("reader", writer2); 
                                
                                race.addProperty("description", String.format("Lack of synchronization between %s (Write) and %s (Write)", 
                                        prevWrite.lastThread, thread));
                                
                                dataRaces.add(race);
                            }
                        }

                        lastWriteStates.put(varName, currentWriteState);
                        itemWriteStates.computeIfAbsent(varName, k -> new HashMap<>()).put(item, currentWriteState);
                        
                        Set<String> currentTaints = threadReadTaints.get(thread);
                        if (!currentTaints.isEmpty()) {
                            JsonObject flow = new JsonObject();
                            flow.addProperty("type", "intra_thread");
                            flow.addProperty("thread", thread);
                            flow.addProperty("time", time);
                            flow.addProperty("target_variable", varName);
                            flow.addProperty("written_item", item);
                            
                            JsonArray sources = new JsonArray();
                            currentTaints.forEach(sources::add);
                            flow.add("possible_source_variables", sources);
                            
                            flow.addProperty("description", String.format("Thread %s wrote to %s, potentially tainted by previous reads: %s", 
                                    thread, varName, currentTaints));
                            flowsArray.add(flow);
                        }
                        
                    } else if (action.contains("READ")) {
                        
                        Map<String, SyncState> varItemWrites = itemWriteStates.get(varName);
                        SyncState actualWrite = (varItemWrites != null) ? varItemWrites.get(item) : null;
                        
                        threadReadTaints.get(thread).add(varName);
                        
                        if (actualWrite != null && !actualWrite.lastThread.equals(thread)) {
                            
                            JsonObject flow = new JsonObject();
                            flow.addProperty("type", "inter_thread");
                            flow.addProperty("variable", varName);
                            flow.addProperty("item", item);
                            
                            JsonObject source = new JsonObject();
                            source.addProperty("thread", actualWrite.lastThread);
                            source.addProperty("time", actualWrite.time);
                            flow.add("source", source);
                            
                            JsonObject sink = new JsonObject();
                            sink.addProperty("thread", thread);
                            sink.addProperty("time", time);
                            flow.add("sink", sink);
                            
                            flow.addProperty("description", String.format("Potential data flow via %s: %s -> %s", 
                                    varName, actualWrite.lastThread, thread));
                            flowsArray.add(flow);
                            
                            if (!action.contains("volatile") && !actualWrite.clock.happensBefore(currentClock)) {
                                JsonObject race = new JsonObject();
                                race.addProperty("variable", varName);
                                
                                JsonObject writer = new JsonObject();
                                writer.addProperty("thread", actualWrite.lastThread);
                                writer.addProperty("time", actualWrite.time);
                                race.add("writer", writer);
                                
                                JsonObject readerNode = new JsonObject();
                                readerNode.addProperty("thread", thread);
                                readerNode.addProperty("time", time);
                                race.add("reader", readerNode);
                                
                                race.addProperty("description", String.format("Lack of synchronization between %s (Write) and %s (Read)", 
                                        actualWrite.lastThread, thread));
                                
                                dataRaces.add(race);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse event log: " + e.getMessage());
        }

        possibleTaintFlows.add("flows", flowsArray);

        result.add("sync_relations", syncRelations);
        result.add("data_races", dataRaces);
        result.add("possible_taint_flows", possibleTaintFlows);
        return result;
    }
}