package com.example.instrumentor.pruner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LogDeduplicator {
    private static class ThreadRecord {
        final String name;
        final List<Integer> sequence;
        final SortedSet<Integer> blockSet;

        ThreadRecord(String name, List<Integer> sequence) {
            this.name = name;
            this.sequence = Collections.unmodifiableList(sequence);
            this.blockSet = Collections.unmodifiableSortedSet(new TreeSet<>(sequence));
        }

        String canonicalKey() {
            return blockSet.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.exit(1);
        }
        Path logFile = Paths.get(args[0]);
        Path outputFile = Paths.get(args[1]);

        List<ThreadRecord> records = parseInstrumentLog(logFile);
        if (records.isEmpty()) {
            return;
        }

        LinkedHashMap<String, List<ThreadRecord>> groups = new LinkedHashMap<>();
        for (ThreadRecord rec : records) {
            groups.computeIfAbsent(rec.canonicalKey(), k -> new ArrayList<>()).add(rec);
        }

        writeDeduplicatedLog(outputFile, groups, records.size());
    }

    private static List<ThreadRecord> parseInstrumentLog(Path file) throws IOException {
        List<ThreadRecord> records = new ArrayList<>();
        Pattern headerPattern = Pattern.compile("^\\[(.+?)].*");
        String currentThread = null;
        List<Integer> currentSequence = null;

        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("# ")) continue;

            Matcher m = headerPattern.matcher(line);
            if (m.matches()) {
                if (currentThread != null && currentSequence != null && !currentSequence.isEmpty()) {
                    records.add(new ThreadRecord(currentThread, currentSequence));
                }
                currentThread = m.group(1);
                currentSequence = new ArrayList<>();
            } else if (currentThread != null && currentSequence != null) {
                for (String part : line.split("-> ")) {
                    part = part.trim();
                    if (!part.isEmpty()) {
                        try {
                            currentSequence.add(Integer.parseInt(part));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }

        if (currentThread != null && currentSequence != null && !currentSequence.isEmpty()) {
            records.add(new ThreadRecord(currentThread, currentSequence));
        }
        return records;
    }

    private static void writeDeduplicatedLog(
            Path outputFile,
            LinkedHashMap<String, List<ThreadRecord>> groups,
            int originalThreadCount) throws IOException {

        StringBuilder sb = new StringBuilder();
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss "));
        int totalEntries = groups.values().stream()
                .mapToInt(g -> g.get(0).sequence.size())
                .sum();

        sb.append("# InstrumentLog (Deduplicated) @  ").append(timestamp).append('\n');
        sb.append("# Original thread count:  ").append(originalThreadCount)
                .append(", Deduplicated group count:  ").append(groups.size()).append('\n');
        sb.append("# Total log entries:  ").append(totalEntries).append('\n');
        sb.append('\n');

        int groupIdx = 0;
        for (Map.Entry<String, List<ThreadRecord>> entry : groups.entrySet()) {
            groupIdx++;
            List<ThreadRecord> group = entry.getValue();
            ThreadRecord representative = group.get(0);

            sb.append(String.format("[%s] (Appearance order: #%d, Entry count: %d) ",
                    representative.name, groupIdx, representative.sequence.size()));

            if (group.size() > 1) {
                String allNames = group.stream()
                        .map(r -> r.name)
                        .collect(Collectors.joining(",  "));
                sb.append(String.format("  # Merged from %d threads: %s ",
                        group.size(), allNames));
            }
            sb.append('\n');

            sb.append("   ");
            List<Integer> seq = representative.sequence;
            for (int i = 0; i < seq.size(); i++) {
                if (i > 0) sb.append(" ->  ");
                sb.append(seq.get(i));
            }
            sb.append('\n');
        }

        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputFile, sb.toString(), StandardCharsets.UTF_8);
    }
}