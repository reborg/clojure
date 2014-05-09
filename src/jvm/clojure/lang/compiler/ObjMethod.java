package clojure.lang.compiler;

import clojure.asm.ClassVisitor;
import clojure.asm.Label;
import clojure.asm.Opcodes;
import clojure.asm.Type;
import clojure.asm.commons.GeneratorAdapter;
import clojure.asm.commons.Method;
import clojure.lang.Compiler;
import clojure.lang.*;
import clojure.lang.compiler.expr.Expr;
import clojure.lang.compiler.expr.MaybePrimitiveExpr;
import clojure.lang.compiler.expr.ObjExpr;

abstract public class ObjMethod {
    //when closures are defined inside other closures,
    //the closed over locals need to be propagated to the enclosing objx
    public final ObjMethod parent;
    //localbinding->localbinding
    public IPersistentMap locals = null;
    //num->localbinding
    public IPersistentMap indexlocals = null;
    public Expr body = null;
    public ObjExpr objx;
    public PersistentVector argLocals;
    public int maxLocal = 0;
    public int line;
    public int column;
    public PersistentHashSet localsUsedInCatchFinally = PersistentHashSet.EMPTY;
    protected IPersistentMap methodMeta;


    public final IPersistentMap locals() {
        return locals;
    }

    public final Expr body() {
        return body;
    }

    public final ObjExpr objx() {
        return objx;
    }

    public final PersistentVector argLocals() {
        return argLocals;
    }

    public final int maxLocal() {
        return maxLocal;
    }

    public final int line() {
        return line;
    }

    public final int column() {
        return column;
    }

    public ObjMethod(ObjExpr objx, ObjMethod parent) {
        this.parent = parent;
        this.objx = objx;
    }

    public static void emitBody(ObjExpr objx, GeneratorAdapter gen, Class retClass, Expr body) {
        MaybePrimitiveExpr be = (MaybePrimitiveExpr) body;
        if (Util.isPrimitive(retClass) && be.canEmitPrimitive()) {
            Class bc = Compiler.maybePrimitiveType(be);
            if (bc == retClass)
                be.emitUnboxed(C.RETURN, objx, gen);
            else if (retClass == long.class && bc == int.class) {
                be.emitUnboxed(C.RETURN, objx, gen);
                gen.visitInsn(Opcodes.I2L);
            } else if (retClass == double.class && bc == float.class) {
                be.emitUnboxed(C.RETURN, objx, gen);
                gen.visitInsn(Opcodes.F2D);
            } else if (retClass == int.class && bc == long.class) {
                be.emitUnboxed(C.RETURN, objx, gen);
                gen.invokeStatic(Compiler.RT_TYPE, Method.getMethod("int intCast(long)"));
            } else if (retClass == float.class && bc == double.class) {
                be.emitUnboxed(C.RETURN, objx, gen);
                gen.visitInsn(Opcodes.D2F);
            } else
                throw new IllegalArgumentException("Mismatched primitive return, expected: "
                        + retClass + ", had: " + be.getJavaClass());
        } else {
            body.emit(C.RETURN, objx, gen);
            if (retClass == void.class) {
                gen.pop();
            } else
                gen.unbox(Type.getType(retClass));
        }
    }

    public abstract int numParams();

    public abstract String getMethodName();

    public abstract Type getReturnType();

    public abstract Type[] getArgTypes();

    public void emit(ObjExpr fn, ClassVisitor cv) {
        Method m = new Method(getMethodName(), getReturnType(), getArgTypes());

        GeneratorAdapter gen = new GeneratorAdapter(Opcodes.ACC_PUBLIC,
                m,
                null,
                //todo don't hardwire this
                Compiler.EXCEPTION_TYPES,
                cv);
        gen.visitCode();

        Label loopLabel = gen.mark();
        gen.visitLineNumber(line, loopLabel);
        try {
            Var.pushThreadBindings(RT.map(Compiler.LOOP_LABEL, loopLabel, Compiler.METHOD, this));

            body.emit(C.RETURN, fn, gen);
            Label end = gen.mark();
            gen.visitLocalVariable("this", "Ljava/lang/Object;", null, loopLabel, end, 0);
            for (ISeq lbs = argLocals.seq(); lbs != null; lbs = lbs.next()) {
                Compiler.LocalBinding lb = (Compiler.LocalBinding) lbs.first();
                gen.visitLocalVariable(lb.name, "Ljava/lang/Object;", null, loopLabel, end, lb.idx);
            }
        } finally {
            Var.popThreadBindings();
        }

        gen.returnValue();
        //gen.visitMaxs(1, 1);
        gen.endMethod();
    }

    public void emitClearLocals(GeneratorAdapter gen) {
    }

    void emitClearLocalsOld(GeneratorAdapter gen) {
        for (int i = 0; i < argLocals.count(); i++) {
            Compiler.LocalBinding lb = (Compiler.LocalBinding) argLocals.nth(i);
            if (!localsUsedInCatchFinally.contains(lb.idx) && lb.getPrimitiveType() == null) {
                gen.visitInsn(Opcodes.ACONST_NULL);
                gen.storeArg(lb.idx - 1);
            }

        }
//		for(int i = 1; i < numParams() + 1; i++)
//			{
//			if(!localsUsedInCatchFinally.contains(i))
//				{
//				gen.visitInsn(Opcodes.ACONST_NULL);
//				gen.visitVarInsn(OBJECT_TYPE.getOpcode(Opcodes.ISTORE), i);
//				}
//			}
        for (int i = numParams() + 1; i < maxLocal + 1; i++) {
            if (!localsUsedInCatchFinally.contains(i)) {
                Compiler.LocalBinding b = (Compiler.LocalBinding) RT.get(indexlocals, i);
                if (b == null || Compiler.maybePrimitiveType(b.init) == null) {
                    gen.visitInsn(Opcodes.ACONST_NULL);
                    gen.visitVarInsn(Compiler.OBJECT_TYPE.getOpcode(Opcodes.ISTORE), i);
                }
            }
        }
    }
}
