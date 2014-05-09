package clojure.lang.compiler.expr;

public abstract class UntypedExpr implements Expr {

public Class getJavaClass(){
    throw new IllegalArgumentException("Has no Java class");
}

public boolean hasJavaClass(){
    return false;
}
}
