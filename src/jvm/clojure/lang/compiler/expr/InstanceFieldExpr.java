package clojure.lang.compiler.expr;

import clojure.asm.Type;
import clojure.asm.commons.GeneratorAdapter;
import clojure.asm.commons.Method;
import clojure.lang.Compiler;
import clojure.lang.*;
import clojure.lang.compiler.C;

public class InstanceFieldExpr extends FieldExpr implements AssignableExpr {
    public final Expr target;
    public final Class targetClass;
    public final java.lang.reflect.Field field;
    public final String fieldName;
    public final int line;
    public final int column;
    public final Symbol tag;
    public final boolean requireField;
    final static Method invokeNoArgInstanceMember = Method.getMethod("Object invokeNoArgInstanceMember(Object,String,boolean)");
    final static Method setInstanceFieldMethod = Method.getMethod("Object setInstanceField(Object,String,Object)");


    public InstanceFieldExpr(int line, int column, Expr target, String fieldName, Symbol tag, boolean requireField) {
        this.target = target;
        this.targetClass = target.hasJavaClass() ? target.getJavaClass() : null;
        this.field = targetClass != null ? Reflector.getField(targetClass, fieldName, false) : null;
        this.fieldName = fieldName;
        this.line = line;
        this.column = column;
        this.tag = tag;
        this.requireField = requireField;
        if (field == null && RT.booleanCast(RT.WARN_ON_REFLECTION.deref())) {
            if (targetClass == null) {
                RT.errPrintWriter()
                        .format("Reflection warning, %s:%d:%d - reference to field %s can't be resolved.\n",
                                Compiler.SOURCE_PATH.deref(), line, column, fieldName);
            } else {
                RT.errPrintWriter()
                        .format("Reflection warning, %s:%d:%d - reference to field %s on %s can't be resolved.\n",
                                Compiler.SOURCE_PATH.deref(), line, column, fieldName, targetClass.getName());
            }
        }
    }

    public Object eval() {
        return Reflector.invokeNoArgInstanceMember(target.eval(), fieldName, requireField);
    }

    public boolean canEmitPrimitive() {
        return targetClass != null && field != null &&
                Util.isPrimitive(field.getType());
    }

    public void emitUnboxed(C context, ObjExpr objx, GeneratorAdapter gen) {
        gen.visitLineNumber(line, gen.mark());
        if (targetClass != null && field != null) {
            target.emit(C.EXPRESSION, objx, gen);
            gen.checkCast(Compiler.getType(targetClass));
            gen.getField(Compiler.getType(targetClass), fieldName, Type.getType(field.getType()));
        } else
            throw new UnsupportedOperationException("Unboxed emit of unknown member");
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        gen.visitLineNumber(line, gen.mark());
        if (targetClass != null && field != null) {
            target.emit(C.EXPRESSION, objx, gen);
            gen.checkCast(Compiler.getType(targetClass));
            gen.getField(Compiler.getType(targetClass), fieldName, Type.getType(field.getType()));
            //if(context != C.STATEMENT)
            HostExpr.emitBoxReturn(objx, gen, field.getType());
            if (context == C.STATEMENT) {
                gen.pop();
            }
        } else {
            target.emit(C.EXPRESSION, objx, gen);
            gen.push(fieldName);
            gen.push(requireField);
            gen.invokeStatic(Compiler.REFLECTOR_TYPE, invokeNoArgInstanceMember);
            if (context == C.STATEMENT)
                gen.pop();
        }
    }

    public boolean hasJavaClass() {
        return field != null || tag != null;
    }

    public Class getJavaClass() {
        return tag != null ? HostExpr.tagToClass(tag) : field.getType();
    }

    public Object evalAssign(Expr val) {
        return Reflector.setInstanceField(target.eval(), fieldName, val.eval());
    }

    public void emitAssign(C context, ObjExpr objx, GeneratorAdapter gen,
                           Expr val) {
        gen.visitLineNumber(line, gen.mark());
        if (targetClass != null && field != null) {
            target.emit(C.EXPRESSION, objx, gen);
            gen.checkCast(Type.getType(targetClass));
            val.emit(C.EXPRESSION, objx, gen);
            gen.dupX1();
            HostExpr.emitUnboxArg(objx, gen, field.getType());
            gen.putField(Type.getType(targetClass), fieldName, Type.getType(field.getType()));
        } else {
            target.emit(C.EXPRESSION, objx, gen);
            gen.push(fieldName);
            val.emit(C.EXPRESSION, objx, gen);
            gen.invokeStatic(Compiler.REFLECTOR_TYPE, setInstanceFieldMethod);
        }
        if (context == C.STATEMENT)
            gen.pop();
    }
}
