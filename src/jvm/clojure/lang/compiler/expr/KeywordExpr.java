package clojure.lang.compiler.expr;

import clojure.asm.commons.GeneratorAdapter;
import clojure.lang.*;
import clojure.lang.compiler.C;

public class KeywordExpr extends LiteralExpr {
public final Keyword k;

public KeywordExpr(Keyword k){
    this.k = k;
}

public Object val(){
    return k;
}

public Object eval() {
    return k;
}

public void emit(C context, ObjExpr objx, GeneratorAdapter gen){
    objx.emitKeyword(gen, k);
    if(context == C.STATEMENT)
        gen.pop();

}

public boolean hasJavaClass(){
    return true;
}

public Class getJavaClass() {
    return Keyword.class;
}
}
