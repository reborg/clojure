package clojure.lang.compiler.expr;

import clojure.asm.commons.GeneratorAdapter;
import clojure.lang.*;
import clojure.lang.analyzer.Analyzer;
import clojure.lang.compiler.C;

public class AssignExpr implements Expr{
public final AssignableExpr target;
public final Expr val;

public AssignExpr(AssignableExpr target, Expr val){
    this.target = target;
    this.val = val;
}

public Object eval() {
    return target.evalAssign(val);
}

public void emit(C context, ObjExpr objx, GeneratorAdapter gen){
    target.emitAssign(context, objx, gen, val);
}

public boolean hasJavaClass() {
    return val.hasJavaClass();
}

public Class getJavaClass() {
    return val.getJavaClass();
}

public static class Parser implements IParser {
    public Expr parse(C context, Object frm) {
        ISeq form = (ISeq) frm;
        if(RT.length(form) != 3)
            throw new IllegalArgumentException("Malformed assignment, expecting (set! target val)");
        Expr target = Analyzer.analyze(C.EXPRESSION, RT.second(form));
        if(!(target instanceof AssignableExpr))
            throw new IllegalArgumentException("Invalid assignment target");
        return new AssignExpr((AssignableExpr) target, Analyzer.analyze(C.EXPRESSION, RT.third(form)));
    }
}
}
