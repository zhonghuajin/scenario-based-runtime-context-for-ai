package com.example.instrumentor.happens.before;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.util.Set;

public class HbVisitor extends ModifierVisitor<Void> {

    private final ProbeContext ctx;
    private final EventProbe eventProbe;
    private final VarProbe   varProbe;
    private boolean muteVarProbe = false;

    
    private static final Set<String> STREAM_METHODS = Set.of(
            "filter", "map", "flatMap", "forEach", "forEachOrdered",
            "reduce", "collect", "anyMatch", "allMatch", "noneMatch", "peek"
    );

    public HbVisitor(ProbeContext ctx) {
        this.ctx        = ctx;
        this.eventProbe = new EventProbe(ctx);
        this.varProbe   = new VarProbe(ctx);
    }

    @Override
    public Visitable visit(ClassOrInterfaceDeclaration n, Void arg) {
        String prev = ctx.getEnclosingClass();
        ctx.setEnclosingClass(n.getNameAsString());
        Visitable r = super.visit(n, arg);
        ctx.setEnclosingClass(prev);
        return r;
    }

    @Override
    public Visitable visit(BlockStmt n, Void arg) {
        ctx.pushScope();
        Visitable r = super.visit(n, arg);
        ctx.popScope();
        return r;
    }

    @Override
    public Visitable visit(MethodDeclaration n, Void arg) {
        boolean prevStatic = ctx.isInStaticScope();
        CallableDeclaration<?> prevC = ctx.getCurrentCallable();

        ctx.setCurrentCallable(n);
        ctx.setActiveLambda(null);
        ctx.setInStaticScope(n.isStatic());
        muteVarProbe = isTrivialAccessor(n);
        ctx.pushScope();

        Visitable r = super.visit(n, arg);

        ctx.popScope();
        ctx.setInStaticScope(prevStatic);
        ctx.setCurrentCallable(prevC);
        muteVarProbe = false;
        return r;
    }

    @Override
    public Visitable visit(ConstructorDeclaration n, Void arg) {
        boolean prevStatic = ctx.isInStaticScope();
        CallableDeclaration<?> prevC = ctx.getCurrentCallable();

        ctx.setCurrentCallable(n);
        ctx.setActiveLambda(null);
        ctx.setInStaticScope(false);
        ctx.pushScope();

        Visitable r = super.visit(n, arg);

        ctx.popScope();
        ctx.setInStaticScope(prevStatic);
        ctx.setCurrentCallable(prevC);
        return r;
    }

    @Override
    public Visitable visit(InitializerDeclaration n, Void arg) {
        boolean prevStatic = ctx.isInStaticScope();
        CallableDeclaration<?> prevC = ctx.getCurrentCallable();

        ctx.setCurrentCallable(null);
        ctx.setActiveLambda(null);
        ctx.setInStaticScope(n.isStatic());
        ctx.pushScope();

        Visitable r = super.visit(n, arg);

        ctx.popScope();
        ctx.setInStaticScope(prevStatic);
        ctx.setCurrentCallable(prevC);
        return r;
    }

    @Override
    public Visitable visit(LambdaExpr n, Void arg) {
        
        if (isStreamApiCall(n)) {
            return n;
        }

        
        if (n.getBody() != null && n.getBody().isExpressionStmt()) {
            Expression expr = n.getBody().asExpressionStmt().getExpression();
            
            
            if (expr instanceof AssignExpr || 
                (expr instanceof UnaryExpr && isMutatingUnary((UnaryExpr) expr))) {
                BlockStmt blockStmt = new BlockStmt();
                blockStmt.addStatement(expr);
                n.setBody(blockStmt);
            }
            
            
        }

        
        LambdaExpr prev = ctx.getActiveLambda();
        ctx.setActiveLambda(n);
        ctx.pushScope();

        for (var p : n.getParameters()) {
            String typeName = p.getType().asString();
            if (!"UnknownType".equals(typeName))
                ctx.registerType(p.getNameAsString(), typeName);
        }

        Visitable r = super.visit(n, arg);
        
        ctx.popScope();
        ctx.setActiveLambda(prev);
        return r;
    }

    @Override
    public Visitable visit(FieldDeclaration n, Void arg) {
        boolean prevStatic = ctx.isInStaticScope();
        ctx.setInStaticScope(n.isStatic());

        for (VariableDeclarator vd : n.getVariables()) {
            ctx.registerType(vd.getNameAsString(), vd.getType().asString());
            ctx.registerField(vd.getNameAsString(), n.isStatic(), n.isFinal());
        }

        Visitable r = super.visit(n, arg);
        ctx.setInStaticScope(prevStatic);
        return r;
    }

    @Override
    public Visitable visit(VariableDeclarationExpr n, Void arg) {
        String baseType = n.getElementType().asString();
        for (VariableDeclarator vd : n.getVariables()) {
            String vType = vd.getType().asString();
            ctx.registerType(vd.getNameAsString(), vType.equals("var") ? baseType : vType);
        }
        return super.visit(n, arg);
    }

    @Override
    public Visitable visit(SynchronizedStmt n, Void arg) {
        eventProbe.probeSyncBlock(n);
        return super.visit(n, arg);
    }

    @Override
    public Visitable visit(MethodCallExpr n, Void arg) {
        if (n.containsData(AstKit.INSTRUMENTED_KEY)) return n;
        Visitable r = super.visit(n, arg);
        if (!ctx.isInsideConstructor()) eventProbe.probeMethodCall(n);
        return r;
    }

    @Override
    public Visitable visit(NameExpr n, Void arg) {
        if (n.containsData(AstKit.INSTRUMENTED_KEY)) return n;
        if (ctx.isLhs(ProbeContext.nodeKey(n))) return super.visit(n, arg);

        if (n.getParentNode().isPresent() && n.getParentNode().get() instanceof ArrayAccessExpr aae) {
            if (aae.getName() == n) return super.visit(n, arg);
        }

        Visitable r = super.visit(n, arg);
        if (!ctx.isInsideConstructor() && !muteVarProbe) varProbe.probeRead(n);
        return r;
    }

    @Override
    public Visitable visit(FieldAccessExpr n, Void arg) {
        if (n.containsData(AstKit.INSTRUMENTED_KEY)) return n;
        if (ctx.isLhs(ProbeContext.nodeKey(n))) return super.visit(n, arg);

        if (n.getParentNode().isPresent() && n.getParentNode().get() instanceof ArrayAccessExpr aae) {
            if (aae.getName() == n) return super.visit(n, arg);
        }

        Visitable r = super.visit(n, arg);
        if (!ctx.isInsideConstructor() && !muteVarProbe) varProbe.probeFieldRead(n);
        return r;
    }

    @Override
    public Visitable visit(ArrayAccessExpr n, Void arg) {
        if (n.containsData(AstKit.INSTRUMENTED_KEY)) return n;
        if (ctx.isLhs(ProbeContext.nodeKey(n))) return super.visit(n, arg);
        Visitable r = super.visit(n, arg);
        if (!ctx.isInsideConstructor() && !muteVarProbe) varProbe.probeArrayRead(n);
        return r;
    }

    @Override
    public Visitable visit(AssignExpr n, Void arg) {
        if (n.containsData(AstKit.INSTRUMENTED_KEY)) return n;
        Expression target = n.getTarget();
        String key = ProbeContext.nodeKey(target);
        ctx.markLhs(key);

        Visitable r = super.visit(n, arg);

        ctx.unmarkLhs(key);
        if (!ctx.isInsideConstructor() && !muteVarProbe) varProbe.probeWrite(n);
        return r;
    }

    @Override
    public Visitable visit(UnaryExpr n, Void arg) {
        if (n.containsData(AstKit.INSTRUMENTED_KEY)) return n;
        UnaryExpr.Operator op = n.getOperator();
        boolean mutating = op == UnaryExpr.Operator.PREFIX_INCREMENT
                || op == UnaryExpr.Operator.POSTFIX_INCREMENT
                || op == UnaryExpr.Operator.PREFIX_DECREMENT
                || op == UnaryExpr.Operator.POSTFIX_DECREMENT;

        if (mutating) {
            String key = ProbeContext.nodeKey(n.getExpression());
            ctx.markLhs(key);
            Visitable r = super.visit(n, arg);
            ctx.unmarkLhs(key);
            if (!ctx.isInsideConstructor() && !muteVarProbe) varProbe.probeIncDec(n);
            return r;
        }
        return super.visit(n, arg);
    }

    

    private boolean isStreamApiCall(LambdaExpr n) {
        if (n.getParentNode().isPresent() && n.getParentNode().get() instanceof MethodCallExpr call) {
            return STREAM_METHODS.contains(call.getNameAsString());
        }
        return false;
    }

    private boolean isMutatingUnary(UnaryExpr expr) {
        UnaryExpr.Operator op = expr.getOperator();
        return op == UnaryExpr.Operator.PREFIX_INCREMENT || 
               op == UnaryExpr.Operator.POSTFIX_INCREMENT || 
               op == UnaryExpr.Operator.PREFIX_DECREMENT || 
               op == UnaryExpr.Operator.POSTFIX_DECREMENT;
    }

    private boolean isTrivialAccessor(MethodDeclaration n) {
        if (n.getBody().isEmpty()) return false;
        BlockStmt body = n.getBody().get();
        if (body.getStatements().size() > 2) return false;

        String name = n.getNameAsString();
        boolean getter = name.startsWith("get") || name.startsWith("is");
        boolean setter = name.startsWith("set");
        if (!getter && !setter) return false;

        for (Statement stmt : body.getStatements()) {
            if (getter && stmt instanceof ReturnStmt) return true;
            if (setter && stmt.isExpressionStmt()) {
                Expression expr = stmt.asExpressionStmt().getExpression();
                if (expr instanceof AssignExpr) return true;
            }
        }
        return false;
    }
}