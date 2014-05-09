package clojure.lang.compiler.expr;

import clojure.asm.commons.GeneratorAdapter;
import clojure.lang.*;
import clojure.lang.compiler.C;

public class UnresolvedVarExpr implements Expr{
public final Symbol symbol;

public UnresolvedVarExpr(Symbol symbol){
    this.symbol = symbol;
}

public boolean hasJavaClass(){
    return false;
}

public Class getJavaClass() {
    throw new IllegalArgumentException(
            "UnresolvedVarExpr has no Java class");
}

public void emit(C context, ObjExpr objx, GeneratorAdapter gen){
}

public Object eval() {
    throw new IllegalArgumentException(
            "UnresolvedVarExpr cannot be evalled");
}
}
