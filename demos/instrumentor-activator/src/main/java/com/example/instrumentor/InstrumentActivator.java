package com.example.instrumentor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;


public class InstrumentActivator {

    

    
    private static final Pattern INST_COMMENT_PATTERN =
            Pattern.compile("^(\\s*)//\\s*INST#(\\d+)\\s*$");

    
    private static final Pattern INST_CALL_PATTERN =
            Pattern.compile("^(\\s*)com\\.example\\.instrumentor\\.InstrumentLog\\.staining\\((\\d+)\\);\\s*$");

    
    private static final String CALL_TEMPLATE =
            "com.example.instrumentor.InstrumentLog.staining(%d);";

    
    private static final String COMMENT_TEMPLATE = "// INST#%d";

    

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }

        String command = args[0];

        InstrumentActivator activator = new InstrumentActivator();

        switch (command) {

            case "activate" -> {
                int total = runOnPaths(args, activator::activate);
                System.out.println();
                System.out.println("====================================");
                System.out.println(" Activation Complete");
                System.out.println(" Total replaced " + total + " comments → function calls");
                System.out.println("====================================");
            }

            case "deactivate" -> {
                int total = runOnPaths(args, activator::deactivate);
                System.out.println();
                System.out.println("====================================");
                System.out.println(" Restoration Complete");
                System.out.println(" Total replaced " + total + " function calls → comments");
                System.out.println("====================================");
            }

            default -> {
                System.err.println("[Error] Unknown command: " + command);
                printUsage();
                System.exit(1);
            }
        }
    }

    @FunctionalInterface
    private interface PathAction {
        int apply(Path path) throws IOException;
    }

    private static int runOnPaths(String[] args, PathAction action) throws Exception {
        int total = 0;
        for (int i = 1; i < args.length; i++) {
            Path target = Paths.get(args[i]).toAbsolutePath().normalize();
            if (!Files.exists(target)) {
                System.err.println("[Error] Path does not exist: " + target);
                System.exit(1);
            }
            total += action.apply(target);
        }
        return total;
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  InstrumentActivator activate   <dir1|file1> [dir2] [dir3] ...");
        System.err.println("  InstrumentActivator deactivate <dir1|file1> [dir2] [dir3] ...");
        System.err.println();
        System.err.println("Command Description:");
        System.err.println("  activate   : Replace // INST#<id> comments with function calls (execute before deployment)");
        System.err.println("  deactivate : Restore function calls to // INST#<id> comments (execute before re-instrumentation)");
    }

    

    
    public int activate(Path target) throws IOException {
        List<Path> files = collectJavaFiles(target);
        int total = 0;

        for (Path file : files) {
            int count = processFile(file, Direction.ACTIVATE);
            total += count;
        }

        return total;
    }

    
    public int deactivate(Path target) throws IOException {
        List<Path> files = collectJavaFiles(target);
        int total = 0;

        for (Path file : files) {
            int count = processFile(file, Direction.DEACTIVATE);
            total += count;
        }

        return total;
    }

    

    
    private enum Direction {
        
        ACTIVATE,
        
        DEACTIVATE
    }

    
    private int processFile(Path file, Direction direction) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        boolean modified = false;
        int count = 0;

        Pattern sourcePattern = (direction == Direction.ACTIVATE)
                ? INST_COMMENT_PATTERN
                : INST_CALL_PATTERN;

        for (int i = 0; i < lines.size(); i++) {
            Matcher m = sourcePattern.matcher(lines.get(i));

            if (m.matches()) {
                String indent = m.group(1);
                int id = Integer.parseInt(m.group(2));

                String replacement;
                if (direction == Direction.ACTIVATE) {
                    replacement = indent + String.format(CALL_TEMPLATE, id);
                } else {
                    replacement = indent + String.format(COMMENT_TEMPLATE, id);
                }

                lines.set(i, replacement);
                modified = true;
                count++;
            }
        }

        if (modified) {
            Files.write(file, lines, StandardCharsets.UTF_8);
            String action = (direction == Direction.ACTIVATE) ? "Activate" : "Restore";
            System.out.println("[" + action + "] " + file + " (" + count + " locations)");
        }

        return count;
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
