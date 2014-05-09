package clojure.lang.compiler.expr;

import clojure.asm.commons.GeneratorAdapter;
import clojure.lang.compiler.C;

public class StringExpr extends LiteralExpr {
    public final String str;

    public StringExpr(String str) {
        this.str = str;
    }

    public Object val() {
        return str;
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        if (context != C.STATEMENT)
            gen.push(str);
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return String.class;
    }
}
