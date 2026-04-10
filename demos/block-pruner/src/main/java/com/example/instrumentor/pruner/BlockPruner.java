package com.example.instrumentor.pruner;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;


public class BlockPruner {

    

    
    private static class BlockLocation {
        final String normalizedPath; 
        final int startLine;

        BlockLocation(String normalizedPath, int startLine) {
            this.normalizedPath = normalizedPath;
            this.startLine = startLine;
        }
    }

    

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: BlockPruner <Source Directories> <comment-mapping file> <instrument-log file> <Output Directory>");
            System.err.println();
            System.err.println("Parameter Description:");
            System.err.println("  <Source Directories>       Java source root directories, separated by ';' for multiple paths");
            System.err.println("                             e.g. \"dir1;dir2;dir3\"");
            System.err.println("  <comment-mapping>          Instrumentation mapping file (format: ID = filePath:lineNo)");
            System.err.println("  <instrument-log>           Runtime instrumentation log file");
            System.err.println("  <Output Directory>         Output root directory for pruned source code");
            System.exit(1);
            return;
        }

        // Parse multiple source directories separated by ';'
        List<Path> sourceDirs = new ArrayList<>();
        for (String part : args[0].split(";")) {
            part = part.trim();
            if (!part.isEmpty()) {
                sourceDirs.add(Paths.get(part).toAbsolutePath().normalize());
            }
        }
        if (sourceDirs.isEmpty()) {
            System.err.println("[Error] No valid source directory provided.");
            System.exit(1);
            return;
        }

        Path mappingFile = Paths.get(args[1]);
        Path logFile     = Paths.get(args[2]);
        Path outputDir   = Paths.get(args[3]).toAbsolutePath().normalize();

        System.out.println("[BlockPruner] Source Directories:");
        for (int i = 0; i < sourceDirs.size(); i++) {
            System.out.printf("  [%d] %s%n", i + 1, sourceDirs.get(i));
        }
        System.out.println("[BlockPruner] Mapping File: " + mappingFile);
        System.out.println("[BlockPruner] Log File: " + logFile);
        System.out.println("[BlockPruner] Output Directory: " + outputDir);
        System.out.println();

        
        Map<Integer, BlockLocation> blockMap = parseCommentMapping(mappingFile);
        System.out.printf("[Step 1] Loaded %d block mappings%n", blockMap.size());

        
        LinkedHashMap<String, Set<Integer>> threadLogs = parseInstrumentLog(logFile);
        System.out.printf("[Step 2] Loaded execution logs for %d threads%n", threadLogs.size());

        
        Map<String, Map<Integer, Integer>> fileBlockIndex = buildFileBlockIndex(blockMap);
        System.out.printf("[Step 3] Involves %d source files%n", fileBlockIndex.size());

        
        Map<String, Path> resolvedPaths = resolveSourceFiles(fileBlockIndex.keySet(), sourceDirs);
        System.out.printf("[Step 4] Successfully located %d / %d source files%n",
                resolvedPaths.size(), fileBlockIndex.size());

        
        ParserConfiguration parserConfig = new ParserConfiguration();
        parserConfig.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        StaticJavaParser.setConfiguration(parserConfig);

        
        int totalThreads = threadLogs.size();
        int idx = 0;
        for (Map.Entry<String, Set<Integer>> entry : threadLogs.entrySet()) {
            idx++;
            String threadName = entry.getKey();
            Set<Integer> executedIds = entry.getValue();
            System.out.printf("%n[%d/%d] ", idx, totalThreads);
            pruneForThread(threadName, executedIds, blockMap, fileBlockIndex,
                    resolvedPaths, sourceDirs, outputDir);
        }

        System.out.println();
        System.out.println("[BlockPruner] All processing completed. Output Directory: " + outputDir);
    }

    

    
    private static void pruneForThread(
            String threadName,
            Set<Integer> executedIds,
            Map<Integer, BlockLocation> blockMap,
            Map<String, Map<Integer, Integer>> fileBlockIndex,
            Map<String, Path> resolvedPaths,
            List<Path> sourceDirs,
            Path outputDir) throws IOException {

        System.out.printf("===== Thread [%s]  Executed %d blocks =====%n", threadName, executedIds.size());

        
        Set<String> involvedFiles = new LinkedHashSet<>();
        for (int id : executedIds) {
            BlockLocation loc = blockMap.get(id);
            if (loc != null) {
                involvedFiles.add(loc.normalizedPath);
            }
        }

        if (involvedFiles.isEmpty()) {
            System.out.println("  (No files involved for this thread, skipping)");
            return;
        }

        String safeDirName = sanitizeDirName(threadName);

        for (String normalizedFile : involvedFiles) {
            Map<Integer, Integer> lineToBlockId = fileBlockIndex.get(normalizedFile);
            if (lineToBlockId == null) continue;

            
            Set<Integer> unexecutedLines = new LinkedHashSet<>();
            Map<Integer, Integer> executedLineToId = new LinkedHashMap<>();
            
            for (Map.Entry<Integer, Integer> e : lineToBlockId.entrySet()) {
                if (executedIds.contains(e.getValue())) {
                    executedLineToId.put(e.getKey(), e.getValue());
                } else {
                    unexecutedLines.add(e.getKey());
                }
            }

            
            Path srcFile = resolvedPaths.get(normalizedFile);
            if (srcFile == null) {
                System.err.printf("  [Skip] Source file not found: %s%n", normalizedFile);
                continue;
            }

            
            CompilationUnit cu;
            try {
                cu = StaticJavaParser.parse(srcFile);
            } catch (Exception ex) {
                System.err.printf("  [Skip] Parsing failed %s: %s%n", srcFile.getFileName(), ex.getMessage());
                continue;
            }

            
            int prunedCount = pruneUnexecutedBlocks(cu, unexecutedLines, executedLineToId, threadName);

            
            Path matchingSourceDir = findMatchingSourceDir(srcFile, sourceDirs);
            Path relativePath = matchingSourceDir.relativize(srcFile.toAbsolutePath().normalize());
            Path outFile = outputDir.resolve(safeDirName).resolve(relativePath);
            Files.createDirectories(outFile.getParent());
            Files.writeString(outFile, cu.toString(), StandardCharsets.UTF_8);

            int totalBlocks = lineToBlockId.size();
            int keptBlocks = totalBlocks - unexecutedLines.size();
            System.out.printf("  %-55s  Kept %3d | Cleared %3d | Total %3d blocks",
                    relativePath.getFileName(), keptBlocks, unexecutedLines.size(), totalBlocks);

            if (prunedCount != unexecutedLines.size()) {
                System.out.printf("  ⚠ AST matched %d/%d", prunedCount, unexecutedLines.size());
            }
            System.out.println();
        }
    }

    /**
     * Find which source directory the given source file resides under.
     * Falls back to the first source directory if no match is found.
     */
    private static Path findMatchingSourceDir(Path srcFile, List<Path> sourceDirs) {
        Path normalized = srcFile.toAbsolutePath().normalize();
        for (Path sd : sourceDirs) {
            if (normalized.startsWith(sd)) {
                return sd;
            }
        }
        // Fallback: pick the directory that shares the longest common suffix with the file path
        Path best = sourceDirs.get(0);
        int bestScore = -1;
        for (Path sd : sourceDirs) {
            int score = commonSuffixLength(normalizePath(normalized.toString()), normalizePath(sd.toString()));
            if (score > bestScore) {
                bestScore = score;
                best = sd;
            }
        }
        return best;
    }

    

    
    private static int pruneUnexecutedBlocks(CompilationUnit cu,
                                             Set<Integer> unexecutedLines,
                                             Map<Integer, Integer> executedLineToId,
                                             String threadName) {
        if (unexecutedLines.isEmpty() && executedLineToId.isEmpty()) return 0;

        
        List<BlockStmt> unexecutedBlocks = new ArrayList<>();
        cu.walk(BlockStmt.class, block ->
                block.getBegin().ifPresent(pos -> {
                    if (unexecutedLines.contains(pos.line)) {
                        unexecutedBlocks.add(block);
                    } else if (executedLineToId.containsKey(pos.line)) {
                        
                        int blockId = executedLineToId.get(pos.line);
                        block.setLineComment(" [Executed Block ID: " + blockId + ", Thread: " + threadName + "] ");
                    }
                })
        );

        
        unexecutedBlocks.sort(Comparator.comparingInt(BlockPruner::nodeDepth).reversed());

        Set<Integer> executedLines = executedLineToId.keySet();
        int prunedCount = 0;
        
        for (BlockStmt block : unexecutedBlocks) {
            if (!containsExecutedBlock(block, executedLines)) {
                
                block.getStatements().clear();
            } else {
                
                List<Statement> toRemove = new ArrayList<>();
                for (Statement stmt : block.getStatements()) {
                    if (!containsExecutedBlock(stmt, executedLines)) {
                        toRemove.add(stmt);
                    }
                }
                for (Statement stmt : toRemove) {
                    stmt.remove();
                }
            }
            prunedCount++;
        }

        return prunedCount;
    }

    
    private static boolean containsExecutedBlock(Node node, Set<Integer> executedLines) {
        for (BlockStmt block : node.findAll(BlockStmt.class)) {
            if (block.getBegin().isPresent()
                    && executedLines.contains(block.getBegin().get().line)) {
                return true;
            }
        }
        return false;
    }    

    
    private static int nodeDepth(Node node) {
        int depth = 0;
        Node current = node;
        while (current.getParentNode().isPresent()) {
            current = current.getParentNode().get();
            depth++;
        }
        return depth;
    }

    

    
    private static Map<Integer, BlockLocation> parseCommentMapping(Path file) throws IOException {
        Map<Integer, BlockLocation> map = new LinkedHashMap<>();

        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            int eqIdx = line.indexOf('=');
            if (eqIdx < 0) continue;

            String idPart = line.substring(0, eqIdx).trim();
            String pathAndLine = line.substring(eqIdx + 1).trim();

            
            int lastColon = pathAndLine.lastIndexOf(':');
            if (lastColon <= 0) continue;

            try {
                int id = Integer.parseInt(idPart);
                String filePath = pathAndLine.substring(0, lastColon).trim();
                int startLine = Integer.parseInt(pathAndLine.substring(lastColon + 1).trim());
                map.put(id, new BlockLocation(normalizePath(filePath), startLine));
            } catch (NumberFormatException e) {
                System.err.println("[Warning] Unable to parse mapping line: " + line);
            }
        }

        return map;
    }

    

    
    private static LinkedHashMap<String, Set<Integer>> parseInstrumentLog(Path file) throws IOException {
        LinkedHashMap<String, Set<Integer>> result = new LinkedHashMap<>();
        Pattern headerPattern = Pattern.compile("^\\[(.+?)].*");

        String currentThread = null;

        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            Matcher m = headerPattern.matcher(line);
            if (m.matches()) {
                currentThread = m.group(1);
                result.put(currentThread, new LinkedHashSet<>());
            } else if (currentThread != null) { 
                
                Set<Integer> ids = result.get(currentThread);
                for (String part : line.split("->")) {
                    part = part.trim();
                    if (!part.isEmpty()) {
                        try {
                            ids.add(Integer.parseInt(part));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }

        return result;
    }

    

    
    private static Map<String, Map<Integer, Integer>> buildFileBlockIndex(
            Map<Integer, BlockLocation> blockMap) {
        Map<String, Map<Integer, Integer>> index = new LinkedHashMap<>();
        for (Map.Entry<Integer, BlockLocation> e : blockMap.entrySet()) {
            BlockLocation loc = e.getValue();
            index.computeIfAbsent(loc.normalizedPath, k -> new LinkedHashMap<>())
                    .put(loc.startLine, e.getKey());
        }
        return index;
    }

    
    private static Map<String, Path> resolveSourceFiles(Set<String> normalizedPaths, List<Path> sourceDirs)
            throws IOException {
        Map<String, Path> resolved = new LinkedHashMap<>();

        
        Map<String, List<Path>> nameIndex = new HashMap<>();
        for (Path sourceDir : sourceDirs) {
            if (!Files.isDirectory(sourceDir)) {
                System.err.println("[Warning] Source directory does not exist or is not a directory: " + sourceDir);
                continue;
            }
            try (Stream<Path> walk = Files.walk(sourceDir)) {
                walk.filter(p -> p.toString().endsWith(".java") && Files.isRegularFile(p))
                        .forEach(p -> nameIndex
                                .computeIfAbsent(p.getFileName().toString(), k -> new ArrayList<>())
                                .add(p.toAbsolutePath().normalize()));
            }
        }

        for (String np : normalizedPaths) {
            Path found = tryResolveDirect(np);
            if (found == null) {
                for (Path sourceDir : sourceDirs) {
                    found = tryResolveByMarker(np, sourceDir);
                    if (found != null) break;
                }
            }
            if (found == null) found = tryResolveByName(np, nameIndex);

            if (found != null) {
                resolved.put(np, found);
            } else {
                System.err.println("[Warning] Unable to locate source file: " + np);
            }
        }

        return resolved;
    }

    
    private static Path tryResolveDirect(String normalizedPath) {
        try {
            Path p = Paths.get(normalizedPath.replace('/', File.separatorChar));
            return Files.isRegularFile(p) ? p.toAbsolutePath().normalize() : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    
    private static Path tryResolveByMarker(String normalizedPath, Path sourceDir) {
        String[] markers = {"src/main/java/", "src/test/java/", "src/"};
        for (String marker : markers) {
            int idx = normalizedPath.indexOf(marker);
            if (idx >= 0) {
                
                String relative = normalizedPath.substring(idx + marker.length());
                Path candidate = sourceDir.resolve(relative);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().normalize();
                }
                
                String withMarker = normalizedPath.substring(idx);
                Path candidate2 = sourceDir.resolve(withMarker);
                if (Files.isRegularFile(candidate2)) {
                    return candidate2.toAbsolutePath().normalize();
                }
            }
        }
        return null;
    }

    
    private static Path tryResolveByName(String normalizedPath, Map<String, List<Path>> nameIndex) {
        String fileName = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
        List<Path> candidates = nameIndex.getOrDefault(fileName, Collections.emptyList());

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        if (candidates.size() > 1) {
            Path best = null;
            int bestScore = -1;
            for (Path c : candidates) {
                int score = commonSuffixLength(normalizedPath, normalizePath(c.toString()));
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
            return best;
        }

        return null;
    }

    

    
    private static String normalizePath(String path) {
        return path.replace('\\', '/');
    }

    
    private static int commonSuffixLength(String a, String b) {
        int i = a.length() - 1, j = b.length() - 1, count = 0;
        while (i >= 0 && j >= 0
                && Character.toLowerCase(a.charAt(i)) == Character.toLowerCase(b.charAt(j))) {
            i--;
            j--;
            count++;
        }
        return count;
    }

    
    private static String sanitizeDirName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
    }
}