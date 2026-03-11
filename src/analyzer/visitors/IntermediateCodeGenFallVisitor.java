package analyzer.visitors;

import analyzer.ast.*;

import java.io.PrintWriter;
import java.util.HashMap;
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
public class IntermediateCodeGenFallVisitor implements ParserVisitor {
    public static final String FALL = "fall";

    private final PrintWriter m_writer;
    public HashMap<String, VarType> SymbolTable = new HashMap<>();
    private int id = 0;
    private int label = 0;

    public IntermediateCodeGenFallVisitor(PrintWriter writer) {
        m_writer = writer;
    }

    private String newID() { return "_t" + id++; }
    private String newLabel() { return "_L" + label++; }
    private void gen(String s) { m_writer.println(s); }
    private void label(String s) { m_writer.println(s); }

    @Override
    public Object visit(SimpleNode node, Object data) {
        node.childrenAccept(this, data);
        return data;
    }

    @Override
    public Object visit(ASTProgram node, Object data) {
        visit((ASTBlock) node.jjtGetChild(0), data);
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        int n = node.jjtGetNumChildren();
        if (n == 0) return null;
        boolean isInnerBlock = data instanceof String;
        String next = isInnerBlock ? (String) data : newLabel();
        String[] nextLabels = new String[n];
        nextLabels[n - 1] = next;
        for (int i = 0; i < n - 1; i++) nextLabels[i] = newLabel();

        for (int i = 0; i < n; i++) {
            Node child = node.jjtGetChild(i);
            child.jjtAccept(this, nextLabels[i]);
            if (i < n - 1) label(nextLabels[i]);
        }
        if (!isInnerBlock) label(next);
        return null;
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
        Node initNode = null, condNode = null, updateNode = null, bodyNode = null;
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            Node c = node.jjtGetChild(i);
            if (c instanceof ASTDeclareStmt) initNode = c;
            else if (c instanceof ASTExpr) condNode = c;
            else if (c instanceof ASTAssignStmt && updateNode == null) updateNode = c;
            else bodyNode = c;
        }
        String begin = newLabel();
        String bodyNext = newLabel();
        if (initNode != null) initNode.jjtAccept(this, null);
        label(begin);
        if (condNode != null) {
            BoolLabel bLabels = new BoolLabel(FALL, sNext);
            condNode.jjtAccept(this, bLabels);
        }
        if (bodyNode != null) bodyNode.jjtAccept(this, bodyNext);
        label(bodyNext);
        if (updateNode != null) updateNode.jjtAccept(this, null);
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
                Node exprChild = node.jjtGetChild(1);
                Node innerExpr = exprChild.jjtGetNumChildren() > 0 ? exprChild.jjtGetChild(0) : null;
                BoolLabel bLabels = new BoolLabel(
                    (innerExpr != null && innerExpr instanceof ASTTernary) ? newLabel() : FALL,
                    newLabel()
                );
                exprChild.jjtAccept(this, bLabels);
                if (!FALL.equals(bLabels.lTrue)) label(bLabels.lTrue);
                gen(identifier + " = 1");
                gen("goto " + next);
                label(bLabels.lFalse);
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
            BoolLabel bLabels = new BoolLabel(FALL, newLabel());
            exprNode.jjtAccept(this, bLabels);
            if (!FALL.equals(bLabels.lTrue)) label(bLabels.lTrue);
            gen(identifier + " = 1");
            gen("goto " + next);
            label(bLabels.lFalse);
            gen(identifier + " = 0");
        } else {
            Object addr = exprNode.jjtAccept(this, null);
            if (addr != null) gen(identifier + " = " + addr);
        }
        return null;
    }

    @Override
    public Object visit(ASTExpr node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
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
            if (FALL.equals(bLabels.lTrue)) bLabels.lTrue = newLabel();
            BoolLabel condLabels = new BoolLabel(thenLabel, elseLabel);
            cond.jjtAccept(this, condLabels);
            label(thenLabel);
            thenExpr.jjtAccept(this, bLabels);
            label(elseLabel);
            elseExpr.jjtAccept(this, bLabels);
        } else {
            Node cond = node.jjtGetChild(0);
            Node thenExpr = node.jjtGetChild(1);
            Node elseExpr = node.jjtGetChild(2);
            String tmp = newID();
            String elseLabel = newLabel();
            String joinLabel = newLabel();
            BoolLabel bLabels = new BoolLabel(FALL, elseLabel);
            cond.jjtAccept(this, bLabels);
            Object thenAddr = thenExpr.jjtAccept(this, null);
            gen(tmp + " = " + thenAddr);
            gen("goto " + joinLabel);
            label(elseLabel);
            Object elseAddr = elseExpr.jjtAccept(this, null);
            gen(tmp + " = " + elseAddr);
            label(joinLabel);
            return tmp;
        }
        return null;
    }

    private Object codeExtAddMul(SimpleNode node, Object data, Vector<String> ops) {
        if (node.jjtGetNumChildren() == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }
        String tmp = newID();
        Object right = node.jjtGetChild(1).jjtAccept(this, null);
        Object left = node.jjtGetChild(0).jjtAccept(this, null);
        gen(tmp + " = " + left + " " + ops.get(0) + " " + right);
        return tmp;
    }

    @Override
    public Object visit(ASTAddExpr node, Object data) {
        return codeExtAddMul(node, data, node.getOps());
    }

    @Override
    public Object visit(ASTMultExpr node, Object data) {
        return codeExtAddMul(node, data, node.getOps());
    }

    @Override
    public Object visit(ASTNegExpr node, Object data) {
        Object addr = node.jjtGetChild(0).jjtAccept(this, data);
        String tmp = newID();
        gen(tmp + " = - " + addr);
        return tmp;
    }

    @Override
    public Object visit(ASTLogExpr node, Object data) {
        if (data instanceof BoolLabel) {
            BoolLabel bLabels = (BoolLabel) data;
            Vector<String> ops = node.getOps();
            if (ops.size() > 0 && ops.get(0).equals("&&")) {
                BoolLabel b1 = new BoolLabel(FALL, bLabels.lFalse.equals(FALL) ? newLabel() : bLabels.lFalse);
                node.jjtGetChild(0).jjtAccept(this, b1);
                if (bLabels.lFalse.equals(FALL)) {
                    node.jjtGetChild(1).jjtAccept(this, bLabels);
                    label(b1.lFalse);
                } else {
                    node.jjtGetChild(1).jjtAccept(this, bLabels);
                }
            } else if (ops.size() > 0 && ops.get(0).equals("||")) {
                String b1True = bLabels.lTrue.equals(FALL) ? newLabel() : bLabels.lTrue;
                BoolLabel b1 = new BoolLabel(b1True, FALL);
                node.jjtGetChild(0).jjtAccept(this, b1);
                if (bLabels.lTrue.equals(FALL)) {
                    node.jjtGetChild(1).jjtAccept(this, bLabels);
                    label(b1True);
                } else {
                    node.jjtGetChild(1).jjtAccept(this, bLabels);
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
                } else if (!FALL.equals(bLabels.lTrue) && FALL.equals(bLabels.lFalse)) {
                    gen("if " + e1 + " " + op + " " + e2 + " goto " + bLabels.lTrue);
                } else if (FALL.equals(bLabels.lTrue) && !FALL.equals(bLabels.lFalse)) {
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
    public Object visit(ASTGenValue node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
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
            } else if (!FALL.equals(bLabels.lTrue) && FALL.equals(bLabels.lFalse)) {
                gen("if " + id + " == 1 goto " + bLabels.lTrue);
            } else if (FALL.equals(bLabels.lTrue) && !FALL.equals(bLabels.lFalse)) {
                gen("ifFalse " + id + " == 1 goto " + bLabels.lFalse);
            }
        } else {
            return node.getValue();
        }
        return null;
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        return Integer.toString(node.getValue());
    }

    public enum VarType { BOOL, INT }

    public static class BoolLabel {
        public String lTrue;
        public String lFalse;
        public BoolLabel(String lTrue, String lFalse) {
            this.lTrue = lTrue;
            this.lFalse = lFalse;
        }
        public BoolLabel swapped() {
            return new BoolLabel(lFalse, lTrue);
        }
    }
}
