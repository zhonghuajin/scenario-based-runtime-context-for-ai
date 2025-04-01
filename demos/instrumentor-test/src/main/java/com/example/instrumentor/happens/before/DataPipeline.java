package com.example.instrumentor.happens.before;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class DataPipeline {

    @FunctionalInterface
    public interface Stage {
        void execute(PipelineContext context) throws Exception;
    }

    private final List<Stage> stages = new ArrayList<>();
    private final ExecutorService executor;
    private final EventBus eventBus;

    public DataPipeline(ExecutorService executor, EventBus eventBus) {
        this.executor = executor;
        this.eventBus = eventBus;
    }

    public DataPipeline addStage(Stage stage) {
        stages.add(stage);
        return this;
    }

    public CompletableFuture<PipelineContext> execute(PipelineContext context) {
        CompletableFuture<PipelineContext> chain = CompletableFuture.completedFuture(context);

        for (Stage stage : stages) {
            chain = chain.thenApplyAsync(ctx -> {
                try {
                    stage.execute(ctx);
                    eventBus.publish(new Event("pipeline.stage.complete",
                            new String[]{ctx.getPipelineId(), ctx.getCurrentStage()}));
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
                return ctx;
            }, executor);
        }

        return chain.thenApplyAsync(ctx -> {
            eventBus.publish(new Event("pipeline.finished", ctx.getPipelineId()));
            return ctx;
        }, executor);
    }
}