package clojure.lang.compiler.expr;

public abstract class LiteralExpr implements Expr {
    public abstract Object val();

    public Object eval() {
        return val();
    }
}
