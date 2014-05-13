package clojure.lang.compiler;

import clojure.asm.*;
import clojure.asm.commons.GeneratorAdapter;
import clojure.asm.commons.Method;
import clojure.lang.Compiler;
import clojure.lang.ISeq;
import clojure.lang.RT;
import clojure.lang.compiler.expr.LocalBindingExpr;
import clojure.lang.compiler.expr.ObjExpr;

import java.io.IOException;

public class ASMGen {

    public static void invoke(ObjExpr objExpr, String superName, String... interfaceNames) throws IOException {
        //create bytecode for a class
        //with name current_ns.defname[$letname]+
        //anonymous fns get names fn__id
        //derived from AFn/RestFn
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
//		ClassWriter cw = new ClassWriter(0);
        ClassVisitor cv = cw;
//		ClassVisitor cv = new TraceClassVisitor(new CheckClassAdapter(cw), new PrintWriter(System.out));
        //ClassVisitor cv = new TraceClassVisitor(cw, new PrintWriter(System.out));
        cv.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER + Opcodes.ACC_FINAL, objExpr.internalName, null, superName, interfaceNames);
//		         superName != null ? superName :
//		         (isVariadic() ? "clojure/lang/RestFn" : "clojure/lang/AFunction"), null);

        createSourceMap(cv);

        if (objExpr.classMeta != null && Compiler.ADD_ANNOTATIONS.isBound())
            Compiler.ADD_ANNOTATIONS.invoke(cv, objExpr.classMeta);

        staticFields(objExpr, cv);

//		for(int i=0;i<varCallsites.count();i++)
//			{
//			cv.visitField(ACC_PRIVATE + ACC_STATIC + ACC_FINAL
//					, varCallsiteName(i), IFN_TYPE.getDescriptor(), null, null);
//			}

        staticInit(objExpr, cv);

        instanceFields(objExpr, cv);
        int supportsMeta = constructor(objExpr, cv, superName);


        alternativeDropConstructor(objExpr, cv);

        if (objExpr.supportsMeta()) {
            Type[] ctorTypes = notMetaConstructor(objExpr, cv);
            methodMeta(objExpr, cv);
            methodWithMeta(objExpr, cv, supportsMeta, ctorTypes);
        }

        objExpr.emitStatics(cv);
        objExpr.emitMethods(cv);

        if (objExpr.keywordCallsites.count() > 0) {
            methodSwapThunk(objExpr, cv);
        }

        //end of class
        cv.visitEnd();

        objExpr.bytecode = cw.toByteArray();
        if (RT.booleanCast(Compiler.COMPILE_FILES.deref()))
            Compiler.writeClassFile(objExpr.internalName, objExpr.bytecode);
//		else
//			getCompiledClass();
    }

    private static void createSourceMap(ClassVisitor cv) {
        String source = (String) Compiler.SOURCE.deref();
        int lineBefore = (Integer) Compiler.LINE_BEFORE.deref();
        int lineAfter = (Integer) Compiler.LINE_AFTER.deref() + 1;
        int columnBefore = (Integer) Compiler.COLUMN_BEFORE.deref();
        int columnAfter = (Integer) Compiler.COLUMN_AFTER.deref() + 1;

        if (source != null && Compiler.SOURCE_PATH.deref() != null) {
            //cv.visitSource(source, null);
            String smap = "SMAP\n" +
                    ((source.lastIndexOf('.') > 0) ?
                            source.substring(0, source.lastIndexOf('.'))
                            : source)
                    //                      : simpleName)
                    + ".java\n" +
                    "Clojure\n" +
                    "*S Clojure\n" +
                    "*F\n" +
                    "+ 1 " + source + "\n" +
                    (String) Compiler.SOURCE_PATH.deref() + "\n" +
                    "*L\n" +
                    String.format("%d#1,%d:%d\n", lineBefore, lineAfter - lineBefore, lineBefore) +
                    "*E";
            cv.visitSource(source, smap);
        }
    }

    private static void methodSwapThunk(ObjExpr objExpr, ClassVisitor cv) {
        Method meth = Method.getMethod("void swapThunk(int,clojure.lang.ILookupThunk)");

        GeneratorAdapter gen = new GeneratorAdapter(Opcodes.ACC_PUBLIC,
                meth,
                null,
                null,
                cv);
        gen.visitCode();
        Label endLabel = gen.newLabel();

        Label[] labels = new Label[objExpr.keywordCallsites.count()];
        for (int i = 0; i < objExpr.keywordCallsites.count(); i++) {
            labels[i] = gen.newLabel();
        }
        gen.loadArg(0);
        gen.visitTableSwitchInsn(0, objExpr.keywordCallsites.count() - 1, endLabel, labels);

        for (int i = 0; i < objExpr.keywordCallsites.count(); i++) {
            gen.mark(labels[i]);
//				gen.loadThis();
            gen.loadArg(1);
            gen.putStatic(objExpr.objtype, objExpr.thunkNameStatic(i), ObjExpr.ILOOKUP_THUNK_TYPE);
            gen.goTo(endLabel);
        }

        gen.mark(endLabel);

        gen.returnValue();
        gen.endMethod();
    }

    private static void methodWithMeta(ObjExpr objExpr, ClassVisitor cv, int supportsMeta, Type[] ctorTypes) {
        //withMeta()
        Method meth = Method.getMethod("clojure.lang.IObj withMeta(clojure.lang.IPersistentMap)");

        GeneratorAdapter gen2 = new GeneratorAdapter(Opcodes.ACC_PUBLIC,
                meth,
                null,
                null,
                cv);
        gen2.visitCode();
        gen2.newInstance(objExpr.objtype);
        gen2.dup();
        gen2.loadArg(0);

        for (ISeq s = RT.keys(objExpr.closes); s != null; s = s.next(), ++supportsMeta) {
            LocalBinding lb = (LocalBinding) s.first();
            gen2.loadThis();
            Class primc = lb.getPrimitiveType();
            if (primc != null) {
                gen2.getField(objExpr.objtype, lb.name, Type.getType(primc));
            } else {
                gen2.getField(objExpr.objtype, lb.name, Compiler.OBJECT_TYPE);
            }
        }

        gen2.invokeConstructor(objExpr.objtype, new Method("<init>", Type.VOID_TYPE, ctorTypes));
        gen2.returnValue();
        gen2.endMethod();
    }

    private static void methodMeta(ObjExpr objExpr, ClassVisitor cv) {
        //meta()
        Method meth = Method.getMethod("clojure.lang.IPersistentMap meta()");

        GeneratorAdapter gen = new GeneratorAdapter(Opcodes.ACC_PUBLIC,
                meth,
                null,
                null,
                cv);
        gen.visitCode();
        gen.loadThis();
        gen.getField(objExpr.objtype, "__meta", Compiler.IPERSISTENTMAP_TYPE);

        gen.returnValue();
        gen.endMethod();
    }

    private static Type[] notMetaConstructor(ObjExpr objExpr, ClassVisitor cv) {
        //ctor that takes closed-overs but not meta
        Type[] ctorTypes = objExpr.ctorTypes();
        Type[] noMetaCtorTypes = new Type[ctorTypes.length - 1];
        for (int i = 1; i < ctorTypes.length; i++)
            noMetaCtorTypes[i - 1] = ctorTypes[i];
        Method alt = new Method("<init>", Type.VOID_TYPE, noMetaCtorTypes);
        GeneratorAdapter ctorgen3 = new GeneratorAdapter(Opcodes.ACC_PUBLIC,
                alt,
                null,
                null,
                cv);
        ctorgen3.visitCode();
        ctorgen3.loadThis();
        ctorgen3.visitInsn(Opcodes.ACONST_NULL);    //null meta
        ctorgen3.loadArgs();
        ctorgen3.invokeConstructor(objExpr.objtype, new Method("<init>", Type.VOID_TYPE, ctorTypes));

        ctorgen3.returnValue();
        ctorgen3.endMethod();
        return ctorTypes;
    }

    private static void alternativeDropConstructor(ObjExpr objExpr, ClassVisitor cv) {
        if (objExpr.altCtorDrops > 0) {
            //ctor that takes closed-overs and inits base + fields
            Type[] ctorTypes = objExpr.ctorTypes();
            Type[] altCtorTypes = new Type[ctorTypes.length - objExpr.altCtorDrops];
            for (int i = 0; i < altCtorTypes.length; i++)
                altCtorTypes[i] = ctorTypes[i];
            Method alt = new Method("<init>", Type.VOID_TYPE, altCtorTypes);
            GeneratorAdapter ctorgen2 = new GeneratorAdapter(Opcodes.ACC_PUBLIC,
                    alt,
                    null,
                    null,
                    cv);
            ctorgen2.visitCode();
            ctorgen2.loadThis();
            ctorgen2.loadArgs();
            for (int i = 0; i < objExpr.altCtorDrops; i++)
                ctorgen2.visitInsn(Opcodes.ACONST_NULL);

            ctorgen2.invokeConstructor(objExpr.objtype, new Method("<init>", Type.VOID_TYPE, ctorTypes));

            ctorgen2.returnValue();
            ctorgen2.endMethod();
        }
    }

    private static int constructor(ObjExpr objExpr, ClassVisitor cv, String superName) {
        //ctor that takes closed-overs and inits base + fields
        Method init = new Method("<init>", Type.VOID_TYPE, objExpr.ctorTypes());
        GeneratorAdapter ctorgen = new GeneratorAdapter(Opcodes.ACC_PUBLIC,
                init,
                null,
                null,
                cv);
        Label start = ctorgen.newLabel();
        Label end = ctorgen.newLabel();
        ctorgen.visitCode();
        ctorgen.visitLineNumber(objExpr.line, ctorgen.mark());
        ctorgen.visitLabel(start);
        ctorgen.loadThis();
//		if(superName != null)
        ctorgen.invokeConstructor(Type.getObjectType(superName), ObjExpr.voidctor);
//		else if(isVariadic()) //RestFn ctor takes reqArity arg
//			{
//			ctorgen.push(variadicMethod.reqParms.count());
//			ctorgen.invokeConstructor(restFnType, restfnctor);
//			}
//		else
//			ctorgen.invokeConstructor(aFnType, voidctor);

//		if(vars.count() > 0)
//			{
//			ctorgen.loadThis();
//			ctorgen.getStatic(VAR_TYPE,"rev",Type.INT_TYPE);
//			ctorgen.push(-1);
//			ctorgen.visitInsn(Opcodes.IADD);
//			ctorgen.putField(objtype, "__varrev__", Type.INT_TYPE);
//			}

        if (objExpr.supportsMeta()) {
            ctorgen.loadThis();
            ctorgen.visitVarInsn(Compiler.IPERSISTENTMAP_TYPE.getOpcode(Opcodes.ILOAD), 1);
            ctorgen.putField(objExpr.objtype, "__meta", Compiler.IPERSISTENTMAP_TYPE);
        }

        int a = objExpr.supportsMeta() ? 2 : 1;
        for (ISeq s = RT.keys(objExpr.closes); s != null; s = s.next(), ++a) {
            LocalBinding lb = (LocalBinding) s.first();
            ctorgen.loadThis();
            Class primc = lb.getPrimitiveType();
            if (primc != null) {
                ctorgen.visitVarInsn(Type.getType(primc).getOpcode(Opcodes.ILOAD), a);
                ctorgen.putField(objExpr.objtype, lb.name, Type.getType(primc));
                if (primc == Long.TYPE || primc == Double.TYPE)
                    ++a;
            } else {
                ctorgen.visitVarInsn(Compiler.OBJECT_TYPE.getOpcode(Opcodes.ILOAD), a);
                ctorgen.putField(objExpr.objtype, lb.name, Compiler.OBJECT_TYPE);
            }
            objExpr.closesExprs = objExpr.closesExprs.cons(new LocalBindingExpr(lb, null));
        }


        ctorgen.visitLabel(end);

        ctorgen.returnValue();

        ctorgen.endMethod();
        return a;
    }

    private static void instanceFields(ObjExpr objExpr, ClassVisitor cv) {

        if (objExpr.supportsMeta()) {
            cv.visitField(Opcodes.ACC_FINAL, "__meta", Compiler.IPERSISTENTMAP_TYPE.getDescriptor(), null, null);
        }

        //instance fields for closed-overs
        for (ISeq s = RT.keys(objExpr.closes); s != null; s = s.next()) {
            LocalBinding lb = (LocalBinding) s.first();
            if (objExpr.isDeftype()) {
                int access = objExpr.isVolatile(lb) ? Opcodes.ACC_VOLATILE :
                        objExpr.isMutable(lb) ? 0 :
                                (Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL);
                FieldVisitor fv;
                if (lb.getPrimitiveType() != null)
                    fv = cv.visitField(access
                            , lb.name, Type.getType(lb.getPrimitiveType()).getDescriptor(),
                            null, null);
                else
                    //todo - when closed-overs are fields, use more specific types here and in ctor and emitLocal?
                    fv = cv.visitField(access
                            , lb.name, Compiler.OBJECT_TYPE.getDescriptor(), null, null);
                Compiler.addAnnotation(fv, RT.meta(lb.sym));
            } else {
                //todo - only enable this non-private+writability for letfns where we need it
                if (lb.getPrimitiveType() != null)
                    cv.visitField(0 + (objExpr.isVolatile(lb) ? Opcodes.ACC_VOLATILE : 0)
                            , lb.name, Type.getType(lb.getPrimitiveType()).getDescriptor(),
                            null, null);
                else
                    cv.visitField(0 //+ (oneTimeUse ? 0 : ACC_FINAL)
                            , lb.name, Compiler.OBJECT_TYPE.getDescriptor(), null, null);
            }
        }

        //static fields for callsites and thunks
        for (int i = 0; i < objExpr.protocolCallsites.count(); i++) {
            cv.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, objExpr.cachedClassName(i), Compiler.CLASS_TYPE.getDescriptor(), null, null);
        }

    }

    private static void staticInit(ObjExpr objExpr, ClassVisitor cv) {
        //static init for constants, keywords and vars
        GeneratorAdapter clinitgen = new GeneratorAdapter(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
                Method.getMethod("void <clinit> ()"),
                null,
                null,
                cv);
        clinitgen.visitCode();
        clinitgen.visitLineNumber(objExpr.line, clinitgen.mark());

        if (objExpr.constants.count() > 0) {
            objExpr.emitConstants(clinitgen);
        }

        if (objExpr.keywordCallsites.count() > 0)
            objExpr.emitKeywordCallsites(clinitgen);

/*
for(int i=0;i<varCallsites.count();i++)
    {
    Label skipLabel = clinitgen.newLabel();
    Label endLabel = clinitgen.newLabel();
    Var var = (Var) varCallsites.nth(i);
    clinitgen.push(var.ns.name.toString());
    clinitgen.push(var.sym.toString());
    clinitgen.invokeStatic(RT_TYPE, Method.getMethod("clojure.lang.Var var(String,String)"));
    clinitgen.dup();
    clinitgen.invokeVirtual(VAR_TYPE,Method.getMethod("boolean hasRoot()"));
    clinitgen.ifZCmp(GeneratorAdapter.EQ,skipLabel);

    clinitgen.invokeVirtual(VAR_TYPE,Method.getMethod("Object getRoot()"));
    clinitgen.dup();
    clinitgen.instanceOf(AFUNCTION_TYPE);
    clinitgen.ifZCmp(GeneratorAdapter.EQ,skipLabel);
    clinitgen.checkCast(IFN_TYPE);
    clinitgen.putStatic(objtype, varCallsiteName(i), IFN_TYPE);
    clinitgen.goTo(endLabel);

    clinitgen.mark(skipLabel);
    clinitgen.pop();

    clinitgen.mark(endLabel);
    }
*/
        clinitgen.returnValue();

        clinitgen.endMethod();
    }

    private static void staticFields(ObjExpr objExpr, ClassVisitor cv) {
        //static fields for constants
        for (int i = 0; i < objExpr.constants.count(); i++) {
            cv.visitField(Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL
                    + Opcodes.ACC_STATIC, objExpr.constantName(i), objExpr.constantType(i).getDescriptor(),
                    null, null);
        }

        //static fields for lookup sites
        for (int i = 0; i < objExpr.keywordCallsites.count(); i++) {
            cv.visitField(Opcodes.ACC_FINAL
                    + Opcodes.ACC_STATIC, objExpr.siteNameStatic(i), ObjExpr.KEYWORD_LOOKUPSITE_TYPE.getDescriptor(),
                    null, null);
            cv.visitField(Opcodes.ACC_STATIC, objExpr.thunkNameStatic(i), ObjExpr.ILOOKUP_THUNK_TYPE.getDescriptor(),
                    null, null);
        }
    }
}
