package clojure.lang.compiler.expr;

import clojure.asm.commons.GeneratorAdapter;
import clojure.lang.Compiler;
import clojure.lang.RT;
import clojure.lang.compiler.C;

public class BooleanExpr extends LiteralExpr {
    public final boolean val;


    public BooleanExpr(boolean val) {
        this.val = val;
    }

    public Object val() {
        return val ? RT.T : RT.F;
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        if (val)
            gen.getStatic(Compiler.BOOLEAN_OBJECT_TYPE, "TRUE", Compiler.BOOLEAN_OBJECT_TYPE);
        else
            gen.getStatic(Compiler.BOOLEAN_OBJECT_TYPE, "FALSE", Compiler.BOOLEAN_OBJECT_TYPE);
        if (context == C.STATEMENT) {
            gen.pop();
        }
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return Boolean.class;
    }
}
