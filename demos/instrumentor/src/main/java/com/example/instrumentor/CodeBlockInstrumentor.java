package com.example.instrumentor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;

import java.util.ArrayList;
import java.util.List;


public class CodeBlockInstrumentor {

    
    
    

    public static void normalizeBraces(CompilationUnit cu) {
        
        for (IfStmt ifStmt : cu.findAll(IfStmt.class)) {
            if (!(ifStmt.getThenStmt() instanceof BlockStmt)) {
                ifStmt.setThenStmt(wrapInBlock(ifStmt.getThenStmt()));
            }
            if (ifStmt.getElseStmt().isPresent()) {
                Statement elseStmt = ifStmt.getElseStmt().get();
                if (!(elseStmt instanceof BlockStmt) && !(elseStmt instanceof IfStmt)) {
                    ifStmt.setElseStmt(wrapInBlock(elseStmt));
                }
            }
        }

        
        for (ForStmt forStmt : cu.findAll(ForStmt.class)) {
            if (!(forStmt.getBody() instanceof BlockStmt)) {
                forStmt.setBody(wrapInBlock(forStmt.getBody()));
            }
        }

        
        for (ForEachStmt forEachStmt : cu.findAll(ForEachStmt.class)) {
            if (!(forEachStmt.getBody() instanceof BlockStmt)) {
                forEachStmt.setBody(wrapInBlock(forEachStmt.getBody()));
            }
        }

        
        for (WhileStmt whileStmt : cu.findAll(WhileStmt.class)) {
            if (!(whileStmt.getBody() instanceof BlockStmt)) {
                whileStmt.setBody(wrapInBlock(whileStmt.getBody()));
            }
        }

        
        for (DoStmt doStmt : cu.findAll(DoStmt.class)) {
            if (!(doStmt.getBody() instanceof BlockStmt)) {
                doStmt.setBody(wrapInBlock(doStmt.getBody()));
            }
        }

        
        for (LambdaExpr lambda : cu.findAll(LambdaExpr.class)) {
            if (lambda.getExpressionBody().isPresent()) {
                Expression expr = lambda.getExpressionBody().get();
                if (isClearlyVoidExpression(expr)) {
                    lambda.setBody(wrapLambdaExprInBlock(expr));
                }
            }
        }
    }

    private static boolean isClearlyVoidExpression(Expression expr) {
        if (expr instanceof AssignExpr) return true;
        if (expr instanceof UnaryExpr) {
            UnaryExpr.Operator op = ((UnaryExpr) expr).getOperator();
            return op == UnaryExpr.Operator.POSTFIX_INCREMENT
                    || op == UnaryExpr.Operator.POSTFIX_DECREMENT
                    || op == UnaryExpr.Operator.PREFIX_INCREMENT
                    || op == UnaryExpr.Operator.PREFIX_DECREMENT;
        }
        return false;
    }

    private static BlockStmt wrapInBlock(Statement stmt) {
        BlockStmt block = new BlockStmt();
        block.addStatement(stmt.clone());
        return block;
    }

    private static BlockStmt wrapLambdaExprInBlock(Expression expr) {
        BlockStmt block = new BlockStmt();
        if (isVoidContextExpression(expr)) {
            block.addStatement(new ExpressionStmt(expr.clone()));
        } else {
            block.addStatement(new ReturnStmt(expr.clone()));
        }
        return block;
    }

    private static boolean isVoidContextExpression(Expression expr) {
        if (expr instanceof AssignExpr) return true;
        if (expr instanceof UnaryExpr) {
            UnaryExpr.Operator op = ((UnaryExpr) expr).getOperator();
            if (op == UnaryExpr.Operator.POSTFIX_INCREMENT
                    || op == UnaryExpr.Operator.POSTFIX_DECREMENT
                    || op == UnaryExpr.Operator.PREFIX_INCREMENT
                    || op == UnaryExpr.Operator.PREFIX_DECREMENT) {
                return true;
            }
        }
        if (expr instanceof MethodCallExpr) return true;
        return false;
    }

    
    
    

    private static boolean isConstructorBody(BlockStmt block) {
        return block.getParentNode().isPresent()
                && (block.getParentNode().get() instanceof ConstructorDeclaration
                    || block.getParentNode().get() instanceof CompactConstructorDeclaration);
    }

    
    public static void instrumentCU(CompilationUnit cu, String absolutePath) {
        
        List<BlockStmt> blocks = new ArrayList<>(cu.findAll(BlockStmt.class));

        for (BlockStmt block : blocks) {
            
            int beginLine = block.getBegin().map(p -> p.line).orElse(-1);
            if (beginLine == -1) continue;

            String commentText = " " + absolutePath + ":" + beginLine;
            LineComment comment = new LineComment(commentText);

            if (block.getStatements().isEmpty()) {
                
                block.addOrphanComment(comment);
            } else {
                
                if (isConstructorBody(block) && block.getStatement(0) instanceof ExplicitConstructorInvocationStmt) {
                    if (block.getStatements().size() > 1) {
                        block.getStatement(1).setComment(comment);
                    } else {
                        block.addOrphanComment(comment);
                    }
                } else {
                    
                    block.getStatement(0).setComment(comment);
                }
            }
        }
    }
}