package clojure.lang.compiler.expr;

import clojure.asm.Type;
import clojure.asm.commons.GeneratorAdapter;
import clojure.asm.commons.Method;
import clojure.lang.Compiler;
import clojure.lang.IObj;
import clojure.lang.IPersistentMap;
import clojure.lang.compiler.C;

public class MetaExpr implements Expr {
    public final Expr expr;
    public final Expr meta;
    final static Type IOBJ_TYPE = Type.getType(IObj.class);
    final static Method withMetaMethod = Method.getMethod("clojure.lang.IObj withMeta(clojure.lang.IPersistentMap)");


    public MetaExpr(Expr expr, Expr meta) {
        this.expr = expr;
        this.meta = meta;
    }

    public Object eval() {
        return ((IObj) expr.eval()).withMeta((IPersistentMap) meta.eval());
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        expr.emit(C.EXPRESSION, objx, gen);
        gen.checkCast(IOBJ_TYPE);
        meta.emit(C.EXPRESSION, objx, gen);
        gen.checkCast(Compiler.IPERSISTENTMAP_TYPE);
        gen.invokeInterface(IOBJ_TYPE, withMetaMethod);
        if (context == C.STATEMENT) {
            gen.pop();
        }
    }

    public boolean hasJavaClass() {
        return expr.hasJavaClass();
    }

    public Class getJavaClass() {
        return expr.getJavaClass();
    }
}
