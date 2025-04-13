package com.example.instrumentor.data.structuring;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.comments.Comment;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DataStructuring {

    static class BlockInfo {
        final int id;
        final String filePath;
        final int originalLine;

        BlockInfo(int id, String filePath, int originalLine) {
            this.id = id;
            this.filePath = filePath;
            this.originalLine = originalLine;
        }
    }

    static class MethodInfo {
        final String className;
        final String fullSignature;
        final int startLine;
        final int endLine;

        MethodInfo(String className, String fullSignature, int startLine, int endLine) {
            this.className = className;
            this.fullSignature = fullSignature;
            this.startLine = startLine;
            this.endLine = endLine;
        }

        String uniqueKey() {
            return fullSignature + "@@" + startLine;
        }
    }

    static class ThreadTrace {
        final String name;
        final int order;
        final int blockCount;
        final List<Integer> blockIds;
        final List<String> mergedFrom;

        ThreadTrace(String name, int order, int blockCount,
                    List<Integer> blockIds, List<String> mergedFrom) {
            this.name = name;
            this.order = order;
            this.blockCount = blockCount;
            this.blockIds = blockIds;
            this.mergedFrom = mergedFrom;
        }
    }

    static Map<Integer, BlockInfo> parseMappingFile(String path) throws IOException {
        Map<Integer, BlockInfo> map = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            try {
                int id = Integer.parseInt(line.substring(0, eq).trim());
                String value = line.substring(eq + 1).trim();
                int lastColon = value.lastIndexOf(':');
                String filePath = value.substring(0, lastColon).trim();
                int lineNum = Integer.parseInt(value.substring(lastColon + 1).trim());
                map.put(id, new BlockInfo(id, filePath.replace('\\', '/'), lineNum));
            } catch (Exception ignored) {}
        }
        return map;
    }

    static List<ThreadTrace> parseLogFile(String path) throws IOException {
        List<ThreadTrace> traces = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);

        Pattern headerPat = Pattern.compile(
            "\\[(.+?)\\].*?#(\\d+).*?count:\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE
        );
        Pattern mergedPat = Pattern.compile(
            "[Mm]erged from\\s+\\d+\\s+threads:\\s*(.+?)$"
        );

        int i = 0;
        while (i < lines.size()) {
            String raw = lines.get(i).trim();
            Matcher hm = headerPat.matcher(raw);
            if (hm.find()) {
                String threadName = hm.group(1);
                int order = Integer.parseInt(hm.group(2));
                int count = Integer.parseInt(hm.group(3));

                List<String> mergedFrom = null;
                Matcher mm = mergedPat.matcher(raw);
                if (mm.find()) {
                    mergedFrom = Arrays.stream(mm.group(1).split(","))
                            .map(String::trim).filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                }

                StringBuilder buf = new StringBuilder();
                i++;
                while (i < lines.size()) {
                    String l = lines.get(i).trim();
                    if (l.isEmpty() || l.startsWith("#") || l.startsWith("[")) break;
                    buf.append(" ").append(l);
                    i++;
                }

                List<Integer> ids = new ArrayList<>();
                for (String part : buf.toString().trim().split("\\s*->\\s*")) {
                    part = part.trim();
                    if (!part.isEmpty()) {
                        try { ids.add(Integer.parseInt(part)); } catch (Exception ignored) {}
                    }
                }
                traces.add(new ThreadTrace(threadName, order, count, ids, mergedFrom));
            } else {
                i++;
            }
        }
        return traces;
    }

    static Map<Integer, MethodInfo> mapBlocksToMethods(Path prunedFile) {
        Map<Integer, MethodInfo> blockToMethod = new HashMap<>();
        if (!Files.exists(prunedFile)) return blockToMethod;

        try {
            ParserConfiguration cfg = new ParserConfiguration();
            cfg.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
            StaticJavaParser.setConfiguration(cfg);
            CompilationUnit cu = StaticJavaParser.parse(prunedFile);

            Pattern blockIdPattern = Pattern.compile("Executed Block ID:\\s*(\\d+)");

            for (Comment comment : cu.getAllComments()) {
                Matcher m = blockIdPattern.matcher(comment.getContent());
                if (m.find()) {
                    int blockId = Integer.parseInt(m.group(1));
                    Node startNode = comment.getCommentedNode().orElse(comment);
                    MethodInfo mi = findEnclosingMethod(startNode);
                    if (mi != null) {
                        blockToMethod.put(blockId, mi);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[WARN] Parsing failed: " + prunedFile + " — " + e.getMessage());
        }
        return blockToMethod;
    }

    private static MethodInfo findEnclosingMethod(Node node) {
        Node current = node;
        while (current != null) {
            if (current instanceof MethodDeclaration md) return createMethodInfo(md);
            if (current instanceof ConstructorDeclaration cd) return createMethodInfo(cd);
            if (current instanceof InitializerDeclaration id) return createMethodInfo(id);
            if (current instanceof FieldDeclaration fd) return createMethodInfo(fd);
            if (current instanceof LambdaExpr le) return createMethodInfo(le);
            current = current.getParentNode().orElse(null);
        }
        return null;
    }

    private static MethodInfo createMethodInfo(Node node) {
        String signature;
        if (node instanceof MethodDeclaration md) {
            signature = buildSignature(md);
        } else if (node instanceof ConstructorDeclaration cd) {
            signature = buildCtorSignature(cd);
        } else if (node instanceof InitializerDeclaration id) {
            signature = id.isStatic() ? "static initializer" : "instance initializer";
        } else if (node instanceof FieldDeclaration fd) {
            String vars = fd.getVariables().stream().map(v -> v.getNameAsString()).collect(Collectors.joining(", "));
            signature = "Field initializer: " + vars;
        } else if (node instanceof LambdaExpr le) {
            String params = le.getParameters().stream().map(p -> p.toString()).collect(Collectors.joining(", "));
            signature = "Lambda: (" + params + ") -> {...}";
        } else {
            signature = "<unknown>";
        }

        int start = node.getBegin().isPresent() ? node.getBegin().get().line : 0;
        int end = node.getEnd().isPresent() ? node.getEnd().get().line : 0;
        return new MethodInfo(enclosingTypeName(node), signature, start, end);
    }

    private static String buildSignature(MethodDeclaration md) {
        StringBuilder sb = new StringBuilder();
        String mods = md.getModifiers().stream()
                .map(m -> m.getKeyword().asString()).collect(Collectors.joining(" "));
        if (!mods.isEmpty()) sb.append(mods).append(" ");
        if (md.getTypeParameters() != null && !md.getTypeParameters().isEmpty()) {
            sb.append("<").append(md.getTypeParameters().stream()
                    .map(Object::toString).collect(Collectors.joining(", "))).append("> ");
        }
        sb.append(md.getTypeAsString()).append(" ");
        sb.append(md.getNameAsString()).append("(");
        sb.append(md.getParameters().stream()
                .map(p -> p.toString()).collect(Collectors.joining(", ")));
        sb.append(")");
        return sb.toString();
    }

    private static String buildCtorSignature(ConstructorDeclaration cd) {
        StringBuilder sb = new StringBuilder();
        String mods = cd.getModifiers().stream()
                .map(m -> m.getKeyword().asString()).collect(Collectors.joining(" "));
        if (!mods.isEmpty()) sb.append(mods).append(" ");
        sb.append(cd.getNameAsString()).append("(");
        sb.append(cd.getParameters().stream()
                .map(p -> p.toString()).collect(Collectors.joining(", ")));
        sb.append(")");
        return sb.toString();
    }

    private static String enclosingTypeName(Node node) {
        Deque<String> parts = new ArrayDeque<>();
        Node cur = node.getParentNode().orElse(null);
        while (cur != null) {
            if (cur instanceof ClassOrInterfaceDeclaration c) parts.addFirst(c.getNameAsString());
            else if (cur instanceof EnumDeclaration e) parts.addFirst(e.getNameAsString());
            else if (cur instanceof RecordDeclaration r) parts.addFirst(r.getNameAsString());
            cur = cur.getParentNode().orElse(null);
        }
        return parts.isEmpty() ? "<anonymous>" : String.join("$", parts);
    }

    static String extractSource(Path filePath, int startLine, int endLine) {
        if (startLine <= 0 || endLine <= 0) return "";
        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            if (lines.isEmpty() || startLine > lines.size()) return "";
            endLine = Math.min(endLine, lines.size());
            List<String> slice = lines.subList(startLine - 1, endLine);
            return dedent(slice);
        } catch (IOException e) {
            return "";
        }
    }

    static String dedent(List<String> lines) {
        int minIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.isBlank()) continue;
            int spaces = 0;
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == ' ') spaces++;
                else if (line.charAt(i) == '\t') spaces += 4;
                else break;
            }
            minIndent = Math.min(minIndent, spaces);
        }
        if (minIndent <= 0 || minIndent == Integer.MAX_VALUE) return String.join("\n", lines);
        
        final int strip = minIndent;
        return lines.stream().map(line -> {
            if (line.isBlank()) return "";
            int removed = 0, pos = 0;
            while (pos < line.length() && removed < strip) {
                char c = line.charAt(pos);
                if (c == ' ') { removed++; pos++; }
                else if (c == '\t') { removed += 4; pos++; }
                else break;
            }
            return line.substring(pos);
        }).collect(Collectors.joining("\n"));
    }

    static String extractRelativePath(String absolutePath) {
        String norm = absolutePath.replace('\\', '/');
        String[] markers = {"/src/main/java/", "/src/test/java/", "/src/"};
        for (String marker : markers) {
            int idx = norm.indexOf(marker);
            if (idx >= 0) {
                return norm.substring(idx + marker.length());
            }
        }
        int lastSlash = norm.lastIndexOf('/');
        return lastSlash >= 0 ? norm.substring(lastSlash + 1) : norm;
    }

    static Path resolvePrunedFilePath(Path prunedDir, String threadName, String originalPath) {
        String fileName = Paths.get(originalPath).getFileName().toString();
        Path baseThreadDir = prunedDir.resolve(sanitizeDirName(threadName));
        
        if (!Files.exists(baseThreadDir)) return null;

        try (var stream = Files.walk(baseThreadDir)) {
            return stream.filter(Files::isRegularFile)
                         .filter(p -> p.getFileName().toString().equals(fileName))
                         .findFirst()
                         .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static String sanitizeDirName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
    }

    private static String compactIntegerArrays(String json) {
        Pattern p = Pattern.compile("\\[([\\s\\d,]+)\\]");
        Matcher m = p.matcher(json);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String arrayContent = m.group(1);
            String compacted = arrayContent.replaceAll("\\s+", "").replace(",", ", ");
            m.appendReplacement(sb, "[" + compacted + "]");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: java -jar instrumentor-analyzer.jar <pruned_dir> <comment_mapping> <log_file> <event_log_file> [event_dictionary] [output_base_name]");
            return;
        }

        Path prunedDir = Paths.get(args[0]);
        String mappingPath = args[1];
        String logPath = args[2];
        String eventLogPath = args[3];
        
        String dictPath = null;
        String baseOutputName = "final-output";
        
        if (args.length == 5) {
            if (args[4].endsWith(".txt")) dictPath = args[4];
            else baseOutputName = args[4];
        } else if (args.length >= 6) {
            dictPath = args[4];
            baseOutputName = args[5];
        }

        if (baseOutputName.endsWith(".json")) {
            baseOutputName = baseOutputName.substring(0, baseOutputName.length() - 5);
        }

        System.out.println("[1/3] Starting to parse logs and source code structure...");
        Map<Integer, BlockInfo> blockMap = parseMappingFile(mappingPath);
        List<ThreadTrace> traces = parseLogFile(logPath);

        Map<String, Integer> fileBlockTotals = new HashMap<>();
        for (BlockInfo bi : blockMap.values()) {
            String rel = extractRelativePath(bi.filePath);
            fileBlockTotals.merge(rel, 1, Integer::sum);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> threadList = new ArrayList<>();

        for (ThreadTrace trace : traces) {
            Map<String, Object> tObj = new LinkedHashMap<>();
            tObj.put("name", trace.name);
            tObj.put("order", trace.order);
            if (trace.mergedFrom != null) {
                tObj.put("merged_from", trace.mergedFrom);
            }
            
            // Restored block_trace for CallTreeAnalyzer to process executed blocks
            tObj.put("block_trace", trace.blockIds);

            List<String> fileOrder = new ArrayList<>();
            Map<String, List<String>> methodOrderInFile = new HashMap<>();
            Map<String, Map<String, List<Integer>>> fileMethodBlocks = new HashMap<>();
            Map<String, Map<String, MethodInfo>> fileMethodInfos = new HashMap<>();
            
            Map<String, Map<Integer, MethodInfo>> fileToBlockMethodMap = new HashMap<>();
            Map<String, Path> resolvedFiles = new HashMap<>();

            for (int bid : trace.blockIds) {
                BlockInfo bi = blockMap.get(bid);
                if (bi == null) continue;

                String relPath = extractRelativePath(bi.filePath);
                
                if (!fileOrder.contains(relPath)) {
                    fileOrder.add(relPath);
                    methodOrderInFile.put(relPath, new ArrayList<>());
                    fileMethodBlocks.put(relPath, new HashMap<>());
                    fileMethodInfos.put(relPath, new HashMap<>());
                    
                    Path pFile = resolvePrunedFilePath(prunedDir, trace.name, bi.filePath);
                    if (pFile != null) {
                        resolvedFiles.put(relPath, pFile);
                        fileToBlockMethodMap.put(relPath, mapBlocksToMethods(pFile));
                    } else {
                        fileToBlockMethodMap.put(relPath, Collections.emptyMap());
                    }
                }

                MethodInfo mi = fileToBlockMethodMap.get(relPath).get(bid);
                String methodKey = (mi != null) ? mi.uniqueKey() : "<unknown>@@block:" + bid;

                if (!methodOrderInFile.get(relPath).contains(methodKey)) {
                    methodOrderInFile.get(relPath).add(methodKey);
                }

                fileMethodBlocks.get(relPath).computeIfAbsent(methodKey, k -> new ArrayList<>()).add(bid);
                if (mi != null) {
                    fileMethodInfos.get(relPath).putIfAbsent(methodKey, mi);
                }
            }

            List<Map<String, Object>> filesArray = new ArrayList<>();

            for (String relPath : fileOrder) {
                Map<String, Object> fObj = new LinkedHashMap<>();
                fObj.put("path", relPath);
                
                Path actualFile = resolvedFiles.get(relPath);
                
                if (fileBlockTotals.containsKey(relPath)) {
                    fObj.put("blocks_total", fileBlockTotals.get(relPath));
                }

                List<Map<String, Object>> methodsArray = new ArrayList<>();
                
                for (String methodKey : methodOrderInFile.get(relPath)) {
                    MethodInfo mi = fileMethodInfos.get(relPath).get(methodKey);

                    Map<String, Object> mObj = new LinkedHashMap<>();
                    
                    if (mi != null) {
                        mObj.put("line_start", mi.startLine);
                        mObj.put("source", actualFile != null ? extractSource(actualFile, mi.startLine, mi.endLine) : "");
                    } else {
                        mObj.put("line_start", 0);
                        mObj.put("source", "");
                    }
                    methodsArray.add(mObj);
                }

                fObj.put("methods", methodsArray);
                filesArray.add(fObj);
            }

            tObj.put("files", filesArray);
            threadList.add(tObj);
        }

        root.put("threads", threadList);

        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        String intermediateJson = gson.toJson(root);
        intermediateJson = compactIntegerArrays(intermediateJson);

        System.out.println("[2/3] Starting analysis of Happens-Before synchronization relationships...");
        
        Map<String, String> eventDict = new HashMap<>();
        if (dictPath != null && Files.exists(Paths.get(dictPath))) {
            System.out.println(" - Dictionary file found, loading: " + dictPath);
            for (String line : Files.readAllLines(Paths.get(dictPath), StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String id = line.substring(0, eq).trim();
                    String action = line.substring(eq + 1).trim();
                    eventDict.put("EVT_" + id, action);
                }
            }
        }

        String eventLogContent = Files.readString(Paths.get(eventLogPath), StandardCharsets.UTF_8);
        
        if (!eventDict.isEmpty()) {
            StringBuilder translatedLog = new StringBuilder();
            for (String line : eventLogContent.split("\\r?\\n")) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    translatedLog.append(line).append("\n");
                    continue;
                }
                String[] parts = line.split(", ");
                
                if (parts.length >= 3 && eventDict.containsKey(parts[2].trim())) {
                    parts[2] = eventDict.get(parts[2].trim());
                    translatedLog.append(String.join(", ", parts)).append("\n");
                } else {
                    translatedLog.append(line).append("\n");
                }
            }
            eventLogContent = translatedLog.toString();
        }

        JsonObject happensBeforeData = HappensBeforeAnalyzer.analyzeEvents(eventLogContent);

        System.out.println("[3/3] Generating standalone data files (JSON + Markdown)...");

        String happensBeforeOutput = gson.toJson(happensBeforeData);
        String hbPath = baseOutputName + "-happensbefore.json";
        String hbMdPath = baseOutputName + "-happensbefore.md";
        Files.writeString(Paths.get(hbPath), happensBeforeOutput, StandardCharsets.UTF_8);
        MarkdownGenerator.generate(happensBeforeOutput, hbMdPath);
        System.out.println(" - Happens-Before JSON & MD generated: " + hbPath + " / " + hbMdPath);

        String callTreeOnlyOutput = CallTreeAnalyzer.analyze(intermediateJson, null);
        String ctPath = baseOutputName + "-calltree.json";
        String ctMdPath = baseOutputName + "-calltree.md";
        Files.writeString(Paths.get(ctPath), callTreeOnlyOutput, StandardCharsets.UTF_8);
        MarkdownGenerator.generate(callTreeOnlyOutput, ctMdPath);
        System.out.println(" - Call Tree JSON & MD generated: " + ctPath + " / " + ctMdPath);

        JsonObject combinedRoot = JsonParser.parseString(callTreeOnlyOutput).getAsJsonObject();
        if (happensBeforeData.has("sync_relations")) {
            combinedRoot.add("sync_relations", happensBeforeData.get("sync_relations"));
        }
        if (happensBeforeData.has("data_races")) {
            combinedRoot.add("data_races", happensBeforeData.get("data_races"));
        }
        if (happensBeforeData.has("possible_taint_flows")) {
            combinedRoot.add("possible_taint_flows", happensBeforeData.get("possible_taint_flows"));
        }

        String combinedOutput = compactIntegerArrays(gson.toJson(combinedRoot));
        String combinedPath = baseOutputName + "-combined.json";
        String combinedMdPath = baseOutputName + "-combined.md";
        Files.writeString(Paths.get(combinedPath), combinedOutput, StandardCharsets.UTF_8);
        MarkdownGenerator.generate(combinedOutput, combinedMdPath);
        System.out.println(" - Combined data JSON & MD generated: " + combinedPath + " / " + combinedMdPath);

        System.out.println("==================================================");
        System.out.println("All analysis tasks have been completed!");
    }
}