package clojure.lang.compiler.expr;

import clojure.asm.Opcodes;
import clojure.asm.commons.GeneratorAdapter;
import clojure.lang.compiler.C;

public class NilExpr extends LiteralExpr {
    public Object val() {
        return null;
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        gen.visitInsn(Opcodes.ACONST_NULL);
        if (context == C.STATEMENT)
            gen.pop();
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return null;
    }
}
