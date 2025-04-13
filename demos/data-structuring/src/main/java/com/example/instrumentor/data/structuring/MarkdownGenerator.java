package com.example.instrumentor.data.structuring;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MarkdownGenerator {

    public static void generate(String jsonContent, String outputPath) throws IOException {
        JsonObject root = JsonParser.parseString(jsonContent).getAsJsonObject();
        StringBuilder md = new StringBuilder();

        if (root.has("threads")) {
            md.append("# Thread Traces\n\n");
            md.append("> **Data Schema & Legend:**\n");
            md.append("> This section represents the execution call tree for each thread.\n");
            md.append("> - **Call Tree**: Hierarchical execution flow. Each node contains the source file and pruned source code.\n\n");

            for (JsonElement tElem : root.getAsJsonArray("threads")) {
                JsonObject thread = tElem.getAsJsonObject();
                md.append("## ").append(thread.get("name").getAsString())
                  .append(" (Order: ").append(thread.get("order").getAsInt()).append(")\n");

                // block_trace / Trace output has been removed as requested.

                if (thread.has("call_tree") && !thread.get("call_tree").isJsonNull()) {
                    JsonElement callTreeElem = thread.get("call_tree");
                    if (callTreeElem.isJsonArray()) {
                        for (JsonElement callElem : callTreeElem.getAsJsonArray()) {
                            processCallNode(callElem.getAsJsonObject(), md, 0);
                        }
                    } else if (callTreeElem.isJsonObject()) {
                        processCallNode(callTreeElem.getAsJsonObject(), md, 0);
                    }
                }
                md.append("\n---\n\n");
            }
        }

        if (root.has("sync_relations")) {
            md.append("# Happens-Before\n");
            md.append("> **Format:** `- [Sync_Object] Releasing_Thread (Time) -> Acquiring_Thread (Time)`\n");
            md.append("> Represents synchronization edges where the left side happens-before the right side.\n\n");
            
            JsonArray syncs = root.getAsJsonArray("sync_relations");
            for (JsonElement sElem : syncs) {
                JsonObject sync = sElem.getAsJsonObject();
                JsonObject from = sync.getAsJsonObject("from");
                JsonObject to = sync.getAsJsonObject("to");
                md.append(String.format("- [%s] %s (%s) -> %s (%s)\n",
                        sync.get("sync_object").getAsString(),
                        from.get("thread").getAsString(), from.get("time").getAsString(),
                        to.get("thread").getAsString(), to.get("time").getAsString()
                ));
            }
            md.append("\n");
        }

        if (root.has("data_races")) {
            md.append("# Data Races\n");
            
            md.append("> **Format:** `- variable: `VarName` | W: Thread1 (Time) -> R/W: Thread2 (Time)`\n");
            md.append("> Represents unsynchronized concurrent access to shared variables (Write-Write or Write-Read conflicts).\n\n");
            
            JsonArray races = root.getAsJsonArray("data_races");
            for (JsonElement rElem : races) {
                JsonObject race = rElem.getAsJsonObject();
                JsonObject writer = race.getAsJsonObject("writer");
                JsonObject readerOrWriter = race.has("reader") ? race.getAsJsonObject("reader") : null;
                
                String type = race.get("description").getAsString().contains("(Read)") ? "R" : "W";
                
                if (readerOrWriter != null) {
                    md.append(String.format("- variable: `%s` | W: %s (%s) -> %s: %s (%s)\n",
                            race.get("variable").getAsString(),
                            writer.get("thread").getAsString(), writer.get("time").getAsString(),
                            type,
                            readerOrWriter.get("thread").getAsString(), readerOrWriter.get("time").getAsString()
                    ));
                }
            }
            md.append("\n");
        }

        if (root.has("possible_taint_flows")) {
            JsonObject taintObj = root.getAsJsonObject("possible_taint_flows");
            if (taintObj.has("flows")) {
                md.append("# Possible Taint Flows \n");
                md.append("> **Legend:**\n");
                md.append("> - `[Inter]`: Cross-thread data flow via shared variables.\n");
                md.append("> - `[Intra]`: Within-thread data flow (a write operation potentially tainted by previous reads in the same thread).\n\n");
                
                JsonArray flows = taintObj.getAsJsonArray("flows");
                for (JsonElement fElem : flows) {
                    JsonObject flow = fElem.getAsJsonObject();
                    String flowType = flow.get("type").getAsString();
                    if ("inter_thread".equals(flowType)) {
                        JsonObject source = flow.getAsJsonObject("source");
                        JsonObject sink = flow.getAsJsonObject("sink");
                        md.append(String.format("- [Inter] `%s` (Item: %s): %s (%s) -> %s (%s)\n",
                                flow.get("variable").getAsString(),
                                flow.has("item") ? flow.get("item").getAsString() : "-",
                                source.get("thread").getAsString(), source.get("time").getAsString(),
                                sink.get("thread").getAsString(), sink.get("time").getAsString()
                        ));
                    } else if ("intra_thread".equals(flowType)) {
                        md.append(String.format("- [Intra] %s (%s): Wrote to `%s`, tainted by %s\n",
                                flow.get("thread").getAsString(), flow.get("time").getAsString(),
                                flow.get("target_variable").getAsString(),
                                flow.getAsJsonArray("possible_source_variables").toString()
                        ));
                    }
                }
                md.append("\n");
            }
        }

        Files.writeString(Paths.get(outputPath), md.toString(), StandardCharsets.UTF_8);
    }

    private static void processCallNode(JsonObject node, StringBuilder md, int level) {
        String indent = "    ".repeat(level);
        String contentIndent = indent + "    ";

        if (node.has("file")) {
            md.append(indent).append("- *File:* `").append(node.get("file").getAsString()).append("`\n");
        } else {
            md.append(indent).append("- *(no file)*\n");
        }

        // executed_blocks is completely ignored here, as requested.

        if (node.has("source")) {
            String source = node.get("source").getAsString();
            source = source.replaceAll("//\\s*\\[Executed Block ID:.*?\\]", "").trim();
            
            md.append(contentIndent).append("```java\n");
            for (String line : source.split("\n")) {
                md.append(contentIndent).append(line).append("\n");
            }
            md.append(contentIndent).append("```\n");
        }

        if (node.has("calls")) {
            md.append(contentIndent).append("*Calls:*\n");
            for (JsonElement child : node.getAsJsonArray("calls")) {
                processCallNode(child.getAsJsonObject(), md, level + 1);
            }
        }
    }
}