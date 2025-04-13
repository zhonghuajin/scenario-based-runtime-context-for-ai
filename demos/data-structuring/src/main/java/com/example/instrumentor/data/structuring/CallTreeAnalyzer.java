package com.example.instrumentor.data.structuring;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CallTreeAnalyzer {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public static String analyze(String jsonInput, String targetThread) {
        try {
            JsonObject root = JsonParser.parseString(jsonInput).getAsJsonObject();
            JsonArray threads = root.has("threads") && root.get("threads").isJsonArray()
                    ? root.getAsJsonArray("threads")
                    : null;

            if (threads == null || threads.size() == 0) {
                System.err.println("No threads data found");
                return jsonInput;
            }

            List<ThreadAnalysis> analyses = new ArrayList<>();

            for (JsonElement elem : threads) {
                JsonObject td = elem.getAsJsonObject();
                String name = getAsString(td, "name");
                if (targetThread != null && !targetThread.equals(name))
                    continue;

                System.out.println("[Analysis] Thread: " + name);
                int order = getAsInt(td, "order", 0);

                List<Integer> trace = extractBlockTrace(td);

                Map<Integer, String> blockToSig = new HashMap<>();
                Map<String, String> sigToSource = new LinkedHashMap<>();
                Map<String, String> sigToFile = new HashMap<>();
                buildMappings(td, blockToSig, sigToSource, sigToFile);

                CallNode tree = buildCallTree(trace, blockToSig, sigToSource);
                attachMetadata(tree, sigToSource, sigToFile);

                analyses.add(new ThreadAnalysis(name, order, trace, tree));
            }

            return generateJson(analyses);

        } catch (Exception e) {
            System.err.println("Call tree analysis failed: " + e.getMessage());
            e.printStackTrace();
            return jsonInput;
        }
    }

    private static List<Integer> extractBlockTrace(JsonObject threadData) {
        List<Integer> trace = new ArrayList<>();
        if (!threadData.has("block_trace") || !threadData.get("block_trace").isJsonArray()) {
            return extractBlockTraceFromSources(threadData);
        }

        JsonArray raw = threadData.getAsJsonArray("block_trace");
        for (JsonElement e : raw) {
            if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                trace.add(e.getAsInt());
            }
        }
        return trace;
    }

    private static List<Integer> extractBlockTraceFromSources(JsonObject threadData) {
        List<Integer> trace = new ArrayList<>();
        if (!threadData.has("files") || !threadData.get("files").isJsonArray())
            return trace;

        Pattern pat = Pattern.compile("\\[Executed Block ID: (\\d+)");
        JsonArray files = threadData.getAsJsonArray("files");
        for (JsonElement fileElem : files) {
            JsonObject file = fileElem.getAsJsonObject();
            if (!file.has("methods") || !file.get("methods").isJsonArray())
                continue;
            JsonArray methods = file.getAsJsonArray("methods");
            for (JsonElement methodElem : methods) {
                JsonObject method = methodElem.getAsJsonObject();
                String source = getAsString(method, "source");
                if (source == null || source.trim().isEmpty())
                    continue;
                Matcher m = pat.matcher(source);
                while (m.find()) {
                    trace.add(Integer.parseInt(m.group(1)));
                }
            }
        }
        return trace;
    }

    private static void buildMappings(JsonObject threadData,
            Map<Integer, String> blockToSig,
            Map<String, String> sigToSource,
            Map<String, String> sigToFile) {
        Pattern pat = Pattern.compile("\\[Executed Block ID: (\\d+)");
        if (!threadData.has("files") || !threadData.get("files").isJsonArray())
            return;

        JsonArray files = threadData.getAsJsonArray("files");
        for (JsonElement fileElem : files) {
            JsonObject file = fileElem.getAsJsonObject();
            String filePath = getAsString(file, "path");

            if (!file.has("methods") || !file.get("methods").isJsonArray())
                continue;
            JsonArray methods = file.getAsJsonArray("methods");

            for (JsonElement methodElem : methods) {
                JsonObject method = methodElem.getAsJsonObject();
                String source = getAsString(method, "source");
                if (source == null || source.trim().isEmpty())
                    continue;

                String sig = extractSignature(source);

                Matcher m = pat.matcher(source);
                while (m.find()) {
                    blockToSig.put(Integer.parseInt(m.group(1)), sig);
                }

                sigToSource.put(sig, source);
                sigToFile.put(sig, filePath);
            }
        }
    }

    private static String extractSignature(String source) {
        if (source == null || source.trim().isEmpty())
            return "<unknown>";

        String[] lines = source.split("\\n");
        List<String> parts = new ArrayList<>();

        for (String line : lines) {
            String cleaned = line.trim()
                    .replaceAll("//\\s*\\[Executed Block ID:.*?\\]", "")
                    .replaceAll("//.*", "")
                    .trim();
            if (cleaned.isEmpty())
                continue;

            if (cleaned.startsWith("@")) {
                parts.add(cleaned);
                continue;
            }

            String decl = cleaned.replaceAll("\\{.*", "").trim();
            if (!decl.isEmpty())
                parts.add(decl);
            break;
        }

        String sig = String.join(" ", parts).trim();
        if (sig.isEmpty())
            return "<instance-initializer>";
        if (sig.equals("static"))
            return "<static-initializer>";
        return sig;
    }

    private static CallNode buildCallTree(List<Integer> trace,
            Map<Integer, String> blockToSig,
            Map<String, String> sigToSource) {
        CallNode root = new CallNode("ROOT");
        CallNode current = root;
        String currentSig = null;

        for (int blockId : trace) {
            String targetSig = blockToSig.get(blockId);
            if (targetSig == null)
                continue;

            if (currentSig == null) {
                CallNode node = new CallNode(targetSig);
                node.parent = current;
                current.children.add(node);
                current = node;
                currentSig = targetSig;
                current.executedBlocks.add(blockId);

            } else if (currentSig.equals(targetSig)) {
                current.executedBlocks.add(blockId);

            } else {
                CallNode ancestor = findAncestor(current, targetSig);
                if (ancestor != null) {
                    current = ancestor;
                    currentSig = targetSig;
                    current.executedBlocks.add(blockId);
                    continue;
                }

                CallNode caller = findPlausibleCaller(current, targetSig, sigToSource);
                if (caller != null) {
                    current = caller;
                }

                CallNode node = new CallNode(targetSig);
                node.parent = current;
                current.children.add(node);
                current = node;
                currentSig = targetSig;
                current.executedBlocks.add(blockId);
            }
        }
        return root;
    }

    private static CallNode findAncestor(CallNode current, String targetSig) {
        CallNode node = current.parent;
        while (node != null && !node.signature.equals("ROOT")) {
            if (node.signature.equals(targetSig))
                return node;
            node = node.parent;
        }
        return null;
    }

    private static CallNode findPlausibleCaller(CallNode current, String targetSig,
            Map<String, String> sigToSource) {
        String targetName = extractCallableName(targetSig);
        if (targetName == null)
            return null;

        CallNode node = current;
        while (node != null && !node.signature.equals("ROOT")) {
            String src = sigToSource.get(node.signature);
            if (src != null && src.contains(targetName + "(")) {
                return node;
            }
            node = node.parent;
        }
        return null;
    }

    private static String extractCallableName(String signature) {
        if (signature.startsWith("<"))
            return null;

        String clean = signature.replaceAll("@\\w+\\s*", "").trim();
        int paren = clean.indexOf('(');
        if (paren <= 0)
            return null;

        String before = clean.substring(0, paren).trim();
        String[] tokens = before.split("\\s+");
        return tokens[tokens.length - 1];
    }

    private static void attachMetadata(CallNode node,
            Map<String, String> sigToSource,
            Map<String, String> sigToFile) {
        if (!node.signature.equals("ROOT")) {
            node.source = sigToSource.get(node.signature);
            node.filePath = sigToFile.get(node.signature);
        }
        for (CallNode child : node.children) {
            attachMetadata(child, sigToSource, sigToFile);
        }
    }

    static class CallNode {
        String signature;
        String source;
        String filePath;
        List<Integer> executedBlocks = new ArrayList<>();
        List<CallNode> children = new ArrayList<>();
        CallNode parent;

        CallNode(String signature) {
            this.signature = signature;
        }
    }

    static class ThreadAnalysis {
        String name;
        int order;
        List<Integer> blockTrace;
        CallNode callTree;

        ThreadAnalysis(String name, int order, List<Integer> blockTrace, CallNode tree) {
            this.name = name;
            this.order = order;
            this.blockTrace = blockTrace;
            this.callTree = tree;
        }
    }

    private static String generateJson(List<ThreadAnalysis> analyses) {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Object> threadList = new ArrayList<>();

        for (ThreadAnalysis ta : analyses) {
            Map<String, Object> threadMap = new LinkedHashMap<>();
            threadMap.put("name", ta.name);
            threadMap.put("order", ta.order);
            
            if (ta.blockTrace != null && !ta.blockTrace.isEmpty()) {
                threadMap.put("block_trace", ta.blockTrace);
            }

            CallNode rootNode = ta.callTree;
            if (rootNode.children.isEmpty()) {
                threadMap.put("call_tree", null);
            } else if (rootNode.children.size() == 1) {
                threadMap.put("call_tree", toMap(rootNode.children.get(0)));
            } else {
                List<Object> topCalls = new ArrayList<>();
                for (CallNode child : rootNode.children) {
                    topCalls.add(toMap(child));
                }
                threadMap.put("call_tree", topCalls);
            }

            threadList.add(threadMap);
        }

        root.put("threads", threadList);
        return GSON.toJson(root);
    }

    private static Map<String, Object> toMap(CallNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("method", node.signature);

        if (node.filePath != null) {
            map.put("file", node.filePath);
        }
        
        if (!node.executedBlocks.isEmpty()) {
            map.put("executed_blocks", node.executedBlocks);
        }

        if (node.source != null) {
            map.put("source", node.source);
        }

        if (!node.children.isEmpty()) {
            List<Object> calls = new ArrayList<>();
            for (CallNode child : node.children) {
                calls.add(toMap(child));
            }
            map.put("calls", calls);
        }

        return map;
    }

    private static String getAsString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull())
            return null;
        return obj.get(key).getAsString();
    }

    private static int getAsInt(JsonObject obj, String key, int defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull())
            return defaultValue;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return defaultValue;
        }
    }
}