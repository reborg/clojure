package clojure.lang.compiler.expr;

import clojure.asm.commons.GeneratorAdapter;
import clojure.lang.Compiler;
import clojure.lang.RT;
import clojure.lang.compiler.C;

public class MonitorEnterExpr extends UntypedExpr {
    final Expr target;

    public MonitorEnterExpr(Expr target) {
        this.target = target;
    }

    public Object eval() {
        throw new UnsupportedOperationException("Can't eval monitor-enter");
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        target.emit(C.EXPRESSION, objx, gen);
        gen.monitorEnter();
        Compiler.NIL_EXPR.emit(context, objx, gen);
    }

    public static class Parser implements IParser {
        public Expr parse(C context, Object form) {
            return new MonitorEnterExpr(Compiler.analyze(C.EXPRESSION, RT.second(form)));
        }
    }
}
