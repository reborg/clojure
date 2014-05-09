package clojure.lang.compiler;

import clojure.lang.compiler.expr.Expr;

public class BindingInit {
    public LocalBinding binding;
    public Expr init;

    public final LocalBinding binding() {
        return binding;
    }

    public final Expr init() {
        return init;
    }

    public BindingInit(LocalBinding binding, Expr init) {
        this.binding = binding;
        this.init = init;
    }
}
