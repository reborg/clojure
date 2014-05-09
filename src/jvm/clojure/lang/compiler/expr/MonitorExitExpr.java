package clojure.lang.compiler.expr;

import clojure.asm.commons.GeneratorAdapter;
import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.compiler.C;

public class MonitorExitExpr extends UntypedExpr {
    final Expr target;

    public MonitorExitExpr(Expr target) {
        this.target = target;
    }

    public Object eval() {
        throw new UnsupportedOperationException("Can't eval monitor-exit");
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        target.emit(C.EXPRESSION, objx, gen);
        gen.monitorExit();
        Compiler.NIL_EXPR.emit(context, objx, gen);
    }

    public static class Parser implements IParser {
        public Expr parse(C context, Object form) {
            return new MonitorExitExpr(Compiler.analyze(C.EXPRESSION, RT.second(form)));
        }
    }

}
