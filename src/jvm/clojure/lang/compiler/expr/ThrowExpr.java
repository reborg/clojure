package clojure.lang.compiler.expr;

import clojure.asm.commons.GeneratorAdapter;
import clojure.lang.Compiler;
import clojure.lang.PersistentVector;
import clojure.lang.RT;
import clojure.lang.Util;
import clojure.lang.analyzer.Analyzer;
import clojure.lang.compiler.C;

public class ThrowExpr extends UntypedExpr {
    public final Expr excExpr;

    public ThrowExpr(Expr excExpr) {
        this.excExpr = excExpr;
    }


    public Object eval() {
        throw Util.runtimeException("Can't eval throw");
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        excExpr.emit(C.EXPRESSION, objx, gen);
        gen.checkCast(Compiler.THROWABLE_TYPE);
        gen.throwException();
    }

    public static class Parser implements IParser {
        public Expr parse(C context, Object form) {
            if (context == C.EVAL)
                return Analyzer.analyze(context, RT.list(RT.list(Compiler.FNONCE, PersistentVector.EMPTY, form)));
            return new ThrowExpr(Analyzer.analyze(C.EXPRESSION, RT.second(form)));
        }
    }
}
