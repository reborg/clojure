package clojure.lang.compiler.expr;

import clojure.asm.commons.GeneratorAdapter;
import clojure.asm.commons.Method;
import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.compiler.C;

public class VarExpr implements Expr, AssignableExpr {
public final Var var;
public final Object tag;
final static Method getMethod = Method.getMethod("Object get()");
final static Method setMethod = Method.getMethod("Object set(Object)");

public VarExpr(Var var, Symbol tag){
    this.var = var;
    this.tag = tag != null ? tag : var.getTag();
}

public Object eval() {
    return var.deref();
}

public void emit(C context, ObjExpr objx, GeneratorAdapter gen){
    objx.emitVarValue(gen,var);
    if(context == C.STATEMENT)
        {
        gen.pop();
        }
}

public boolean hasJavaClass(){
    return tag != null;
}

public Class getJavaClass() {
    return HostExpr.tagToClass(tag);
}

public Object evalAssign(Expr val) {
    return var.set(val.eval());
}

public void emitAssign(C context, ObjExpr objx, GeneratorAdapter gen,
                       Expr val){
    objx.emitVar(gen, var);
    val.emit(C.EXPRESSION, objx, gen);
    gen.invokeVirtual(Compiler.VAR_TYPE, setMethod);
    if(context == C.STATEMENT)
        gen.pop();
}
}
