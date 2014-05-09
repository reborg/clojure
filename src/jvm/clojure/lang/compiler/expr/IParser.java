package clojure.lang.compiler.expr;

import clojure.lang.compiler.C;

public interface IParser {
    Expr parse(C context, Object form);
}
