package com.example.instrumentor.happens.before;

import java.util.Set;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
import com.github.javaparser.ast.stmt.TryStmt;

public class EventProbe {

    
    public record OpInfo(
            String type,
            String detail,
            Expression targetExpr,
            Expression dataExpr,
            boolean deferred,
            boolean afterCall) {
    }

    private static final Set<String> G_LOCK = Set.of("Lock", "ReentrantLock");
    private static final Set<String> G_COND = Set.of("Condition");
    private static final Set<String> G_QUEUE = Set.of(
            "BlockingQueue", "LinkedBlockingQueue", "ArrayBlockingQueue",
            "PriorityBlockingQueue", "SynchronousQueue", "DelayQueue",
            "LinkedTransferQueue", "TransferQueue", "BlockingDeque",
            "LinkedBlockingDeque");
    private static final Set<String> G_SEMA = Set.of("Semaphore");
    private static final Set<String> G_LATCH = Set.of("CountDownLatch");
    private static final Set<String> G_BARRIER = Set.of("CyclicBarrier");
    private static final Set<String> G_EXCH = Set.of("Exchanger");
    private static final Set<String> G_PHASER = Set.of("Phaser");
    private static final Set<String> G_FUTURE = Set.of("CompletableFuture", "Future");
    private static final Set<String> G_RWLOCK = Set.of("ReentrantReadWriteLock", "ReadWriteLock");
    private static final Set<String> G_STAMPED = Set.of("StampedLock");
    private static final Set<String> G_EXEC = Set.of(
            "ExecutorService", "ThreadPoolExecutor", "ScheduledExecutorService",
            "ScheduledThreadPoolExecutor", "ForkJoinPool", "Executor");
    private static final Set<String> G_PIPE_OUT = Set.of("PipedOutputStream");
    private static final Set<String> G_PIPE_IN = Set.of("PipedInputStream");

    private final ProbeContext ctx;

    public EventProbe(ProbeContext ctx) {
        this.ctx = ctx;
    }

    public void probeSyncBlock(SynchronizedStmt n) {
        if (ctx.isInsideConstructor()) return;

        Expression monitor = n.getExpression();
        String syncContext = describeMonitor(monitor);

        ExpressionStmt enterLog = AstKit.emitEventStmt("SYNC_ENTER", syncContext, monitor, null);
        ExpressionStmt exitLog = AstKit.emitEventStmt("SYNC_EXIT", syncContext, monitor, null);

        ctx.addPostAction(() -> {
            BlockStmt body = n.getBody();
            NodeList<Statement> original = new NodeList<>(body.getStatements());
            body.getStatements().clear();

            TryStmt innerTry = new TryStmt();
            innerTry.setTryBlock(new BlockStmt(original));
            innerTry.setFinallyBlock(new BlockStmt());

            BlockStmt outerTryBody = new BlockStmt();
            outerTryBody.addStatement(enterLog);
            outerTryBody.addStatement(innerTry);

            BlockStmt outerFinally = new BlockStmt();
            outerFinally.addStatement(exitLog);

            TryStmt outerTry = new TryStmt();
            outerTry.setTryBlock(outerTryBody);
            outerTry.setFinallyBlock(outerFinally);

            body.addStatement(outerTry);
        });
    }

    private String describeMonitor(Expression monitor) {
        if (monitor instanceof ThisExpr) return ctx.getEnclosingClass() + ".this";
        if (monitor instanceof NameExpr ne) {
            String name = ne.getNameAsString();
            if (ctx.isField(name)) {
                return ctx.getEnclosingClass() + "." + name + (ctx.isStaticField(name) ? " (static)" : "");
            }
            String type = ctx.lookupType(name);
            return type != null ? name + " (" + type + ")" : name;
        }
        if (monitor instanceof FieldAccessExpr fae) {
            String fieldName = fae.getNameAsString();
            Expression scope = fae.getScope();
            if (scope instanceof ThisExpr) return ctx.getEnclosingClass() + "." + fieldName;
            String scopeType = EventProbe.inferType(scope, ctx);
            return scopeType != null ? scope.toString() + "(" + scopeType + ")." + fieldName : scope.toString() + "." + fieldName;
        }
        return monitor.toString();
    }

    
    
    
    
    

    
    
    
    
    
    public void probeMethodCall(MethodCallExpr n) {
        if (n.getScope().isEmpty()) return;

        String receiverType = inferType(n.getScope().get(), ctx);
        String method = n.getNameAsString();

        
        if (belongs(receiverType, G_EXEC) && method.equals("submit")) {
            replaceWithTrackedSubmit(n);
            return; 
        }

        
        if (isCompletableFutureAsyncFactory(n, method)) {
            replaceWithTrackedAsync(n, method);
            return;
        }

        OpInfo op = matchEvent(n, receiverType, ctx);
        if (op == null) return;

        Expression dataExpr = op.dataExpr();
        
        boolean placeAfter = op.deferred() || op.afterCall();

        if (op.deferred()) {
            Expression assignee = resolveAssignee(n);
            if (assignee != null) dataExpr = assignee;
            else dataExpr = null;
        }

        ExpressionStmt log = AstKit.emitEventStmt(op.type(), op.detail(), op.targetExpr(), dataExpr);

        
        if (placeAfter && !AstKit.isTerminal(n)) {
            AstKit.addAfter(n, log, ctx);
        } else {
            AstKit.addBefore(n, log, ctx);
        }
    }

    

    
    private void replaceWithTrackedSubmit(MethodCallExpr n) {
        Expression scope = n.getScope().get();
        String scopeStr = scope.toString();

        int submitEventId = AstKit.getEventId(
                "[EVENT] type=EXECUTOR_SUBMIT sync_context=" + scopeStr + ".submit");
        int taskCompleteEventId = AstKit.getEventId(
                "[EVENT] type=TASK_COMPLETE sync_context=" + scopeStr + ".task.complete");

        
        ctx.addPostAction(() -> {
            int argCount = n.getArguments().size();
            
            
            String trackedMethod = (argCount >= 2) ? "trackedSubmitWithResult" : "trackedSubmit";

            MethodCallExpr replacement = new MethodCallExpr(
                    new NameExpr(AstKit.LOGGER_FQN), trackedMethod);

            
            replacement.addArgument(scope.clone());
            
            for (Expression arg : n.getArguments()) {
                replacement.addArgument(arg.clone());
            }
            
            replacement.addArgument(String.valueOf(submitEventId));
            replacement.addArgument(String.valueOf(taskCompleteEventId));

            replacement.setData(AstKit.INSTRUMENTED_KEY, true);
            n.replace(replacement);
        });
    }

    

    
    private boolean isCompletableFutureAsyncFactory(MethodCallExpr n, String method) {
        if (!Set.of("supplyAsync", "runAsync").contains(method)) return false;
        if (n.getScope().isEmpty()) return false;
        Expression scope = n.getScope().get();
        if (scope instanceof NameExpr ne) {
            return "CompletableFuture".equals(ne.getNameAsString());
        }
        return false;
    }

    
    private void replaceWithTrackedAsync(MethodCallExpr n, String method) {
        int taskCompleteEventId = AstKit.getEventId(
                "[EVENT] type=TASK_COMPLETE sync_context=CompletableFuture." + method + ".task.complete");

        ctx.addPostAction(() -> {
            String trackedMethod = method.equals("supplyAsync")
                    ? "trackedSupplyAsync" : "trackedRunAsync";

            MethodCallExpr replacement = new MethodCallExpr(
                    new NameExpr(AstKit.LOGGER_FQN), trackedMethod);

            
            for (Expression arg : n.getArguments()) {
                replacement.addArgument(arg.clone());
            }
            
            replacement.addArgument(String.valueOf(taskCompleteEventId));

            replacement.setData(AstKit.INSTRUMENTED_KEY, true);
            n.replace(replacement);
        });
    }

    

    public static OpInfo matchEvent(MethodCallExpr call, String receiverType, ProbeContext ctx) {
        if (call.getScope().isEmpty()) return null;

        Expression scope = call.getScope().get();
        String method = call.getNameAsString();
        String scopeStr = scope.toString();

        
        if (method.equals("wait"))
            return tagAfter("OBJ_WAIT", scopeStr + ".wait", scope, null);       
        if (method.equals("notify"))
            return tag("OBJ_NOTIFY", scopeStr + ".notify", scope, null, false);
        if (method.equals("notifyAll"))
            return tag("OBJ_NOTIFY_ALL", scopeStr + ".notifyAll", scope, null, false);

        
        if (scope instanceof MethodCallExpr chained) {
            OpInfo rwOp = matchRwLockChain(chained, method, ctx);
            if (rwOp != null) return rwOp;
        }

        
        if (belongs(receiverType, G_LOCK)) {
            if (Set.of("lock", "lockInterruptibly", "tryLock").contains(method))
                return tagAfter("LOCK_ACQUIRE", scopeStr + "." + method, scope, null); 
            if (method.equals("unlock"))
                return tag("LOCK_RELEASE", scopeStr + ".unlock", scope, null, false);
        }

        
        if (belongs(receiverType, G_COND)) {
            if (method.startsWith("await"))
                return tagAfter("CONDITION_AWAIT", scopeStr + "." + method, scope, null); 
            if (method.equals("signal"))
                return tag("CONDITION_SIGNAL", scopeStr + ".signal", scope, null, false);
            if (method.equals("signalAll"))
                return tag("CONDITION_SIGNAL_ALL", scopeStr + ".signalAll", scope, null, false);
        }

        
        if (belongs(receiverType, G_QUEUE)) {
            if (method.equals("put"))
                return tagAfter("QUEUE_PUT", scopeStr + ".put", scope, leadingArg(call)); 
            if (Set.of("offer", "add").contains(method))
                return tag("QUEUE_PUT", scopeStr + "." + method, scope, leadingArg(call), false);
            if (Set.of("take", "poll", "remove", "peek").contains(method))
                return tag("QUEUE_TAKE", scopeStr + "." + method, scope, null, true); 
        }

        
        if (belongs(receiverType, G_SEMA)) {
            if (Set.of("acquire", "acquireUninterruptibly", "tryAcquire").contains(method))
                return tagAfter("SEMAPHORE_ACQUIRE", scopeStr + "." + method, scope, null); 
            if (method.equals("release"))
                return tag("SEMAPHORE_RELEASE", scopeStr + ".release", scope, null, false);
        }

        
        if (belongs(receiverType, G_LATCH)) {
            if (method.equals("await"))
                return tagAfter("LATCH_AWAIT", scopeStr + ".await", scope, null);         
            if (method.equals("countDown"))
                return tag("LATCH_COUNT_DOWN", scopeStr + ".countDown", scope, null, false);
        }

        
        if (belongs(receiverType, G_BARRIER)) {
            if (method.equals("await"))
                return tagAfter("BARRIER_AWAIT", scopeStr + ".await", scope, null);       
        }

        
        if (belongs(receiverType, G_EXCH)) {
            if (method.equals("exchange"))
                return tagAfter("EXCHANGER_EXCHANGE", scopeStr + ".exchange", scope, leadingArg(call)); 
        }

        
        if (belongs(receiverType, G_PHASER)) {
            Set<String> blockingPhaser = Set.of(
                    "arriveAndAwaitAdvance", "awaitAdvance", "awaitAdvanceInterruptibly");
            if (blockingPhaser.contains(method))
                return tagAfter("PHASER_" + method.toUpperCase(), scopeStr + "." + method, scope, null);
            if (Set.of("arrive", "arriveAndDeregister", "register").contains(method))
                return tag("PHASER_" + method.toUpperCase(), scopeStr + "." + method, scope, null, false);
        }

        
        if (belongs(receiverType, G_FUTURE)) {
            if (method.equals("complete"))
                return tag("FUTURE_COMPLETE", scopeStr + ".complete", scope, leadingArg(call), false);
            if (method.equals("completeExceptionally"))
                return tag("FUTURE_COMPLETE", scopeStr + ".completeExceptionally", scope, leadingArg(call), false);
            
            if (Set.of("get", "join", "getNow").contains(method))
                return tag("FUTURE_GET", scopeStr + "." + method, scope, null, true);
        }

        
        if (belongs(receiverType, G_STAMPED)) {
            Set<String> blockingStamped = Set.of("readLock", "writeLock");
            String label = switch (method) {
                case "readLock"              -> "STAMPEDLOCK_READLOCK";
                case "writeLock"             -> "STAMPEDLOCK_WRITELOCK";
                case "tryReadLock"           -> "STAMPEDLOCK_TRYREADLOCK";
                case "tryWriteLock"          -> "STAMPEDLOCK_TRYWRITELOCK";
                case "unlockRead"            -> "STAMPEDLOCK_UNLOCKREAD";
                case "unlockWrite"           -> "STAMPEDLOCK_UNLOCKWRITE";
                case "unlock"                -> "STAMPEDLOCK_UNLOCK";
                case "tryOptimisticRead"     -> "STAMPEDLOCK_TRY_OPTIMISTIC_READ";
                case "tryConvertToReadLock"  -> "STAMPEDLOCK_CONVERT_TO_READ";
                case "tryConvertToWriteLock" -> "STAMPEDLOCK_CONVERT_TO_WRITE";
                default -> null;
            };
            if (label != null) {
                if (blockingStamped.contains(method))
                    return tagAfter(label, scopeStr + "." + method, scope, null);
                else
                    return tag(label, scopeStr + "." + method, scope, null, false);
            }
        }

        
        
        
        if (belongs(receiverType, G_EXEC)) {
            String label = switch (method) {
                case "submit", "execute", "invokeAll", "invokeAny",
                     "schedule", "scheduleAtFixedRate", "scheduleWithFixedDelay"
                     -> "EXECUTOR_SUBMIT";
                case "shutdown"          -> "EXECUTOR_SHUTDOWN";
                case "shutdownNow"       -> "EXECUTOR_SHUTDOWNNOW";
                case "awaitTermination"  -> "EXECUTOR_AWAIT_TERMINATION";
                default -> null;
            };
            if (label != null) {
                if (method.equals("awaitTermination"))
                    return tagAfter(label, scopeStr + "." + method, scope, null);         
                else
                    return tag(label, scopeStr + "." + method, scope, null, false);
            }
        }

        
        if (belongs(receiverType, G_PIPE_OUT) && method.equals("write"))
            return tag("PIPED_WRITE", scopeStr + ".write", scope, leadingArg(call), false);
        if (belongs(receiverType, G_PIPE_IN) && method.equals("read"))
            return tag("PIPED_READ", scopeStr + ".read", scope, null, true);              

        return null;
    }

    private static OpInfo matchRwLockChain(MethodCallExpr chained, String outerMethod, ProbeContext ctx) {
        if (chained.getScope().isEmpty()) return null;
        Expression outerScope = chained.getScope().get();
        String outerScopeType = inferType(outerScope, ctx);
        String innerMethod = chained.getNameAsString();

        if (!belongs(outerScopeType, G_RWLOCK)) return null;

        String kind = innerMethod.equals("readLock") ? "READ"
                    : (innerMethod.equals("writeLock") ? "WRITE" : null);
        if (kind == null) return null;

        Expression oid = chained;
        if (Set.of("lock", "lockInterruptibly", "tryLock").contains(outerMethod))
            return tagAfter("RWLOCK_" + kind + "_LOCK",
                    outerScope + "." + innerMethod + "()." + outerMethod, oid, null);  
        if (outerMethod.equals("unlock"))
            return tag("RWLOCK_" + kind + "_UNLOCK",
                    outerScope + "." + innerMethod + "().unlock", oid, null, false);
        return null;
    }

    public static String inferType(Expression expr, ProbeContext ctx) {
        if (expr instanceof NameExpr ne) return ctx.lookupType(ne.getNameAsString());
        if (expr instanceof FieldAccessExpr fae) return ctx.lookupType(fae.getNameAsString());
        if (expr instanceof MethodCallExpr mc) {
            String m = mc.getNameAsString();
            if (mc.getScope().isPresent()) {
                String st = inferType(mc.getScope().get(), ctx);
                if (belongs(st, G_RWLOCK) && (m.equals("readLock") || m.equals("writeLock"))) return "Lock";
                if (belongs(st, G_LOCK) && m.equals("newCondition")) return "Condition";
            }
            if (Set.of("supplyAsync", "runAsync", "completedFuture", "allOf", "anyOf",
                    "thenApply", "thenApplyAsync", "thenAccept", "thenAcceptAsync",
                    "thenRun", "thenRunAsync", "thenCompose", "thenComposeAsync",
                    "thenCombine", "thenCombineAsync", "whenComplete", "whenCompleteAsync",
                    "handle", "handleAsync", "exceptionally").contains(m))
                return "CompletableFuture";
            if (m.startsWith("newFixed") || m.startsWith("newCached") || m.startsWith("newSingle")
                    || m.startsWith("newScheduled") || m.startsWith("newWorkStealing"))
                return "ExecutorService";
        }
        if (expr instanceof ObjectCreationExpr oce) return oce.getType().getNameAsString();
        return null;
    }

    private static Expression resolveAssignee(MethodCallExpr n) {
        var parent = n.getParentNode().orElse(null);
        if (parent instanceof VariableDeclarator vd) return new NameExpr(vd.getNameAsString());
        if (parent instanceof AssignExpr assign) return assign.getTarget();
        return null;
    }

    private static boolean belongs(String type, Set<String> group) {
        return type != null && group.contains(ProbeContext.stripGenerics(type));
    }

    private static Expression leadingArg(MethodCallExpr call) {
        return call.getArguments().isEmpty() ? null : call.getArgument(0);
    }

    
    private static OpInfo tag(String type, String detail, Expression target,
                              Expression data, boolean deferred) {
        return new OpInfo(type, detail, target, data, deferred, false);
    }

    
    private static OpInfo tagAfter(String type, String detail, Expression target,
                                   Expression data) {
        return new OpInfo(type, detail, target, data, false, true);
    }
}