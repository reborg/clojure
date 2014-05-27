package clojure.lang.compiler.expr;

import clojure.asm.commons.GeneratorAdapter;
import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.analyzer.Analyzer;
import clojure.lang.compiler.C;

import java.lang.reflect.Modifier;

public class ConstantExpr extends LiteralExpr {
    //stuff quoted vals in classloader at compile time, pull out at runtime
//this won't work for static compilation...
    public final Object v;
    public final int id;

    public ConstantExpr(Object v) {
        this.v = v;
        this.id = Analyzer.registerConstant(v);
//		this.id = RT.nextID();
//		DynamicClassLoader loader = (DynamicClassLoader) LOADER.get();
//		loader.registerQuotedVal(id, v);
    }

    public Object val() {
        return v;
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        objx.emitConstant(gen, id);

        if (context == C.STATEMENT) {
            gen.pop();
//			gen.loadThis();
//			gen.invokeVirtual(OBJECT_TYPE, getClassMethod);
//			gen.invokeVirtual(CLASS_TYPE, getClassLoaderMethod);
//			gen.checkCast(DYNAMIC_CLASSLOADER_TYPE);
//			gen.push(id);
//			gen.invokeVirtual(DYNAMIC_CLASSLOADER_TYPE, getQuotedValMethod);
        }
    }

    public boolean hasJavaClass() {
        return Modifier.isPublic(v.getClass().getModifiers());
        //return false;
    }

    public Class getJavaClass() {
        if (v instanceof APersistentMap)
            return APersistentMap.class;
        else if (v instanceof APersistentSet)
            return APersistentSet.class;
        else if (v instanceof APersistentVector)
            return APersistentVector.class;
        else
            return v.getClass();
        //throw new IllegalArgumentException("Has no Java class");
    }

    public static class Parser implements IParser {
        public Expr parse(C context, Object form) {
            Object v = RT.second(form);

            if (v == null)
                return Compiler.NIL_EXPR;
            else if (v == Boolean.TRUE)
                return Compiler.TRUE_EXPR;
            else if (v == Boolean.FALSE)
                return Compiler.FALSE_EXPR;
            if (v instanceof Number)
                return NumberExpr.parse((Number) v);
            else if (v instanceof String)
                return new StringExpr((String) v);
            else if (v instanceof IPersistentCollection && ((IPersistentCollection) v).count() == 0)
                return new EmptyExpr(v);
            else
                return new ConstantExpr(v);
        }
    }
}
