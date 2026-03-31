package com.example.instrumentor.happens.before;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;

public class HbInstrumentor {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java HbInstrumentor <input.java|input_dir>");
            System.err.println("  - Modified files will be saved back to original locations");
            return;
        }

        Path inputPath = Path.of(args[0]);

        if (!Files.exists(inputPath)) {
            System.err.println("Error: Input path does not exist: " + inputPath);
            System.exit(1);
        }

        
        if (Files.isRegularFile(inputPath)) {
            
            processSingleFile(inputPath);
        } else if (Files.isDirectory(inputPath)) {
            
            processDirectory(inputPath);
        } else {
            System.err.println("Error: Input path is neither a file nor a directory: " + inputPath);
            System.exit(1);
        }

        
        Path dictPath = Path.of("event_dictionary.txt");
        List<String> dictLines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : AstKit.EVENT_DICT.entrySet()) {
            dictLines.add(entry.getValue() + "=" + entry.getKey());
        }
        Files.write(dictPath, dictLines);
        System.out.println("Dictionary saved → " + dictPath.toAbsolutePath());
        
    }

    
    private static void processSingleFile(Path inputFile) throws Exception {
        instrumentFile(inputFile, inputFile);
    }

    
    private static void processDirectory(Path inputDir) throws Exception {
        System.out.println("Processing directory: " + inputDir.toAbsolutePath());

        
        Files.walkFileTree(inputDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    try {
                        instrumentFile(file, file);
                    } catch (Exception e) {
                        System.err.println("Error processing file: " + file);
                        e.printStackTrace();
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                System.err.println("Failed to access file: " + file + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });

        System.out.println("Directory processing complete.");
    }

    
    private static void instrumentFile(Path inputFile, Path outputFile) throws Exception {
        System.out.println("Instrumenting: " + inputFile);

        CompilationUnit cu = StaticJavaParser.parse(inputFile);

        ProbeContext ctx = new ProbeContext();
        new HbVisitor(ctx).visit(cu, null);

        
        ctx.executePostActions();

        Files.writeString(outputFile, cu.toString());
    }
}