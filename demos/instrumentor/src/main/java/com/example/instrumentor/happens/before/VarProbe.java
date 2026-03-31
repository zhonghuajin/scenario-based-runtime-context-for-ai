package com.example.instrumentor.happens.before;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;

public class VarProbe {

    private final ProbeContext ctx;

    public VarProbe(ProbeContext ctx) {
        this.ctx = ctx;
    }

    public void probeRead(NameExpr n) {
        String name = n.getNameAsString();
        if (!eligibleName(n, name)) return;
        insertReadLog(n, name, n);
    }

    public void probeFieldRead(FieldAccessExpr n) {
        String name = n.getNameAsString();
        if (!eligibleAccess(n, name)) return;
        insertReadLog(n, name, n.getScope());
    }

    public void probeArrayRead(ArrayAccessExpr n) {
        String name = fieldNameOf(n.getName());
        if (name == null || !passesFilter(name)) return;
        insertReadLog(n, name, AstKit.ownerOf(n.getName(), ctx));
    }

    private void insertReadLog(Expression anchor, String name, Expression ownerBase) {
        String fqn = qualifiedName(ctx.getEnclosingClass(), name);
        Expression owner = AstKit.ownerOf(ownerBase, ctx);
        Expression payload = AstKit.guardPayload(anchor);
        AstKit.addBefore(anchor, AstKit.emitVarStmt("READ", fqn, owner, payload), ctx);
    }

    public void probeWrite(AssignExpr n) {
        Expression target = n.getTarget();
        String name = fieldNameOf(target);
        if (name == null) return;

        if (target instanceof NameExpr ne) {
            if (!eligibleName(ne, name)) return;
        } else if (target instanceof FieldAccessExpr fae) {
            if (!eligibleAccess(fae, name)) return;
        } else if (target instanceof ArrayAccessExpr) {
            if (!passesFilter(name)) return;
        } else {
            return;
        }

        String fqn = qualifiedName(ctx.getEnclosingClass(), name);
        Expression owner = AstKit.ownerOf(
                target instanceof ArrayAccessExpr ? ((ArrayAccessExpr) target).getName() : target, ctx);

        Expression payload;
        if (ctx.isInStaticScope()) {
            Expression rhs = n.getValue();
            payload = isAtom(rhs) ? rhs : target;
        } else {
            payload = target;
        }
        payload = AstKit.guardPayload(payload);

        ExpressionStmt log = AstKit.emitVarStmt("WRITE", fqn, owner, payload);

        if (!AstKit.isTerminal(n)) AstKit.addAfter(n, log, ctx);
        else AstKit.addBefore(n, log, ctx);
    }

    
    
    
    
    
    
    public void probeIncDec(UnaryExpr n) {
        Expression inner = n.getExpression();
        String name = fieldNameOf(inner);
        if (name == null) return;

        if (inner instanceof NameExpr ne) {
            if (!eligibleName(ne, name)) return;
        } else if (inner instanceof FieldAccessExpr fae) {
            if (!eligibleAccess(fae, name)) return;
        } else if (inner instanceof ArrayAccessExpr) {
            if (!passesFilter(name)) return;
        } else {
            return;
        }

        String fqn = qualifiedName(ctx.getEnclosingClass(), name);
        Expression owner = AstKit.ownerOf(
                inner instanceof ArrayAccessExpr ? ((ArrayAccessExpr) inner).getName() : inner, ctx);
        Expression payload = AstKit.guardPayload(inner);

        
        AstKit.addBefore(n, AstKit.emitVarStmt("READ", fqn, owner, payload), ctx);
        
        
        ExpressionStmt writeLog = AstKit.emitVarStmt("WRITE", fqn, owner, payload);
        if (!AstKit.isTerminal(n)) {
            AstKit.addAfter(n, writeLog, ctx);
        } else {
            AstKit.addBefore(n, writeLog, ctx);
        }
    }

    public static boolean isFieldRef(NameExpr n, ProbeContext ctx) {
        String name = n.getNameAsString();
        if (!ctx.isField(name)) return false;
        return !isShadowedByLocal(n, name);
    }

    public static boolean isShadowedByLocal(NameExpr n, String name) {
        
        CallableDeclaration<?> callable = n.findAncestor(CallableDeclaration.class).orElse(null);
        if (callable != null) {
            for (Parameter p : callable.getParameters()) {
                if (p.getNameAsString().equals(name)) return true;
            }
        }

        
        Node cur = n;
        while (cur.getParentNode().isPresent()) {
            cur = cur.getParentNode().get();
            if (cur instanceof LambdaExpr lambda) {
                for (Parameter p : lambda.getParameters()) {
                    if (p.getNameAsString().equals(name)) return true;
                }
            }
            if (cur instanceof TypeDeclaration) break;
        }

        
        cur = n;
        while (cur.getParentNode().isPresent()) {
            cur = cur.getParentNode().get();
            if (cur instanceof BlockStmt block) {
                
                
                
                if (blockDeclaresVar(block.getStatements(), name, n)) return true;
            }
            if (cur instanceof ForEachStmt fe) {
                if (fe.getVariable().getVariables().stream()
                        .anyMatch(v -> v.getNameAsString().equals(name)))
                    return true;
            }
            if (cur instanceof ForStmt fs) {
                for (Expression init : fs.getInitialization()) {
                    if (init instanceof VariableDeclarationExpr vde) {
                        for (VariableDeclarator vd : vde.getVariables()) {
                            if (vd.getNameAsString().equals(name)) return true;
                        }
                    }
                }
            }
            if (cur instanceof TryStmt ts) {
                for (Expression res : ts.getResources()) {
                    if (res instanceof VariableDeclarationExpr vde) {
                        for (VariableDeclarator vd : vde.getVariables()) {
                            if (vd.getNameAsString().equals(name)) return true;
                        }
                    }
                }
            }
            if (cur instanceof CatchClause cc) {
                if (cc.getParameter().getNameAsString().equals(name)) return true;
            }
            if (cur instanceof TypeDeclaration) break;
        }
        return false;
    }

    private boolean eligibleName(NameExpr n, String name) {
        if (!isFieldRef(n, ctx)) return false;
        return passesFilter(name);
    }

    private boolean eligibleAccess(FieldAccessExpr n, String name) {
        if (!ctx.isField(name)) return false;
        if (isJdkScope(n)) return false;
        return passesFilter(name);
    }

    private boolean passesFilter(String name) {
        if (ctx.isImmutableField(name)) return false;
        String type = ctx.lookupType(name);
        if (ctx.isConcurrencyType(type)) return false;
        if (ctx.isExcludedType(type)) return false;
        return true;
    }

    public static String qualifiedName(String className, String fieldName) {
        return className + "." + fieldName;
    }

    private static String fieldNameOf(Expression expr) {
        if (expr instanceof NameExpr ne) return ne.getNameAsString();
        if (expr instanceof FieldAccessExpr fae) return fae.getNameAsString();
        if (expr instanceof ArrayAccessExpr aae) return fieldNameOf(aae.getName());
        return null;
    }

    private static boolean isAtom(Expression e) {
        return e instanceof LiteralExpr || e instanceof NameExpr
                || e instanceof ClassExpr || e instanceof NullLiteralExpr;
    }

    private static final java.util.Set<String> JDK_SCOPES = java.util.Set.of(
            "System", "Math", "TimeUnit", "Executors", "Collections", "Arrays",
            "Objects", "Integer", "Long", "Double", "Float", "Boolean", "Byte",
            "Short", "Character", "String", "Thread", "Runtime");

    private static boolean isJdkScope(FieldAccessExpr n) {
        Expression scope = n.getScope();
        return scope instanceof NameExpr ne && JDK_SCOPES.contains(ne.getNameAsString());
    }

    private static boolean isTerminal(Node node) {
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

    
    
    
    
    private static boolean blockDeclaresVar(NodeList<Statement> stmts, String name, NameExpr target) {
        int targetLine = target.getBegin().map(p -> p.line).orElse(-1);
        int targetCol  = target.getBegin().map(p -> p.column).orElse(-1);

        for (Statement stmt : stmts) {
            if (stmt.isExpressionStmt()) {
                Expression expr = stmt.asExpressionStmt().getExpression();
                if (expr instanceof VariableDeclarationExpr vde) {
                    for (VariableDeclarator vd : vde.getVariables()) {
                        if (vd.getNameAsString().equals(name)) {
                            
                            if (targetLine < 0) return true;
                            int declLine = vd.getBegin().map(p -> p.line).orElse(-1);
                            if (declLine < 0) return true;

                            int declCol = vd.getBegin().map(p -> p.column).orElse(-1);

                            
                            if (declLine < targetLine
                                    || (declLine == targetLine && declCol < targetCol)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}