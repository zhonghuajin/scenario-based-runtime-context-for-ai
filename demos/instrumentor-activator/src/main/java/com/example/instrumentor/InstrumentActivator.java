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
    private static final Pattern INST_COMMENT_PATTERN = Pattern.compile("^(\\s*)//\\s*INST#(\\d+)\\s*$");
    private static final Pattern INST_CALL_PATTERN = Pattern.compile("^(\\s*)com\\.example\\.instrumentor\\.InstrumentLog\\.staining\\((\\d+)\\);\\s*$");
    private static final String CALL_TEMPLATE = "com.example.instrumentor.InstrumentLog.staining(%d);";
    private static final String COMMENT_TEMPLATE = "// INST#%d";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: InstrumentActivator <target1> [target2 ...]");
            System.exit(1);
        }

        InstrumentActivator activator = new InstrumentActivator();
        for (String arg : args) {
            Path target = Paths.get(arg).toAbsolutePath().normalize();
            if (!Files.exists(target)) {
                System.err.println("Target does not exist: " + target);
                System.exit(1);
            }
            activator.activate(target);
        }
    }

    public int activate(Path target) throws IOException {
        List<Path> files = collectJavaFiles(target);
        int total = 0;
        for (Path file : files) {
            total += processFile(file, Direction.ACTIVATE);
        }
        return total;
    }

    public int deactivate(Path target) throws IOException {
        List<Path> files = collectJavaFiles(target);
        int total = 0;
        for (Path file : files) {
            total += processFile(file, Direction.DEACTIVATE);
        }
        return total;
    }

    private enum Direction { ACTIVATE, DEACTIVATE }

    private int processFile(Path file, Direction direction) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        boolean modified = false;
        int count = 0;
        Pattern sourcePattern = (direction == Direction.ACTIVATE) ? INST_COMMENT_PATTERN : INST_CALL_PATTERN;

        for (int i = 0; i < lines.size(); i++) {
            Matcher m = sourcePattern.matcher(lines.get(i));
            if (m.matches()) {
                String indent = m.group(1);
                int id = Integer.parseInt(m.group(2));
                String replacement = indent + ((direction == Direction.ACTIVATE)
                        ? String.format(CALL_TEMPLATE, id)
                        : String.format(COMMENT_TEMPLATE, id));
                lines.set(i, replacement);
                modified = true;
                count++;
            }
        }

        if (modified) {
            Files.write(file, lines, StandardCharsets.UTF_8);
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