package com.example.instrumentor.happens.before;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

public class SyncTest {

    private int sharedData = 0;
    private volatile boolean volatileFlag = false;

    public static void main(String[] args) throws Exception {
        SyncTest test = new SyncTest();

        System.out.println("=== Test 1: Thread Start / Join ===");
        test.testThreadStartJoin();

        System.out.println("\n=== Test 2: Volatile Visibility ===");
        test.testVolatile();

        System.out.println("\n=== Test 3: Synchronized Monitor ===");
        test.testSynchronized();

        System.out.println("\n=== Test 4: ReentrantLock + Condition ===");
        test.testLockCondition();

        System.out.println("\n=== Test 5: CompletableFuture Pipeline ===");
        test.testPipeline();

        System.out.println("\n=== Test 6: ReadWriteLock + CyclicBarrier (RiskEngine) ===");
        test.testRiskEngine();

        System.out.println("\n=== Test 7: Direct Parallel Batch ===");
        test.testDirectBatch();

        System.out.println("\n=== Test 8: Event-Driven Aggregation ===");
        test.testEventDrivenAggregation();

        System.out.println("\n=== Test 9: Market Data Cache ===");
        test.testMarketDataCache();

    }

    // ==================== Test 1 ====================
    private void testThreadStartJoin() throws InterruptedException {
        sharedData = 10;
        Thread t = new Thread(() -> {
            System.out.println("  [child] sharedData = " + sharedData);
            sharedData = 20;
        });
        t.start();
        t.join();
        System.out.println("  [main]  sharedData = " + sharedData);
    }

    // ==================== Test 2 ====================
    private void testVolatile() throws InterruptedException {
        sharedData = 0;
        volatileFlag = false;
        Thread writer = new Thread(() -> {
            sharedData = 42;
            volatileFlag = true;
        }, "vol-writer");
        Thread reader = new Thread(() -> {
            while (!volatileFlag) Thread.yield();
            System.out.println("  [reader] sharedData = " + sharedData);
        }, "vol-reader");
        reader.start();
        writer.start();
        writer.join();
        reader.join();
    }

    // ==================== Test 3 ====================
    private void testSynchronized() throws InterruptedException {
        final Object monitor = new Object();
        sharedData = 0;
        Thread writer = new Thread(() -> {
            synchronized (monitor) {
                sharedData = 99;
            }
        }, "sync-writer");
        Thread reader = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            synchronized (monitor) {
                System.out.println("  [reader] sharedData = " + sharedData);
            }
        }, "sync-reader");
        writer.start();
        reader.start();
        writer.join();
        reader.join();
    }

    // ==================== Test 4 ====================
    private void testLockCondition() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        sharedData = 0;

        Thread waiter = new Thread(() -> {
            lock.lock();
            try {
                while (sharedData == 0) condition.await();
                System.out.println("  [waiter] sharedData = " + sharedData);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }, "cond-waiter");

        Thread signaler = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            lock.lock();
            try {
                sharedData = 77;
                condition.signal();
            } finally {
                lock.unlock();
            }
        }, "cond-signaler");

        waiter.start();
        signaler.start();
        waiter.join();
        signaler.join();
    }

    // ==================== Test 5 ====================
    private void testPipeline() throws Exception {
        ExecutorService pipelinePool = Executors.newFixedThreadPool(3);
        EventBus internalBus = new EventBus(false);

        PipelineContext ctx = new PipelineContext("TXN-20250331-001");

        DataPipeline pipeline = new DataPipeline(pipelinePool, internalBus)
                .addStage(buildValidationStage())
                .addStage(buildEnrichmentStage())
                .addStage(buildScoringStage());

        CompletableFuture<PipelineContext> future = pipeline.execute(ctx);
        PipelineContext result = future.get(5, TimeUnit.SECONDS);

        System.out.println("  [pipeline] validated = " + result.getAttribute("validated"));
        System.out.println("  [pipeline] price     = " + result.getAttribute("market.price"));
        System.out.println("  [pipeline] risk      = " + result.getAttribute("risk.score"));
        System.out.println("  [pipeline] stages    = " + result.getProcessedCount());

        pipelinePool.shutdown();
    }

    private DataPipeline.Stage buildValidationStage() {
        return ctx -> {
            ctx.setCurrentStage("VALIDATION");
            ctx.setAttribute("order.symbol", "AAPL");
            ctx.setAttribute("order.qty", 100);
            ctx.setAttribute("validated", true);
            ctx.incrementProcessed();
        };
    }

    private DataPipeline.Stage buildEnrichmentStage() {
        return ctx -> {
            ctx.setCurrentStage("ENRICHMENT");
            if (Boolean.TRUE.equals(ctx.getAttribute("validated"))) {
                ctx.setAttribute("market.price", 189.50);
                ctx.setAttribute("enriched.ts", System.nanoTime());
            }
            ctx.incrementProcessed();
        };
    }

    private DataPipeline.Stage buildScoringStage() {
        return ctx -> {
            ctx.setCurrentStage("SCORING");
            Double price = (Double) ctx.getAttribute("market.price");
            Integer qty = (Integer) ctx.getAttribute("order.qty");
            if (price != null && qty != null) {
                ctx.setAttribute("risk.score", price * qty * 0.02);
            }
            ctx.incrementProcessed();
        };
    }

    // ==================== Test 6 ====================
    private void testRiskEngine() throws Exception {
        RiskEngine engine = new RiskEngine();
        CyclicBarrier barrier = new CyclicBarrier(3);

        Thread calibrator = new Thread(() -> {
            Map<String, Double> prices = new HashMap<>();
            prices.put("AAPL", 189.50);
            prices.put("GOOG", 175.30);
            prices.put("TSLA", 172.00);
            engine.calibrate(prices);
            try { barrier.await(); } catch (Exception ignored) {}
        }, "re-calibrator");

        Thread riskWorker1 = new Thread(() -> {
            try { barrier.await(); } catch (Exception ignored) {}
            double r = engine.computeRisk("AAPL", 200);
            engine.recordRisk("TRADE-001", r);
        }, "re-worker-1");

        Thread riskWorker2 = new Thread(() -> {
            try { barrier.await(); } catch (Exception ignored) {}
            double r = engine.computeRisk("TSLA", 150);
            engine.recordRisk("TRADE-002", r);
        }, "re-worker-2");

        calibrator.start();
        riskWorker1.start();
        riskWorker2.start();
        calibrator.join();
        riskWorker1.join();
        riskWorker2.join();

        System.out.println("  [risk] scores = " + engine.getAllRiskScores());
    }

    // ==================== Test 7 ====================
    private void testDirectBatch() throws Exception {
        final int taskCount = 4;
        BatchAggregator aggregator = new BatchAggregator(taskCount);
        CompletionTracker tracker = new CompletionTracker(taskCount);
        AuditLog auditLog = new AuditLog();
        EventBus localBus = new EventBus(false);
        WorkerCoordinator coordinator = new WorkerCoordinator(
                taskCount, aggregator, localBus, tracker, auditLog
        );

        for (int i = 0; i < taskCount; i++) {
            final int id = i;
            coordinator.submitTask(id, () -> (id + 1) * 111);
        }

        tracker.awaitAll(5, TimeUnit.SECONDS);
        int[] results = aggregator.getAllResults();
        System.out.println("  [direct] results = " + Arrays.toString(results));
        System.out.println("  [direct] audit   = " + auditLog.size() + " entries");
        coordinator.shutdown();
    }

    // ==================== Test 8 ====================
    private void testEventDrivenAggregation() throws Exception {
        final int taskCount = 3;
        BatchAggregator aggregator = new BatchAggregator(taskCount);
        CompletionTracker tracker = new CompletionTracker(taskCount);
        AuditLog auditLog = new AuditLog();
        EventBus bus = createHighThroughputEventBus();
        WorkerCoordinator coordinator = new WorkerCoordinator(
                taskCount, aggregator, bus, tracker, auditLog
        );

        wireResultHandler(bus, aggregator, auditLog);

        for (int i = 0; i < taskCount; i++) {
            final int id = i;
            coordinator.executeWithNotification(id, () -> (id + 1) * 500);
        }

        tracker.awaitAll(5, TimeUnit.SECONDS);
        int[] results = aggregator.getAllResults();
        System.out.println("  [event] results = " + Arrays.toString(results));
        System.out.println("  [event] audit   = " + auditLog.size() + " entries");

        bus.shutdown();
        coordinator.shutdown();
    }

    private EventBus createHighThroughputEventBus() {
        // Use async dispatch for high-throughput event-driven architecture
        return new EventBus(true);
    }

    private void wireResultHandler(EventBus bus, BatchAggregator aggregator, AuditLog log) {
        bus.subscribe("worker.result.computed", event -> {
            int[] payload = (int[]) event.getPayload();
            int taskId = payload[0];
            int value = payload[1];
            aggregator.submitResult(taskId, value);
            log.append("Committed result: task=" + taskId + " val=" + value);
        });
    }

    // ==================== Test 9 ====================
    private void testMarketDataCache() throws Exception {
        MarketDataCache cache = new MarketDataCache();
        CountDownLatch gate = new CountDownLatch(1);
        AtomicReference<Double> observed = new AtomicReference<>();

        Thread writer = new Thread(() -> {
            cache.computeAndCache("AAPL", () -> 189.50);
            cache.computeAndCache("GOOG", () -> 175.30);
            gate.countDown();
        }, "cache-writer");

        Thread reader = new Thread(() -> {
            try {
                gate.await();
            } catch (InterruptedException ignored) {}
            observed.set(cache.getIfCached("AAPL"));
        }, "cache-reader");

        writer.start();
        reader.start();
        writer.join();
        reader.join();
        System.out.println("  [cache] AAPL = " + observed.get());
    }

}