package clojure.lang.compiler.expr;

import clojure.asm.commons.GeneratorAdapter;
import clojure.lang.compiler.C;

public interface MaybePrimitiveExpr extends Expr{
public boolean canEmitPrimitive();
public void emitUnboxed(C context, ObjExpr objx, GeneratorAdapter gen);
}
