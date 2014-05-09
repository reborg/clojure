package clojure.lang.compiler.expr;

import clojure.asm.commons.GeneratorAdapter;
import clojure.lang.compiler.C;

public interface AssignableExpr {
    Object evalAssign(Expr val);

    void emitAssign(C context, ObjExpr objx, GeneratorAdapter gen, Expr val);
}
