package com.example.instrumentor.happens.before;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;

public class ProbeContext {

    private CallableDeclaration<?> currentCallable;
    private LambdaExpr activeLambda;
    private boolean inStaticScope = false;
    private String enclosingClass = "";

    
    private final Deque<Map<String, String>> typeScopes = new ArrayDeque<>();
    private final Set<String> fieldSet = new HashSet<>();
    private final Set<String> staticFields = new HashSet<>();
    private final Set<String> immutableFields = new HashSet<>();
    private final Set<String> lhsMarkers = new HashSet<>();

    
    private final List<Runnable> postActions = new ArrayList<>();

    public ProbeContext() {
        typeScopes.push(new HashMap<>()); 
    }

    public void setCurrentCallable(CallableDeclaration<?> m) { this.currentCallable = m; }
    public CallableDeclaration<?> getCurrentCallable() { return currentCallable; }

    public void setActiveLambda(LambdaExpr l) { this.activeLambda = l; }
    public LambdaExpr getActiveLambda() { return activeLambda; }

    public void setInStaticScope(boolean s) { this.inStaticScope = s; }
    public boolean isInStaticScope() { return inStaticScope; }

    public void setEnclosingClass(String name) { this.enclosingClass = name; }
    public String getEnclosingClass() { return enclosingClass; }

    public boolean isInsideConstructor() { return currentCallable instanceof ConstructorDeclaration; }
    public boolean isInsideLambda() { return activeLambda != null; }

    public void pushScope() { typeScopes.push(new HashMap<>()); }
    public void popScope() { if (typeScopes.size() > 1) typeScopes.pop(); }

    public void registerType(String name, String type) { 
        typeScopes.peek().put(name, type); 
    }

    public String lookupType(String name) {
        for (Map<String, String> scope : typeScopes) {
            if (scope.containsKey(name)) return scope.get(name);
        }
        return null;
    }

    public void registerField(String name, boolean isStatic, boolean isFinal) {
        fieldSet.add(name);
        if (isStatic) staticFields.add(name);
        if (isFinal) immutableFields.add(name);
    }

    public boolean isField(String name) { return fieldSet.contains(name); }
    public boolean isStaticField(String name) { return staticFields.contains(name); }
    public boolean isImmutableField(String name) { return immutableFields.contains(name); }

    public void markLhs(String key) { lhsMarkers.add(key); }
    public void unmarkLhs(String key) { lhsMarkers.remove(key); }
    public boolean isLhs(String key) { return lhsMarkers.contains(key); }

    public static String nodeKey(Node node) {
        int line = node.getBegin().map(p -> p.line).orElse(-1);
        int col  = node.getBegin().map(p -> p.column).orElse(-1);
        return line + ":" + col;
    }

    public void addPostAction(Runnable action) {
        postActions.add(action);
    }

    public void executePostActions() {
        for (Runnable action : postActions) {
            action.run();
        }
        postActions.clear();
    }

    private static final Set<String> CONCURRENCY_TYPES = Set.of(
            "Lock", "ReentrantLock", "Condition",
            "ReadWriteLock", "ReentrantReadWriteLock", "StampedLock",
            "Semaphore", "CountDownLatch", "CyclicBarrier",
            "Phaser", "Exchanger",
            "BlockingQueue", "LinkedBlockingQueue", "ArrayBlockingQueue",
            "PriorityBlockingQueue", "SynchronousQueue", "LinkedTransferQueue",
            "CompletableFuture", "Future",
            "ExecutorService", "ThreadPoolExecutor", "ScheduledExecutorService",
            "ForkJoinPool", "Executor",
            "PipedInputStream", "PipedOutputStream",
            "AtomicInteger", "AtomicLong", "AtomicBoolean", "AtomicReference"
    );

    private static final Set<String> EXCLUDED_TYPES = Set.of(
            "TimeUnit", "Executors", "Math", "System", "Runtime",
            "ThreadLocal", "Optional", "Logger"
    );

    public boolean isConcurrencyType(String type) {
        if (type == null) return false;
        return CONCURRENCY_TYPES.contains(stripGenerics(type));
    }

    public boolean isExcludedType(String type) {
        if (type == null) return false;
        return EXCLUDED_TYPES.contains(stripGenerics(type));
    }

    public static String stripGenerics(String type) {
        if (type == null) return null;
        int idx = type.indexOf('<');
        return idx > 0 ? type.substring(0, idx) : type;
    }
}