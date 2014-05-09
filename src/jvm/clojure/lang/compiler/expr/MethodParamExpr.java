package clojure.lang.compiler.expr;

import clojure.asm.commons.GeneratorAdapter;
import clojure.lang.*;
import clojure.lang.compiler.C;

public class MethodParamExpr implements Expr, MaybePrimitiveExpr {
    final Class c;

    public MethodParamExpr(Class c) {
        this.c = c;
    }

    public Object eval() {
        throw Util.runtimeException("Can't eval");
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        throw Util.runtimeException("Can't emit");
    }

    public boolean hasJavaClass() {
        return c != null;
    }

    public Class getJavaClass() {
        return c;
    }

    public boolean canEmitPrimitive() {
        return Util.isPrimitive(c);
    }

    public void emitUnboxed(C context, ObjExpr objx, GeneratorAdapter gen) {
        throw Util.runtimeException("Can't emit");
    }
}
