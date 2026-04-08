package com.example.instrumentor.happens.before;

import java.util.LinkedHashMap;
import java.util.Map;

import com.github.javaparser.ast.DataKey;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

public class AstKit {

    public static final String LOGGER_FQN = "com.example.instrumentor.InstrumentLog";
    public static final DataKey<Boolean> INSTRUMENTED_KEY = new DataKey<Boolean>() {};

    public static final Map<String, Integer> EVENT_DICT = new LinkedHashMap<>();
    private static int eventIdCounter = 1;

    public static synchronized int getEventId(String message) {
        return EVENT_DICT.computeIfAbsent(message, k -> eventIdCounter++);
    }

    public static ExpressionStmt emitEventStmt(String eventType, String synContext,
            Expression targetExpr, Expression dataExpr) {
        String msg = "[EVENT] type=" + eventType + " sync_context=" + synContext;
        return emitStmt(msg, targetExpr, dataExpr);
    }

    public static ExpressionStmt emitVarStmt(String type, String fieldFqn,
            Expression ownerExpr, Expression valueExpr) {
        String msg = "[SHARED_VARIABLE] type=" + type + " field=" + fieldFqn;
        return emitStmt(msg, ownerExpr, valueExpr);
    }

    private static ExpressionStmt emitStmt(String message, Expression idExpr,
            Expression payloadExpr) {

        int eventId = getEventId(message);

        MethodCallExpr call = new MethodCallExpr(new NameExpr(LOGGER_FQN), "staining");

        call.addArgument(String.valueOf(eventId));

        if (idExpr != null) {
            call.addArgument(new MethodCallExpr(
                    new NameExpr("System"), "identityHashCode",
                    new NodeList<>(idExpr.clone())));
        } else {
            call.addArgument("0");
        }

        if (payloadExpr != null) {
            call.addArgument(new MethodCallExpr(
                    new NameExpr(LOGGER_FQN), "getObjectHash",
                    new NodeList<>(payloadExpr.clone())));
        } else {
            call.addArgument("0");
        }

        call.addArgument(new MethodCallExpr(new NameExpr("System"), "nanoTime"));

        ExpressionStmt stmt = new ExpressionStmt(call);
        stmt.setData(INSTRUMENTED_KEY, true);
        return stmt;
    }

    public static void addBefore(Expression anchor, Statement stmt, ProbeContext ctx) {
        if (stmt == null) return;
        if (isUnsafeToInject(anchor)) return;
        ctx.addPostAction(() -> insertStmt(anchor, stmt, true));
    }

    public static void addAfter(Expression anchor, Statement stmt, ProbeContext ctx) {
        if (stmt == null) return;
        if (isUnsafeToInject(anchor)) return;
        ctx.addPostAction(() -> insertStmt(anchor, stmt, false));
    }

    private static boolean isUnsafeToInject(Node node) {
        Node cur = node;
        while (cur.getParentNode().isPresent()) {
            Node parent = cur.getParentNode().get();
            if (parent instanceof BlockStmt) {
                return false;
            }
            if (parent instanceof com.github.javaparser.ast.stmt.IfStmt ||
                parent instanceof com.github.javaparser.ast.stmt.WhileStmt ||
                parent instanceof com.github.javaparser.ast.stmt.DoStmt ||
                parent instanceof com.github.javaparser.ast.stmt.ForStmt ||
                parent instanceof com.github.javaparser.ast.stmt.ForEachStmt ||
                parent instanceof com.github.javaparser.ast.stmt.SwitchStmt ||
                parent instanceof com.github.javaparser.ast.stmt.TryStmt ||
                parent instanceof com.github.javaparser.ast.expr.ConditionalExpr) {
                return true;
            }
            cur = parent;
        }
        return false;
    }

    private static void insertStmt(Expression anchor, Statement stmt, boolean before) {
        Node cur = anchor;
        while (cur.getParentNode().isPresent()) {
            Node parent = cur.getParentNode().get();
            if (parent instanceof BlockStmt block) {
                int idx = indexOf(block.getStatements(), cur);
                if (idx >= 0) {
                    block.getStatements().add(before ? idx : idx + 1, stmt);
                    return;
                }
            }
            if (parent instanceof LambdaExpr lambda && lambda.getBody() == cur) {
                BlockStmt body = new BlockStmt();
                if (before) body.addStatement(stmt);

                if (cur instanceof ExpressionStmt es
                        && lambda.getExpressionBody().isPresent()) {
                    Expression e = es.getExpression();
                    if (needsReturn(e)) {
                        body.addStatement(new ReturnStmt(e));
                    } else {
                        body.addStatement(es);
                    }
                } else if (cur instanceof Statement s) {
                    body.addStatement(s);
                } else if (cur instanceof Expression e) {
                    if (needsReturn(e)) {
                        body.addStatement(new ReturnStmt(e));
                    } else {
                        body.addStatement(new ExpressionStmt(e));
                    }
                }

                if (!before) body.addStatement(stmt);
                lambda.setBody(body);
                return;
            }
            cur = parent;
        }
    }

    private static boolean needsReturn(Expression e) {
        if (e instanceof AssignExpr) return false;
        if (e instanceof UnaryExpr ue) {
            UnaryExpr.Operator op = ue.getOperator();
            return !(op == UnaryExpr.Operator.PREFIX_INCREMENT ||
                     op == UnaryExpr.Operator.POSTFIX_INCREMENT ||
                     op == UnaryExpr.Operator.PREFIX_DECREMENT ||
                     op == UnaryExpr.Operator.POSTFIX_DECREMENT);
        }
        if (e instanceof MethodCallExpr mc) {
            String name = mc.getNameAsString();
            if (name.equals("error") || name.equals("warn") || name.equals("info") ||
                name.equals("debug") || name.equals("trace") || name.equals("print") ||
                name.equals("println") || name.startsWith("set") || name.startsWith("add") ||
                name.startsWith("remove") || name.startsWith("put") || name.startsWith("clear") ||
                name.startsWith("close") || name.startsWith("start") || name.startsWith("stop") ||
                name.startsWith("run") || name.startsWith("accept") || name.startsWith("execute") ||
                name.startsWith("forEach") || name.startsWith("register")) {
                return false;
            }
            if (name.startsWith("get") || name.startsWith("is") || name.startsWith("has") ||
                name.startsWith("create") || name.startsWith("build") || name.startsWith("compute") ||
                name.startsWith("calculate") || name.startsWith("supply") || name.startsWith("call")) {
                return true;
            }
            return true;
        }
        if (e instanceof LiteralExpr || e instanceof NameExpr ||
            e instanceof FieldAccessExpr || e instanceof ObjectCreationExpr) {
            return true;
        }
        return true;
    }

    private static int indexOf(NodeList<Statement> stmts, Node target) {
        for (int i = 0; i < stmts.size(); i++) {
            if (stmts.get(i) == target) return i;
        }
        return -1;
    }

    /**
     * Derives the "owner" expression for a field access, suitable for use
     * as an argument to System.identityHashCode().
     *
     * Key fix: when the target is a NameExpr that is not a field, not a known
     * local variable, and starts with an uppercase letter, it is treated as a
     * class name and converted to ClassName.class (a ClassExpr), because a bare
     * class name like "ApplicationConversionService" is not a valid expression
     * in Java — you need "ApplicationConversionService.class".
     */
    public static Expression ownerOf(Expression target, ProbeContext ctx) {
        if (target instanceof FieldAccessExpr fae) return fae;
        if (target instanceof NameExpr ne) {
            String name = ne.getNameAsString();
            if (ctx.isField(name)) {
                if (ctx.isStaticField(name)) return null;
                return new ThisExpr();
            }
            // If not a field and not a known local/parameter variable, and the name
            // starts with an uppercase letter, it is almost certainly a class name
            // used as the scope of a static field access (e.g. MyClass.someField).
            // Convert it to MyClass.class so the generated code is valid Java.
            if (ctx.lookupType(name) == null
                    && !name.isEmpty()
                    && Character.isUpperCase(name.charAt(0))) {
                return new ClassExpr(new ClassOrInterfaceType(null, name));
            }
            return ne;
        }
        if (target instanceof ThisExpr) return target;
        return null;
    }

    public static Expression guardPayload(Expression payload) {
        if (payload == null) return null;
        if (payload instanceof NameExpr ne && isUninitVar(ne)) {
            return new NullLiteralExpr();
        }
        return payload;
    }

    public static boolean isUninitVar(NameExpr n) {
        String name = n.getNameAsString();
        Node cur = n;
        while (cur.getParentNode().isPresent()) {
            cur = cur.getParentNode().get();
            if (cur instanceof BlockStmt block) {
                for (Statement stmt : block.getStatements()) {
                    if (stmt.isExpressionStmt()) {
                        var expr = stmt.asExpressionStmt().getExpression();
                        if (expr instanceof VariableDeclarationExpr vde) {
                            for (VariableDeclarator vd : vde.getVariables()) {
                                if (vd.getNameAsString().equals(name)) {
                                    return vd.getInitializer().isEmpty();
                                }
                            }
                        }
                    }
                }
            }
            if (cur instanceof com.github.javaparser.ast.body.TypeDeclaration) break;
        }
        return false;
    }

    public static boolean isTerminal(Node node) {
        Node cur = node;
        while (cur.getParentNode().isPresent()) {
            Node parent = cur.getParentNode().get();
            if (parent instanceof BlockStmt) {
                return cur instanceof ReturnStmt || cur instanceof ThrowStmt;
            }
            if (parent instanceof LambdaExpr) return false;
            cur = parent;
        }
        return false;
    }
}