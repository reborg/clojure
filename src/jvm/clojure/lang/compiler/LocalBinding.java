package clojure.lang.compiler;

import clojure.lang.Compiler;
import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Util;
import clojure.lang.compiler.expr.Expr;
import clojure.lang.compiler.expr.HostExpr;
import clojure.lang.compiler.expr.MaybePrimitiveExpr;

public class LocalBinding {
    public final Symbol sym;
    public final Symbol tag;
    public Expr init;
    public final int idx;
    public final String name;
    public final boolean isArg;
    public final PathNode clearPathRoot;
    public boolean canBeCleared = !RT.booleanCast(clojure.lang.Compiler.getCompilerOption(Compiler.disableLocalsClearingKey));
    public boolean recurMistmatch = false;

    public LocalBinding(int num, Symbol sym, Symbol tag, Expr init, boolean isArg, PathNode clearPathRoot) {
        if (Compiler.maybePrimitiveType(init) != null && tag != null)
            throw new UnsupportedOperationException("Can't type hint a local with a primitive initializer");
        this.idx = num;
        this.sym = sym;
        this.tag = tag;
        this.init = init;
        this.isArg = isArg;
        this.clearPathRoot = clearPathRoot;
        name = Compiler.munge(sym.name);
    }

    public boolean hasJavaClass() {
        if (init != null && init.hasJavaClass()
                && Util.isPrimitive(init.getJavaClass())
                && !(init instanceof MaybePrimitiveExpr))
            return false;
        return tag != null
                || (init != null && init.hasJavaClass());
    }

    public Class getJavaClass() {
        return tag != null ? HostExpr.tagToClass(tag)
                : init.getJavaClass();
    }

    public Class getPrimitiveType() {
        return Compiler.maybePrimitiveType(init);
    }
}
