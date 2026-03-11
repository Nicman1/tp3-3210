package analyzer.visitors;

import analyzer.ast.*;

import java.io.PrintWriter;
import java.util.Vector;

/**
 * Ce visiteur explore l'AST et génère du code intermédiaire.
 *
 * @author Félix Brunet
 * @author Doriane Olewicki
 * @author Quentin Guidée
 * @author Raphaël Tremblay
 * @version 2025.10.23
 */
public class IntermediateCodeGenFallVisitor extends IntermediateCodeGenVisitor {

    public IntermediateCodeGenFallVisitor(PrintWriter writer) {
        super(writer);
    }

    @Override
    public Object visit(ASTIfStmt node, Object data) {
        String sNext = (String) data;
        Node condNode = node.jjtGetChild(0);
        Node thenNode = node.jjtGetChild(1);
        boolean hasElse = node.jjtGetNumChildren() > 2;
        Node elseNode = hasElse ? node.jjtGetChild(2) : null;

        if (hasElse) {
            BoolLabel bl = new BoolLabel(FALL, newLabel());
            condNode.jjtAccept(this, bl);
            thenNode.jjtAccept(this, sNext);
            gen("goto " + sNext);
            label(bl.lFalse);
            elseNode.jjtAccept(this, sNext);
        } else {
            BoolLabel bl = new BoolLabel(FALL, sNext);
            condNode.jjtAccept(this, bl);
            thenNode.jjtAccept(this, sNext);
        }
        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        String sNext = (String) data;
        Node condNode = node.jjtGetChild(0);
        Node bodyNode = node.jjtGetChild(1);
        String begin = newLabel();
        BoolLabel bLabels = new BoolLabel(FALL, sNext);

        label(begin);
        condNode.jjtAccept(this, bLabels);
        bodyNode.jjtAccept(this, begin);
        gen("goto " + begin);
        return null;
    }

    @Override
    public Object visit(ASTForStmt node, Object data) {
        String sNext = (String) data;
        ForParts parts = splitForParts(node);
        String begin = newLabel();
        String bodyNext = newLabel();
        if (parts.init != null) parts.init.jjtAccept(this, null);
        label(begin);
        if (parts.cond != null) {
            BoolLabel bLabels = new BoolLabel(FALL, sNext);
            parts.cond.jjtAccept(this, bLabels);
        }
        if (parts.body != null) parts.body.jjtAccept(this, bodyNext);
        label(bodyNext);
        if (parts.update != null) parts.update.jjtAccept(this, null);
        gen("goto " + begin);
        return null;
    }

    @Override
    public Object visit(ASTDeclareStmt node, Object data) {
        String identifier = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        VarType varType = node.getValue().equals("bool") ? VarType.BOOL : VarType.INT;
        SymbolTable.put(identifier, varType);
        if (node.jjtGetNumChildren() > 1 && node.jjtGetChild(1) != null) {
            String next = data instanceof String ? (String) data : newLabel();
            if (varType == VarType.BOOL) {
                String lFalse = newLabel();
                BoolLabel bLabels = new BoolLabel(FALL, lFalse);
                node.jjtGetChild(1).jjtAccept(this, bLabels);
                gen(identifier + " = 1");
                gen("goto " + next);
                label(lFalse);
                gen(identifier + " = 0");
            } else {
                Object addr = node.jjtGetChild(1).jjtAccept(this, null);
                if (addr != null) gen(identifier + " = " + addr);
            }
        } else {
            gen(identifier + " = 0");
        }
        return null;
    }

    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        String identifier = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        Node exprNode = node.jjtGetChild(1);
        VarType varType = SymbolTable.get(identifier);

        if (varType == VarType.BOOL) {
            String next = (String) data;
            String lFalse = newLabel();
            BoolLabel bLabels = new BoolLabel(FALL, lFalse);
            String trueTarget = (String) exprNode.jjtAccept(this, bLabels);
            if (trueTarget != null) label(trueTarget);
            gen(identifier + " = 1");
            gen("goto " + next);
            label(lFalse);
            gen(identifier + " = 0");
        } else {
            Object addr = exprNode.jjtAccept(this, null);
            if (addr != null) gen(identifier + " = " + addr);
        }
        return null;
    }

    @Override
    public Object visit(ASTTernary node, Object data) {
        if (data instanceof BoolLabel) {
            BoolLabel bLabels = (BoolLabel) data;
            Node cond = node.jjtGetChild(0);
            Node thenExpr = node.jjtGetChild(1);
            Node elseExpr = node.jjtGetChild(2);
            String thenLabel = newLabel();
            String elseLabel = newLabel();
            String trueTarget = newLabel();
            BoolLabel condLabels = new BoolLabel(thenLabel, elseLabel);
            cond.jjtAccept(this, condLabels);
            label(thenLabel);
            thenExpr.jjtAccept(this, new BoolLabel(trueTarget, bLabels.lFalse));
            label(elseLabel);
            elseExpr.jjtAccept(this, new BoolLabel(trueTarget, bLabels.lFalse));
            return trueTarget;
        } else {
            return genTernaryValueFall(node);
        }
    }

    @Override
    public Object visit(ASTLogExpr node, Object data) {
        if (data instanceof BoolLabel) {
            BoolLabel bLabels = (BoolLabel) data;
            Vector<String> ops = node.getOps();
            if (!ops.isEmpty() && ops.get(0).equals("&&")) {
                BoolLabel b1 = new BoolLabel(FALL, bLabels.lFalse.equals(FALL) ? newLabel() : bLabels.lFalse);
                node.jjtGetChild(0).jjtAccept(this, b1);
                node.jjtGetChild(1).jjtAccept(this, bLabels);
                if (bLabels.lFalse.equals(FALL)) {
                    label(b1.lFalse);
                }
            } else if (!ops.isEmpty() && ops.get(0).equals("||")) {
                String b1True = bLabels.lTrue.equals(FALL) ? newLabel() : bLabels.lTrue;
                BoolLabel b1 = new BoolLabel(b1True, FALL);
                node.jjtGetChild(0).jjtAccept(this, b1);
                node.jjtGetChild(1).jjtAccept(this, bLabels);
                if (bLabels.lTrue.equals(FALL)) {
                    label(b1True);
                }
            } else {
                node.jjtGetChild(0).jjtAccept(this, bLabels);
            }
        } else {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }
        return null;
    }

    @Override
    public Object visit(ASTCompExpr node, Object data) {
        if (data instanceof BoolLabel) {
            BoolLabel bLabels = (BoolLabel) data;
            if (node.jjtGetNumChildren() == 2 && node.getValue() != null) {
                Object e1 = node.jjtGetChild(0).jjtAccept(this, null);
                Object e2 = node.jjtGetChild(1).jjtAccept(this, null);
                String op = node.getValue();
                if (!FALL.equals(bLabels.lTrue) && !FALL.equals(bLabels.lFalse)) {
                    gen("if " + e1 + " " + op + " " + e2 + " goto " + bLabels.lTrue);
                    gen("goto " + bLabels.lFalse);
                } else if (!FALL.equals(bLabels.lTrue)) {
                    gen("if " + e1 + " " + op + " " + e2 + " goto " + bLabels.lTrue);
                } else if (!FALL.equals(bLabels.lFalse)) {
                    gen("ifFalse " + e1 + " " + op + " " + e2 + " goto " + bLabels.lFalse);
                }
            } else {
                node.jjtGetChild(0).jjtAccept(this, bLabels);
            }
        } else {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }
        return null;
    }

    @Override
    public Object visit(ASTNotExpr node, Object data) {
        if (data instanceof BoolLabel) {
            node.jjtGetChild(0).jjtAccept(this, ((BoolLabel) data).swapped());
        }
        return null;
    }

    @Override
    public Object visit(ASTBoolValue node, Object data) {
        if (data instanceof BoolLabel) {
            BoolLabel bLabels = (BoolLabel) data;
            if (node.getValue()) {
                if (!FALL.equals(bLabels.lTrue)) gen("goto " + bLabels.lTrue);
            } else {
                if (!FALL.equals(bLabels.lFalse)) gen("goto " + bLabels.lFalse);
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        if (data instanceof BoolLabel) {
            BoolLabel bLabels = (BoolLabel) data;
            String id = node.getValue();
            if (!FALL.equals(bLabels.lTrue) && !FALL.equals(bLabels.lFalse)) {
                gen("if " + id + " == 1 goto " + bLabels.lTrue);
                gen("goto " + bLabels.lFalse);
            } else if (!FALL.equals(bLabels.lTrue)) {
                gen("if " + id + " == 1 goto " + bLabels.lTrue);
            } else if (!FALL.equals(bLabels.lFalse)) {
                gen("ifFalse " + id + " == 1 goto " + bLabels.lFalse);
            }
        } else {
            return node.getValue();
        }
        return null;
    }
}
