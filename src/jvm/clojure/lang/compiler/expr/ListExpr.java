package clojure.lang.compiler.expr;

import clojure.asm.commons.GeneratorAdapter;
import clojure.asm.commons.Method;
import clojure.lang.Compiler;
import clojure.lang.IPersistentList;
import clojure.lang.IPersistentVector;
import clojure.lang.PersistentVector;
import clojure.lang.compiler.C;

public class ListExpr implements Expr {
    public final IPersistentVector args;
    final static Method arrayToListMethod = Method.getMethod("clojure.lang.ISeq arrayToList(Object[])");


    public ListExpr(IPersistentVector args) {
        this.args = args;
    }

    public Object eval() {
        IPersistentVector ret = PersistentVector.EMPTY;
        for (int i = 0; i < args.count(); i++)
            ret = (IPersistentVector) ret.cons(((Expr) args.nth(i)).eval());
        return ret.seq();
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        MethodExpr.emitArgsAsArray(args, objx, gen);
        gen.invokeStatic(Compiler.RT_TYPE, arrayToListMethod);
        if (context == C.STATEMENT)
            gen.pop();
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return IPersistentList.class;
    }

}
