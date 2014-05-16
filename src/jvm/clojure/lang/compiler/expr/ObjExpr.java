package clojure.lang.compiler.expr;

import clojure.asm.*;
import clojure.asm.commons.GeneratorAdapter;
import clojure.asm.commons.Method;
import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.compiler.ASMGen;
import clojure.lang.compiler.C;
import clojure.lang.compiler.LocalBinding;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Pattern;

public class ObjExpr implements Expr {
    static final String CONST_PREFIX = "const__";
    public String name;
    //String simpleName;
    public String internalName;
    public String thisName;
    public Type objtype;
    public final Object tag;
    //localbinding->itself
    public IPersistentMap closes = PersistentHashMap.EMPTY;
    //localbndingexprs
    public IPersistentVector closesExprs = PersistentVector.EMPTY;
    //symbols
    IPersistentSet volatiles = PersistentHashSet.EMPTY;

    //symbol->lb
    public IPersistentMap fields = null;

    //hinted fields
    public IPersistentVector hintedFields = PersistentVector.EMPTY;

    //Keyword->KeywordExpr
    public IPersistentMap keywords = PersistentHashMap.EMPTY;
    public IPersistentMap vars = PersistentHashMap.EMPTY;
    Class compiledClass;
    public int line;
    public int column;
    public PersistentVector constants;
    public int constantsID;
    public int altCtorDrops = 0;

    public IPersistentVector keywordCallsites;
    public IPersistentVector protocolCallsites;
    public IPersistentSet varCallsites;
    public boolean onceOnly = false;

    public Object src;

    public final static Method voidctor = Method.getMethod("void <init>()");
    public IPersistentMap classMeta;
    public boolean isStatic;

    public final String name() {
        return name;
    }

//	public final String simpleName(){
//		return simpleName;
//	}

    public final String internalName() {
        return internalName;
    }

    public final String thisName() {
        return thisName;
    }

    public final Type objtype() {
        return objtype;
    }

    public final IPersistentMap closes() {
        return closes;
    }

    public final IPersistentMap keywords() {
        return keywords;
    }

    public final IPersistentMap vars() {
        return vars;
    }

    public final Class compiledClass() {
        return compiledClass;
    }

    public final int line() {
        return line;
    }

    public final int column() {
        return column;
    }

    public final PersistentVector constants() {
        return constants;
    }

    public final int constantsID() {
        return constantsID;
    }

    final static Method kwintern = Method.getMethod("clojure.lang.Keyword intern(String, String)");
    final static Method symintern = Method.getMethod("clojure.lang.Symbol intern(String)");
    final static Method varintern =
            Method.getMethod("clojure.lang.Var intern(clojure.lang.Symbol, clojure.lang.Symbol)");

    final static Type DYNAMIC_CLASSLOADER_TYPE = Type.getType(DynamicClassLoader.class);
    final static Method getClassMethod = Method.getMethod("Class getClass()");
    final static Method getClassLoaderMethod = Method.getMethod("ClassLoader getClassLoader()");
    final static Method getConstantsMethod = Method.getMethod("Object[] getConstants(int)");
    final static Method readStringMethod = Method.getMethod("Object readString(String)");

    public final static Type ILOOKUP_SITE_TYPE = Type.getType(ILookupSite.class);
    public final static Type ILOOKUP_THUNK_TYPE = Type.getType(ILookupThunk.class);
    public final static Type KEYWORD_LOOKUPSITE_TYPE = Type.getType(KeywordLookupSite.class);

    private DynamicClassLoader loader;
    public byte[] bytecode;

    public ObjExpr(Object tag) {
        this.tag = tag;
    }

    public static String trimGenID(String name) {
        int i = name.lastIndexOf("__");
        return i == -1 ? name : name.substring(0, i);
    }


    public Type[] ctorTypes() {
        IPersistentVector tv = !supportsMeta() ? PersistentVector.EMPTY : RT.vector(Compiler.IPERSISTENTMAP_TYPE);
        for (ISeq s = RT.keys(closes); s != null; s = s.next()) {
            LocalBinding lb = (LocalBinding) s.first();
            if (lb.getPrimitiveType() != null)
                tv = tv.cons(Type.getType(lb.getPrimitiveType()));
            else
                tv = tv.cons(Compiler.OBJECT_TYPE);
        }
        Type[] ret = new Type[tv.count()];
        for (int i = 0; i < tv.count(); i++)
            ret[i] = (Type) tv.nth(i);
        return ret;
    }

    public void compile(String superName, String[] interfaceNames, boolean oneTimeUse) throws IOException {
        ASMGen.invoke(this, superName, interfaceNames);
    }

    public void emitKeywordCallsites(GeneratorAdapter clinitgen) {
        for (int i = 0; i < keywordCallsites.count(); i++) {
            Keyword k = (Keyword) keywordCallsites.nth(i);
            clinitgen.newInstance(KEYWORD_LOOKUPSITE_TYPE);
            clinitgen.dup();
            emitValue(k, clinitgen);
            clinitgen.invokeConstructor(KEYWORD_LOOKUPSITE_TYPE,
                    Method.getMethod("void <init>(clojure.lang.Keyword)"));
            clinitgen.dup();
            clinitgen.putStatic(objtype, siteNameStatic(i), KEYWORD_LOOKUPSITE_TYPE);
            clinitgen.putStatic(objtype, thunkNameStatic(i), ILOOKUP_THUNK_TYPE);
        }
    }

    public void emitStatics(ClassVisitor gen) {
    }

    public void emitMethods(ClassVisitor gen) {
    }

    void emitListAsObjectArray(Object value, GeneratorAdapter gen) {
        gen.push(((List) value).size());
        gen.newArray(Compiler.OBJECT_TYPE);
        int i = 0;
        for (Iterator it = ((List) value).iterator(); it.hasNext(); i++) {
            gen.dup();
            gen.push(i);
            emitValue(it.next(), gen);
            gen.arrayStore(Compiler.OBJECT_TYPE);
        }
    }

    public void emitValue(Object value, GeneratorAdapter gen) {
        boolean partial = true;
        //System.out.println(value.getClass().toString());

        if (value == null)
            gen.visitInsn(Opcodes.ACONST_NULL);
        else if (value instanceof String) {
            gen.push((String) value);
        } else if (value instanceof Boolean) {
            if (((Boolean) value).booleanValue())
                gen.getStatic(Compiler.BOOLEAN_OBJECT_TYPE, "TRUE", Compiler.BOOLEAN_OBJECT_TYPE);
            else
                gen.getStatic(Compiler.BOOLEAN_OBJECT_TYPE, "FALSE", Compiler.BOOLEAN_OBJECT_TYPE);
        } else if (value instanceof Integer) {
            gen.push(((Integer) value).intValue());
            gen.invokeStatic(Type.getType(Integer.class), Method.getMethod("Integer valueOf(int)"));
        } else if (value instanceof Long) {
            gen.push(((Long) value).longValue());
            gen.invokeStatic(Type.getType(Long.class), Method.getMethod("Long valueOf(long)"));
        } else if (value instanceof Double) {
            gen.push(((Double) value).doubleValue());
            gen.invokeStatic(Type.getType(Double.class), Method.getMethod("Double valueOf(double)"));
        } else if (value instanceof Character) {
            gen.push(((Character) value).charValue());
            gen.invokeStatic(Type.getType(Character.class), Method.getMethod("Character valueOf(char)"));
        } else if (value instanceof Class) {
            Class cc = (Class) value;
            if (cc.isPrimitive()) {
                Type bt;
                if (cc == boolean.class) bt = Type.getType(Boolean.class);
                else if (cc == byte.class) bt = Type.getType(Byte.class);
                else if (cc == char.class) bt = Type.getType(Character.class);
                else if (cc == double.class) bt = Type.getType(Double.class);
                else if (cc == float.class) bt = Type.getType(Float.class);
                else if (cc == int.class) bt = Type.getType(Integer.class);
                else if (cc == long.class) bt = Type.getType(Long.class);
                else if (cc == short.class) bt = Type.getType(Short.class);
                else throw Util.runtimeException(
                            "Can't embed unknown primitive in code: " + value);
                gen.getStatic(bt, "TYPE", Type.getType(Class.class));
            } else {
                gen.push(Compiler.destubClassName(cc.getName()));
                gen.invokeStatic(Type.getType(Class.class), Method.getMethod("Class forName(String)"));
            }
        } else if (value instanceof Symbol) {
            gen.push(((Symbol) value).ns);
            gen.push(((Symbol) value).name);
            gen.invokeStatic(Type.getType(Symbol.class),
                    Method.getMethod("clojure.lang.Symbol intern(String,String)"));
        } else if (value instanceof Keyword) {
            gen.push(((Keyword) value).sym.ns);
            gen.push(((Keyword) value).sym.name);
            gen.invokeStatic(Compiler.RT_TYPE,
                    Method.getMethod("clojure.lang.Keyword keyword(String,String)"));
        }
//						else if(value instanceof KeywordCallSite)
//								{
//								emitValue(((KeywordCallSite) value).k.sym, gen);
//								gen.invokeStatic(Type.getType(KeywordCallSite.class),
//								                 Method.getMethod("clojure.lang.KeywordCallSite create(clojure.lang.Symbol)"));
//								}
        else if (value instanceof Var) {
            Var var = (Var) value;
            gen.push(var.ns.name.toString());
            gen.push(var.sym.toString());
            gen.invokeStatic(Compiler.RT_TYPE, Method.getMethod("clojure.lang.Var var(String,String)"));
        } else if (value instanceof IType) {
            Method ctor = new Method("<init>", Type.getConstructorDescriptor(value.getClass().getConstructors()[0]));
            gen.newInstance(Type.getType(value.getClass()));
            gen.dup();
            IPersistentVector fields = (IPersistentVector) Reflector.invokeStaticMethod(value.getClass(), "getBasis", new Object[]{});
            for (ISeq s = RT.seq(fields); s != null; s = s.next()) {
                Symbol field = (Symbol) s.first();
                Class k = Compiler.tagClass(Compiler.tagOf(field));
                Object val = Reflector.getInstanceField(value, field.name);
                emitValue(val, gen);

                if (k.isPrimitive()) {
                    Type b = Type.getType(Compiler.boxClass(k));
                    String p = Type.getType(k).getDescriptor();
                    String n = k.getName();

                    gen.invokeVirtual(b, new Method(n + "Value", "()" + p));
                }
            }
            gen.invokeConstructor(Type.getType(value.getClass()), ctor);
        } else if (value instanceof IRecord) {
            Method createMethod = Method.getMethod(value.getClass().getName() + " create(clojure.lang.IPersistentMap)");
            emitValue(PersistentArrayMap.create((java.util.Map) value), gen);
            gen.invokeStatic(Compiler.getType(value.getClass()), createMethod);
        } else if (value instanceof IPersistentMap) {
            List entries = new ArrayList();
            for (Map.Entry entry : (Set<Map.Entry>) ((Map) value).entrySet()) {
                entries.add(entry.getKey());
                entries.add(entry.getValue());
            }
            emitListAsObjectArray(entries, gen);
            gen.invokeStatic(Compiler.RT_TYPE,
                    Method.getMethod("clojure.lang.IPersistentMap map(Object[])"));
        } else if (value instanceof IPersistentVector) {
            emitListAsObjectArray(value, gen);
            gen.invokeStatic(Compiler.RT_TYPE, Method.getMethod(
                    "clojure.lang.IPersistentVector vector(Object[])"));
        } else if (value instanceof PersistentHashSet) {
            ISeq vs = RT.seq(value);
            if (vs == null)
                gen.getStatic(Type.getType(PersistentHashSet.class), "EMPTY", Type.getType(PersistentHashSet.class));
            else {
                emitListAsObjectArray(vs, gen);
                gen.invokeStatic(Type.getType(PersistentHashSet.class), Method.getMethod(
                        "clojure.lang.PersistentHashSet create(Object[])"));
            }
        } else if (value instanceof ISeq || value instanceof IPersistentList) {
            emitListAsObjectArray(value, gen);
            gen.invokeStatic(Type.getType(Arrays.class),
                    Method.getMethod("java.util.List asList(Object[])"));
            gen.invokeStatic(Type.getType(PersistentList.class),
                    Method.getMethod(
                            "clojure.lang.IPersistentList create(java.util.List)"));
        } else if (value instanceof Pattern) {
            emitValue(value.toString(), gen);
            gen.invokeStatic(Type.getType(Pattern.class),
                    Method.getMethod("java.util.regex.Pattern compile(String)"));
        } else {
            String cs = null;
            try {
                cs = RT.printString(value);
//				System.out.println("WARNING SLOW CODE: " + Util.classOf(value) + " -> " + cs);
            } catch (Exception e) {
                throw Util.runtimeException(
                        "Can't embed object in code, maybe print-dup not defined: " +
                                value);
            }
            if (cs.length() == 0)
                throw Util.runtimeException(
                        "Can't embed unreadable object in code: " + value);

            if (cs.startsWith("#<"))
                throw Util.runtimeException(
                        "Can't embed unreadable object in code: " + cs);

            gen.push(cs);
            gen.invokeStatic(Compiler.RT_TYPE, readStringMethod);
            partial = false;
        }

        if (partial) {
            if (value instanceof IObj && RT.count(((IObj) value).meta()) > 0) {
                gen.checkCast(Compiler.IOBJ_TYPE);
                Object m = ((IObj) value).meta();
                emitValue(Compiler.elideMeta(m), gen);
                gen.checkCast(Compiler.IPERSISTENTMAP_TYPE);
                gen.invokeInterface(Compiler.IOBJ_TYPE,
                        Method.getMethod("clojure.lang.IObj withMeta(clojure.lang.IPersistentMap)"));
            }
        }
    }


    public void emitConstants(GeneratorAdapter clinitgen) {
        try {
            Var.pushThreadBindings(RT.map(RT.PRINT_DUP, RT.T));

            for (int i = 0; i < constants.count(); i++) {
                emitValue(constants.nth(i), clinitgen);
                clinitgen.checkCast(constantType(i));
                clinitgen.putStatic(objtype, constantName(i), constantType(i));
            }
        } finally {
            Var.popThreadBindings();
        }
    }

    public boolean isMutable(LocalBinding lb) {
        return isVolatile(lb) ||
                RT.booleanCast(RT.contains(fields, lb.sym)) &&
                        RT.booleanCast(RT.get(lb.sym.meta(), Keyword.intern("unsynchronized-mutable")));
    }

    public boolean isVolatile(LocalBinding lb) {
        return RT.booleanCast(RT.contains(fields, lb.sym)) &&
                RT.booleanCast(RT.get(lb.sym.meta(), Keyword.intern("volatile-mutable")));
    }

    public boolean isDeftype() {
        return fields != null;
    }

    public boolean supportsMeta() {
        return !isDeftype();
    }

    void emitClearCloses(GeneratorAdapter gen) {
//		int a = 1;
//		for(ISeq s = RT.keys(closes); s != null; s = s.next(), ++a)
//			{
//			LocalBinding lb = (LocalBinding) s.first();
//			Class primc = lb.getPrimitiveType();
//			if(primc == null)
//				{
//				gen.loadThis();
//				gen.visitInsn(Opcodes.ACONST_NULL);
//				gen.putField(objtype, lb.name, OBJECT_TYPE);
//				}
//			}
    }

    public synchronized Class getCompiledClass() {
        if (compiledClass == null)
//			if(RT.booleanCast(COMPILE_FILES.deref()))
//				compiledClass = RT.classForName(name);//loader.defineClass(name, bytecode);
//			else
        {
            loader = (DynamicClassLoader) Compiler.LOADER.deref();
            compiledClass = loader.defineClass(name, bytecode, src);
        }
        return compiledClass;
    }

    public Object eval() {
        if (isDeftype())
            return null;
        try {
            long start = System.nanoTime();
            Object o = getCompiledClass().newInstance();
            if(System.getProperty("clojure.compile.debug-perf", "false").equals("true"))
                System.out.println("[PERF] " + ((System.nanoTime() - start) / 1000000.) + " millis newInstance of " + name);
            return o;
        } catch (Exception e) {
            throw Util.sneakyThrow(e);
        }
    }

    public void emitLetFnInits(GeneratorAdapter gen, ObjExpr objx, IPersistentSet letFnLocals) {
        //objx arg is enclosing objx, not this
        gen.checkCast(objtype);

        for (ISeq s = RT.keys(closes); s != null; s = s.next()) {
            LocalBinding lb = (LocalBinding) s.first();
            if (letFnLocals.contains(lb)) {
                Class primc = lb.getPrimitiveType();
                gen.dup();
                if (primc != null) {
                    objx.emitUnboxedLocal(gen, lb);
                    gen.putField(objtype, lb.name, Type.getType(primc));
                } else {
                    objx.emitLocal(gen, lb, false);
                    gen.putField(objtype, lb.name, Compiler.OBJECT_TYPE);
                }
            }
        }
        gen.pop();

    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        //emitting a Fn means constructing an instance, feeding closed-overs from enclosing scope, if any
        //objx arg is enclosing objx, not this
//		getCompiledClass();
        if (isDeftype()) {
            gen.visitInsn(Opcodes.ACONST_NULL);
        } else {
            gen.newInstance(objtype);
            gen.dup();
            if (supportsMeta())
                gen.visitInsn(Opcodes.ACONST_NULL);
            for (ISeq s = RT.seq(closesExprs); s != null; s = s.next()) {
                LocalBindingExpr lbe = (LocalBindingExpr) s.first();
                LocalBinding lb = lbe.b;
                if (lb.getPrimitiveType() != null)
                    objx.emitUnboxedLocal(gen, lb);
                else
                    objx.emitLocal(gen, lb, lbe.shouldClear);
            }
            gen.invokeConstructor(objtype, new Method("<init>", Type.VOID_TYPE, ctorTypes()));
        }
        if (context == C.STATEMENT)
            gen.pop();
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return (compiledClass != null) ? compiledClass
                : (tag != null) ? HostExpr.tagToClass(tag)
                : IFn.class;
    }

    public void emitAssignLocal(GeneratorAdapter gen, LocalBinding lb, Expr val) {
        if (!isMutable(lb))
            throw new IllegalArgumentException("Cannot assign to non-mutable: " + lb.name);
        Class primc = lb.getPrimitiveType();
        gen.loadThis();
        if (primc != null) {
            if (!(val instanceof MaybePrimitiveExpr && ((MaybePrimitiveExpr) val).canEmitPrimitive()))
                throw new IllegalArgumentException("Must assign primitive to primitive mutable: " + lb.name);
            MaybePrimitiveExpr me = (MaybePrimitiveExpr) val;
            me.emitUnboxed(C.EXPRESSION, this, gen);
            gen.putField(objtype, lb.name, Type.getType(primc));
        } else {
            val.emit(C.EXPRESSION, this, gen);
            gen.putField(objtype, lb.name, Compiler.OBJECT_TYPE);
        }
    }

    public void emitLocal(GeneratorAdapter gen, LocalBinding lb, boolean clear) {
        if (closes.containsKey(lb)) {
            Class primc = lb.getPrimitiveType();
            gen.loadThis();
            if (primc != null) {
                gen.getField(objtype, lb.name, Type.getType(primc));
                HostExpr.emitBoxReturn(this, gen, primc);
            } else {
                gen.getField(objtype, lb.name, Compiler.OBJECT_TYPE);
                if (onceOnly && clear && lb.canBeCleared) {
                    gen.loadThis();
                    gen.visitInsn(Opcodes.ACONST_NULL);
                    gen.putField(objtype, lb.name, Compiler.OBJECT_TYPE);
                }
            }
        } else {
            int argoff = isStatic ? 0 : 1;
            Class primc = lb.getPrimitiveType();
//            String rep = lb.sym.name + " " + lb.toString().substring(lb.toString().lastIndexOf('@'));
            if (lb.isArg) {
                gen.loadArg(lb.idx - argoff);
                if (primc != null)
                    HostExpr.emitBoxReturn(this, gen, primc);
                else {
                    if (clear && lb.canBeCleared) {
//                        System.out.println("clear: " + rep);
                        gen.visitInsn(Opcodes.ACONST_NULL);
                        gen.storeArg(lb.idx - argoff);
                    } else {
//                        System.out.println("use: " + rep);
                    }
                }
            } else {
                if (primc != null) {
                    gen.visitVarInsn(Type.getType(primc).getOpcode(Opcodes.ILOAD), lb.idx);
                    HostExpr.emitBoxReturn(this, gen, primc);
                } else {
                    gen.visitVarInsn(Compiler.OBJECT_TYPE.getOpcode(Opcodes.ILOAD), lb.idx);
                    if (clear && lb.canBeCleared) {
//                        System.out.println("clear: " + rep);
                        gen.visitInsn(Opcodes.ACONST_NULL);
                        gen.visitVarInsn(Compiler.OBJECT_TYPE.getOpcode(Opcodes.ISTORE), lb.idx);
                    } else {
//                        System.out.println("use: " + rep);
                    }
                }
            }
        }
    }

    public void emitUnboxedLocal(GeneratorAdapter gen, LocalBinding lb) {
        int argoff = isStatic ? 0 : 1;
        Class primc = lb.getPrimitiveType();
        if (closes.containsKey(lb)) {
            gen.loadThis();
            gen.getField(objtype, lb.name, Type.getType(primc));
        } else if (lb.isArg)
            gen.loadArg(lb.idx - argoff);
        else
            gen.visitVarInsn(Type.getType(primc).getOpcode(Opcodes.ILOAD), lb.idx);
    }

    public void emitVar(GeneratorAdapter gen, Var var) {
        Integer i = (Integer) vars.valAt(var);
        emitConstant(gen, i);
        //gen.getStatic(fntype, munge(var.sym.toString()), VAR_TYPE);
    }

    final static Method varGetMethod = Method.getMethod("Object get()");
    final static Method varGetRawMethod = Method.getMethod("Object getRawRoot()");

    public void emitVarValue(GeneratorAdapter gen, Var v) {
        Integer i = (Integer) vars.valAt(v);
        if (!v.isDynamic()) {
            emitConstant(gen, i);
            gen.invokeVirtual(Compiler.VAR_TYPE, varGetRawMethod);
        } else {
            emitConstant(gen, i);
            gen.invokeVirtual(Compiler.VAR_TYPE, varGetMethod);
        }
    }

    public void emitKeyword(GeneratorAdapter gen, Keyword k) {
        Integer i = (Integer) keywords.valAt(k);
        emitConstant(gen, i);
//		gen.getStatic(fntype, munge(k.sym.toString()), KEYWORD_TYPE);
    }

    public void emitConstant(GeneratorAdapter gen, int id) {
        gen.getStatic(objtype, constantName(id), constantType(id));
    }


    public String constantName(int id) {
        return CONST_PREFIX + id;
    }

    String siteName(int n) {
        return "__site__" + n;
    }

    public String siteNameStatic(int n) {
        return siteName(n) + "__";
    }

    String thunkName(int n) {
        return "__thunk__" + n;
    }

    public String cachedClassName(int n) {
        return "__cached_class__" + n;
    }

    String cachedVarName(int n) {
        return "__cached_var__" + n;
    }

    String varCallsiteName(int n) {
        return "__var__callsite__" + n;
    }

    public String thunkNameStatic(int n) {
        return thunkName(n) + "__";
    }

    public Type constantType(int id) {
        Object o = constants.nth(id);
        Class c = Util.classOf(o);
        if (c != null && Modifier.isPublic(c.getModifiers())) {
            //can't emit derived fn types due to visibility
            if (LazySeq.class.isAssignableFrom(c))
                return Type.getType(ISeq.class);
            else if (c == Keyword.class)
                return Type.getType(Keyword.class);
//			else if(c == KeywordCallSite.class)
//				return Type.getType(KeywordCallSite.class);
            else if (RestFn.class.isAssignableFrom(c))
                return Type.getType(RestFn.class);
            else if (AFn.class.isAssignableFrom(c))
                return Type.getType(AFn.class);
            else if (c == Var.class)
                return Type.getType(Var.class);
            else if (c == String.class)
                return Type.getType(String.class);

//			return Type.getType(c);
        }
        return Compiler.OBJECT_TYPE;
    }

}
