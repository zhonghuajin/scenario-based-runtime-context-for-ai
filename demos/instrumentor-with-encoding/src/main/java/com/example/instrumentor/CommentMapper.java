package com.example.instrumentor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;


public class CommentMapper {

    

    
    private static final Pattern ORIGINAL_COMMENT_PATTERN =
            Pattern.compile("^(\\s*)//\\s*(.+\\.java:\\d+)\\s*$");

    
    private static final Pattern MAPPED_COMMENT_PATTERN =
            Pattern.compile("^(\\s*)//\\s*INST#(\\d+)\\s*$");

    

    
    private final Map<Integer, String> idToComment = new LinkedHashMap<>();

    
    private final Map<String, Integer> commentToId = new LinkedHashMap<>();

    

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }

        String command = args[0];

        switch (command) {

            
            case "map" -> {
                Path target = Paths.get(args[1]).toAbsolutePath().normalize();
                Path mappingOutput;
                if (args.length >= 3) {
                    mappingOutput = Paths.get(args[2]).toAbsolutePath().normalize();
                } else {
                    
                    Path currentDir = Paths.get(System.getProperty("user.dir"));
                    mappingOutput = currentDir.resolve("comment-mapping.txt");
                }

                CommentMapper mapper = new CommentMapper();
                mapper.buildFullMapping(target);

                if (mapper.size() == 0) {
                    System.out.println("[Info] No instrumentation comments found. Please run CodeBlockInstrumentor first.");
                    return;
                }

                mapper.replaceCommentsInSource(target);
                mapper.writeMappingFile(mappingOutput);
                printSummary("Full mapping completed", mapper.size(), mappingOutput);
            }

            
            case "incr" -> {
                if (args.length < 3) {
                    System.err.println("[Error] Incremental mode requires at least: incr <mapping file> <modified file1> ...");
                    System.exit(1);
                }

                Path mappingFile = Paths.get(args[1]).toAbsolutePath().normalize();
                List<Path> modifiedFiles = new ArrayList<>();
                for (int i = 2; i < args.length; i++) {
                    modifiedFiles.add(Paths.get(args[i]).toAbsolutePath().normalize());
                }

                CommentMapper mapper = new CommentMapper();
                mapper.buildIncrementalMapping(mappingFile, modifiedFiles);
                mapper.replaceCommentsInSource(modifiedFiles);
                mapper.writeMappingFile(mappingFile);
                printSummary("Incremental mapping completed", mapper.size(), mappingFile);
            }

            
            case "clean" -> {
                Path target = Paths.get(args[1]).toAbsolutePath().normalize();
                int removed = cleanInstrumentation(target);
                System.out.println();
                System.out.println("====================================");
                System.out.println(" Cleanup completed. Removed " + removed + " instrumentation comment lines.");
                System.out.println("====================================");
            }

            default -> {
                System.err.println("[Error] Unknown command: " + command);
                printUsage();
                System.exit(1);
            }
        }
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  CommentMapper map   <Java file or directory> [mapping file path]");
        System.err.println("  CommentMapper incr  <mapping file>       <modified file1> [modified file2] ...");
        System.err.println("  CommentMapper clean <Java file or directory>");
        System.err.println();
        System.err.println("Command Description:");
        System.err.println("  map   : Full mode - Scan all files → Assign IDs → Replace → Output mapping table");
        System.err.println("  Default mapping file location: comment-mapping.txt in the current working directory");
        System.err.println("  incr  : Incremental mode - Process only modified files, reuse existing mappings, ensure global ID uniqueness");
        System.err.println("  clean : Remove all instrumentation comments (original format and mapped format)");
    }

    private static void printSummary(String title, int count, Path mappingFile) {
        System.out.println();
        System.out.println("====================================");
        System.out.println(" " + title);
        System.out.println(" Total mapping entries: " + count);
        System.out.println(" Mapping file:   " + mappingFile);
        System.out.println("====================================");
    }

    

    
    public void buildFullMapping(Path target) throws IOException {
        idToComment.clear();
        commentToId.clear();

        List<Path> files = collectJavaFiles(target);
        List<String> allComments = scanOriginalComments(files);

        
        sortCommentsByPathAndLine(allComments);

        
        int id = 1;
        for (String comment : allComments) {
            idToComment.put(id, comment);
            commentToId.put(comment, id);
            id++;
        }

        System.out.println("[Full Scan] Found " + idToComment.size() + " instrumentation comments in total.");
    }

    

    
    public void buildIncrementalMapping(Path mappingFile, List<Path> modifiedFiles)
            throws IOException {

        idToComment.clear();
        commentToId.clear();

        
        Map<Integer, String> existingIdToComment = new LinkedHashMap<>();
        if (Files.exists(mappingFile)) {
            existingIdToComment = loadRawMapping(mappingFile);
            System.out.println("[Incremental] Loaded existing mapping: " + existingIdToComment.size() + " entries.");
        } else {
            System.out.println("[Incremental] Mapping file does not exist. Will create a new file: " + mappingFile);
        }

        
        Set<String> modifiedPathPrefixes = new HashSet<>();
        for (Path p : modifiedFiles) {
            
            
            modifiedPathPrefixes.add(p.toAbsolutePath().normalize().toString() + ":");
        }

        
        int removedCount = 0;
        for (Map.Entry<Integer, String> entry : existingIdToComment.entrySet()) {
            String comment = entry.getValue();
            boolean belongsToModified = modifiedPathPrefixes.stream()
                    .anyMatch(comment::startsWith);

            if (!belongsToModified) {
                
                idToComment.put(entry.getKey(), comment);
                commentToId.put(comment, entry.getKey());
            } else {
                removedCount++;
            }
        }
        System.out.println("[Incremental] Removed old entries: " + removedCount + " entries.");
        System.out.println("[Incremental] Retained existing entries: " + idToComment.size() + " entries.");

        
        int maxExistingId = idToComment.keySet().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        
        List<String> newComments = scanOriginalComments(modifiedFiles);
        sortCommentsByPathAndLine(newComments);

        
        int nextId = maxExistingId + 1;
        int newCount = 0;
        for (String comment : newComments) {
            if (!commentToId.containsKey(comment)) {
                idToComment.put(nextId, comment);
                commentToId.put(comment, nextId);
                nextId++;
                newCount++;
            }
        }
        System.out.println("[Incremental] Newly allocated entries: " + newCount + " entries (ID: "
                + (maxExistingId + 1) + " ~ " + (nextId - 1) + ")");
        System.out.println("[Incremental] Total mapping entries: " + idToComment.size() + " entries.");
    }

    

    
    public void replaceCommentsInSource(Path target) throws IOException {
        replaceCommentsInSource(collectJavaFiles(target));
    }

    
    public void replaceCommentsInSource(List<Path> files) throws IOException {
        if (commentToId.isEmpty()) {
            System.out.println("[Skip] Mapping is empty. Nothing to replace.");
            return;
        }

        for (Path file : files) {
            if (!Files.exists(file)) continue;

            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            boolean modified = false;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);

                
                Matcher m = ORIGINAL_COMMENT_PATTERN.matcher(line);
                if (m.matches()) {
                    String indent = m.group(1);
                    String content = m.group(2);
                    Integer id = commentToId.get(content);
                    if (id != null) {
                        lines.set(i, indent + "// INST#" + id);
                        modified = true;
                    }
                }
            }

            if (modified) {
                Files.write(file, lines, StandardCharsets.UTF_8);
                System.out.println("[Replace] " + file);
            }
        }
    }

    

    
    public void writeMappingFile(Path outputFile) throws IOException {
        
        List<Map.Entry<Integer, String>> sorted = new ArrayList<>(idToComment.entrySet());
        sorted.sort(Comparator.comparingInt(Map.Entry::getKey));

        List<String> output = new ArrayList<>();
        output.add("# ================================================");
        output.add("# Instrumentation Comment -> Integer ID Mapping Table");
        output.add("# Generation Time: " + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        output.add("# Total Entries: " + sorted.size());
        output.add("# ================================================");
        output.add("# Format: Integer ID = File Absolute Path:Code Block Start Line Number");
        output.add("# Note: This mapping needs to be regenerated (full) or incrementally updated after source code modifications and re-instrumentation.");
        output.add("");

        for (Map.Entry<Integer, String> entry : sorted) {
            output.add(entry.getKey() + " = " + entry.getValue());
        }

        Files.createDirectories(outputFile.getParent());
        Files.write(outputFile, output, StandardCharsets.UTF_8);
    }

    
    private Map<Integer, String> loadRawMapping(Path mappingFile) throws IOException {
        Map<Integer, String> result = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(mappingFile, StandardCharsets.UTF_8);
        Pattern entryPattern = Pattern.compile("^(\\d+)\\s*=\\s*(.+)$");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            Matcher m = entryPattern.matcher(trimmed);
            if (m.matches()) {
                int id = Integer.parseInt(m.group(1));
                String comment = m.group(2).trim();
                result.put(id, comment);
            }
        }
        return result;
    }

    
    public void loadMappingFile(Path mappingFile) throws IOException {
        idToComment.clear();
        commentToId.clear();
        Map<Integer, String> raw = loadRawMapping(mappingFile);
        for (Map.Entry<Integer, String> entry : raw.entrySet()) {
            idToComment.put(entry.getKey(), entry.getValue());
            commentToId.put(entry.getValue(), entry.getKey());
        }
        System.out.println("[Load] Read " + idToComment.size() + " mapping entries.");
    }

    

    
    public static int cleanInstrumentation(Path target) throws IOException {
        List<Path> files = collectJavaFiles(target);
        int totalRemoved = 0;

        for (Path file : files) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String> cleaned = new ArrayList<>(lines.size());
            int removedInFile = 0;

            for (String line : lines) {
                boolean isOriginal = ORIGINAL_COMMENT_PATTERN.matcher(line).matches();
                boolean isMapped = MAPPED_COMMENT_PATTERN.matcher(line).matches();

                if (isOriginal || isMapped) {
                    removedInFile++;
                } else {
                    cleaned.add(line);
                }
            }

            if (removedInFile > 0) {
                Files.write(file, cleaned, StandardCharsets.UTF_8);
                totalRemoved += removedInFile;
                System.out.println("[Clean] " + file + " (Removed " + removedInFile + " lines)");
            }
        }

        return totalRemoved;
    }

    

    public int size() {
        return idToComment.size();
    }

    public String getCommentById(int id) {
        return idToComment.get(id);
    }

    public Integer getIdByComment(String comment) {
        return commentToId.get(comment);
    }

    public Map<Integer, String> getIdToCommentMap() {
        return Collections.unmodifiableMap(idToComment);
    }

    public Map<String, Integer> getCommentToIdMap() {
        return Collections.unmodifiableMap(commentToId);
    }

    

    
    private List<String> scanOriginalComments(List<Path> files) throws IOException {
        Set<String> seen = new LinkedHashSet<>();
        for (Path file : files) {
            if (!Files.exists(file)) continue;
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                Matcher m = ORIGINAL_COMMENT_PATTERN.matcher(line);
                if (m.matches()) {
                    seen.add(m.group(2));
                }
            }
        }
        return new ArrayList<>(seen);
    }

    
    private void sortCommentsByPathAndLine(List<String> comments) {
        comments.sort((a, b) -> {
            int colonA = a.lastIndexOf(':');
            int colonB = b.lastIndexOf(':');
            String pathA = a.substring(0, colonA);
            String pathB = b.substring(0, colonB);
            int cmp = pathA.compareTo(pathB);
            if (cmp != 0) return cmp;
            int lineA = Integer.parseInt(a.substring(colonA + 1));
            int lineB = Integer.parseInt(b.substring(colonB + 1));
            return Integer.compare(lineA, lineB);
        });
    }

    
    private static List<Path> collectJavaFiles(Path target) throws IOException {
        List<Path> result = new ArrayList<>();
        if (Files.isDirectory(target)) {
            try (Stream<Path> walk = Files.walk(target)) {
                walk.filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .forEach(result::add);
            }
        } else if (target.toString().endsWith(".java")) {
            result.add(target);
        }
        return result;
    }
}
