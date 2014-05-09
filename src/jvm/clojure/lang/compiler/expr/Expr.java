package clojure.lang.compiler.expr;

import clojure.asm.commons.GeneratorAdapter;
import clojure.lang.compiler.C;

public interface Expr {
	Object eval() ;

	void emit(C context, ObjExpr objx, GeneratorAdapter gen);

	boolean hasJavaClass() ;

	Class getJavaClass() ;
}
