package clojure.lang.analyzer;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.compiler.C;
import clojure.lang.compiler.CompilerException;
import clojure.lang.compiler.LocalBinding;
import clojure.lang.compiler.ObjMethod;
import clojure.lang.compiler.expr.*;

import java.util.IdentityHashMap;

public class Analyzer {

    public static int lineDeref() {
        return ((Number) Compiler.LINE.deref()).intValue();
    }

    public static int columnDeref() {
        return ((Number) Compiler.COLUMN.deref()).intValue();
    }

    public static boolean isSpecial(Object sym) {
        return Compiler.specials.containsKey(sym);
    }

    public static Expr analyze(C context, Object form) {
        return analyze(context, form, null);
    }

    public static Expr analyze(C context, Object form, String name) {
        //todo symbol macro expansion?
        try {
            if (form instanceof LazySeq) {
                form = RT.seq(form);
                if (form == null)
                    form = PersistentList.EMPTY;
            }
            if (form == null)
                return Compiler.NIL_EXPR;
            else if (form == Boolean.TRUE)
                return Compiler.TRUE_EXPR;
            else if (form == Boolean.FALSE)
                return Compiler.FALSE_EXPR;
            Class fclass = form.getClass();
            if (fclass == Symbol.class)
                return analyzeSymbol((Symbol) form);
            else if (fclass == Keyword.class)
                return registerKeyword((Keyword) form);
            else if (form instanceof Number)
                return NumberExpr.parse((Number) form);
            else if (fclass == String.class)
                return new StringExpr(((String) form).intern());
//	else if(fclass == Character.class)
//		return new CharExpr((Character) form);
            else if (form instanceof IPersistentCollection && ((IPersistentCollection) form).count() == 0) {
                Expr ret = new EmptyExpr(form);
                if (RT.meta(form) != null)
                    ret = new MetaExpr(ret, MapExpr
                            .parse(context == C.EVAL ? context : C.EXPRESSION, ((IObj) form).meta()));
                return ret;
            } else if (form instanceof ISeq)
                return analyzeSeq(context, (ISeq) form, name);
            else if (form instanceof IPersistentVector)
                return VectorExpr.parse(context, (IPersistentVector) form);
            else if (form instanceof IRecord)
                return new ConstantExpr(form);
            else if (form instanceof IType)
                return new ConstantExpr(form);
            else if (form instanceof IPersistentMap)
                return MapExpr.parse(context, (IPersistentMap) form);
            else if (form instanceof IPersistentSet)
                return SetExpr.parse(context, (IPersistentSet) form);

//	else
            //throw new UnsupportedOperationException();
            return new ConstantExpr(form);
        } catch (Throwable e) {
            if (!(e instanceof CompilerException))
                throw new CompilerException((String) Compiler.SOURCE_PATH.deref(), lineDeref(), columnDeref(), e);
            else
                throw (CompilerException) e;
        }
    }

    static public Var isMacro(Object op) {
        //no local macros for now
        if (op instanceof Symbol && referenceLocal((Symbol) op) != null)
            return null;
        if (op instanceof Symbol || op instanceof Var) {
            Var v = (op instanceof Var) ? (Var) op : lookupVar((Symbol) op, false, false);
            if (v != null && v.isMacro()) {
                if (v.ns != currentNS() && !v.isPublic())
                    throw new IllegalStateException("var: " + v + " is not public");
                return v;
            }
        }
        return null;
    }

    static public IFn isInline(Object op, int arity) {
        //no local inlines for now
        if (op instanceof Symbol && referenceLocal((Symbol) op) != null)
            return null;
        if (op instanceof Symbol || op instanceof Var) {
            Var v = (op instanceof Var) ? (Var) op : lookupVar((Symbol) op, false);
            if (v != null) {
                if (v.ns != currentNS() && !v.isPublic())
                    throw new IllegalStateException("var: " + v + " is not public");
                IFn ret = (IFn) RT.get(v.meta(), Compiler.inlineKey);
                if (ret != null) {
                    IFn arityPred = (IFn) RT.get(v.meta(), Compiler.inlineAritiesKey);
                    if (arityPred == null || RT.booleanCast(arityPred.invoke(arity)))
                        return ret;
                }
            }
        }
        return null;
    }

    public static boolean namesStaticMember(Symbol sym) {
        return sym.ns != null && namespaceFor(sym) == null;
    }

    public static Object preserveTag(ISeq src, Object dst) {
        Symbol tag = tagOf(src);
        if (tag != null && dst instanceof IObj) {
            IPersistentMap meta = RT.meta(dst);
            return ((IObj) dst).withMeta((IPersistentMap) RT.assoc(meta, RT.TAG_KEY, tag));
        }
        return dst;
    }

    public static Object macroexpand1(Object x) {
        if (x instanceof ISeq) {
            ISeq form = (ISeq) x;
            Object op = RT.first(form);
            if (isSpecial(op))
                return x;
            //macro expansion
            Var v = isMacro(op);
            if (v != null) {
                try {
                    return v.applyTo(RT.cons(form, RT.cons(Compiler.LOCAL_ENV.get(), form.next())));
                } catch (ArityException e) {
                    // hide the 2 extra params for a macro
                    throw new ArityException(e.actual - 2, e.name);
                }
            } else {
                if (op instanceof Symbol) {
                    Symbol sym = (Symbol) op;
                    String sname = sym.name;
                    //(.substring s 2 5) => (. s substring 2 5)
                    if (sym.name.charAt(0) == '.') {
                        if (RT.length(form) < 2)
                            throw new IllegalArgumentException(
                                    "Malformed member expression, expecting (.member target ...)");
                        Symbol meth = Symbol.intern(sname.substring(1));
                        Object target = RT.second(form);
                        if (HostExpr.maybeClass(target, false) != null) {
                            target = ((IObj) RT.list(Compiler.IDENTITY, target)).withMeta(RT.map(RT.TAG_KEY, Compiler.CLASS));
                        }
                        return preserveTag(form, RT.listStar(Compiler.DOT, target, meth, form.next().next()));
                    } else if (namesStaticMember(sym)) {
                        Symbol target = Symbol.intern(sym.ns);
                        Class c = HostExpr.maybeClass(target, false);
                        if (c != null) {
                            Symbol meth = Symbol.intern(sym.name);
                            return preserveTag(form, RT.listStar(Compiler.DOT, target, meth, form.next()));
                        }
                    } else {
                        //(s.substring 2 5) => (. s substring 2 5)
                        //also (package.class.name ...) (. package.class name ...)
                        int idx = sname.lastIndexOf('.');
//					if(idx > 0 && idx < sname.length() - 1)
//						{
//						Symbol target = Symbol.intern(sname.substring(0, idx));
//						Symbol meth = Symbol.intern(sname.substring(idx + 1));
//						return RT.listStar(DOT, target, meth, form.rest());
//						}
                        //(StringBuilder. "foo") => (new StringBuilder "foo")
                        //else
                        if (idx == sname.length() - 1)
                            return RT.listStar(Compiler.NEW, Symbol.intern(sname.substring(0, idx)), form.next());
                    }
                }
            }
        }
        return x;
    }

    private static Expr analyzeSeq(C context, ISeq form, String name) {
        Object line = lineDeref();
        Object column = columnDeref();
        if (RT.meta(form) != null && RT.meta(form).containsKey(RT.LINE_KEY))
            line = RT.meta(form).valAt(RT.LINE_KEY);
        if (RT.meta(form) != null && RT.meta(form).containsKey(RT.COLUMN_KEY))
            column = RT.meta(form).valAt(RT.COLUMN_KEY);
        Var.pushThreadBindings(
                RT.map(Compiler.LINE, line, Compiler.COLUMN, column));
        try {
            Object me = macroexpand1(form);
            if (me != form)
                return analyze(context, me, name);

            Object op = RT.first(form);
            if (op == null)
                throw new IllegalArgumentException("Can't call nil");
            IFn inline = isInline(op, RT.count(RT.next(form)));
            if (inline != null)
                return analyze(context, preserveTag(form, inline.applyTo(RT.next(form))));
            IParser p;
            if (op.equals(Compiler.FN))
                return FnExpr.parse(context, form, name);
            else if ((p = (IParser) Compiler.specials.valAt(op)) != null)
                return p.parse(context, form);
            else
                return InvokeExpr.parse(context, form);
        } catch (Throwable e) {
            if (!(e instanceof CompilerException))
                throw new CompilerException((String) Compiler.SOURCE_PATH.deref(), lineDeref(), columnDeref(), e);
            else
                throw (CompilerException) e;
        } finally {
            Var.popThreadBindings();
        }
    }

    public static int registerConstant(Object o) {
        if (!Compiler.CONSTANTS.isBound())
            return -1;
        PersistentVector v = (PersistentVector) Compiler.CONSTANTS.deref();
        IdentityHashMap<Object, Integer> ids = (IdentityHashMap<Object, Integer>) Compiler.CONSTANT_IDS.deref();
        Integer i = ids.get(o);
        if (i != null)
            return i;
        Compiler.CONSTANTS.set(RT.conj(v, o));
        ids.put(o, v.count());
        return v.count();
    }

    private static KeywordExpr registerKeyword(Keyword keyword) {
        if (!Compiler.KEYWORDS.isBound())
            return new KeywordExpr(keyword);

        IPersistentMap keywordsMap = (IPersistentMap) Compiler.KEYWORDS.deref();
        Object id = RT.get(keywordsMap, keyword);
        if (id == null) {
            Compiler.KEYWORDS.set(RT.assoc(keywordsMap, keyword, registerConstant(keyword)));
        }
        return new KeywordExpr(keyword);
//	KeywordExpr ke = (KeywordExpr) RT.get(keywordsMap, keyword);
//	if(ke == null)
//		KEYWORDS.set(RT.assoc(keywordsMap, keyword, ke = new KeywordExpr(keyword)));
//	return ke;
    }

    private static Expr analyzeSymbol(Symbol sym) {
        Symbol tag = tagOf(sym);
        if (sym.ns == null) //ns-qualified syms are always Vars
        {
            LocalBinding b = referenceLocal(sym);
            if (b != null) {
                return new LocalBindingExpr(b, tag);
            }
        } else {
            if (namespaceFor(sym) == null) {
                Symbol nsSym = Symbol.intern(sym.ns);
                Class c = HostExpr.maybeClass(nsSym, false);
                if (c != null) {
                    if (Reflector.getField(c, sym.name, true) != null)
                        return new StaticFieldExpr(lineDeref(), columnDeref(), c, sym.name, tag);
                    throw Util.runtimeException("Unable to find static field: " + sym.name + " in " + c);
                }
            }
        }
        //Var v = lookupVar(sym, false);
//	Var v = lookupVar(sym, false);
//	if(v != null)
//		return new VarExpr(v, tag);
        Object o = resolve(sym);
        if (o instanceof Var) {
            Var v = (Var) o;
            if (isMacro(v) != null)
                throw Util.runtimeException("Can't take value of a macro: " + v);
            if (RT.booleanCast(RT.get(v.meta(), RT.CONST_KEY)))
                return analyze(C.EXPRESSION, RT.list(Compiler.QUOTE, v.get()));
            registerVar(v);
            return new VarExpr(v, tag);
        } else if (o instanceof Class)
            return new ConstantExpr(o);
        else if (o instanceof Symbol)
            return new UnresolvedVarExpr((Symbol) o);

        throw Util.runtimeException("Unable to resolve symbol: " + sym + " in this context");

    }

    public static Object resolve(Symbol sym) {
        return resolveIn(currentNS(), sym, false);
    }

    public static Namespace namespaceFor(Symbol sym) {
        return namespaceFor(currentNS(), sym);
    }

    public static Namespace namespaceFor(Namespace inns, Symbol sym) {
        //note, presumes non-nil sym.ns
        // first check against currentNS' aliases...
        Symbol nsSym = Symbol.intern(sym.ns);
        Namespace ns = inns.lookupAlias(nsSym);
        if (ns == null) {
            // ...otherwise check the Namespaces map.
            ns = Namespace.find(nsSym);
        }
        return ns;
    }

    static public Object resolveIn(Namespace n, Symbol sym, boolean allowPrivate) {
        //note - ns-qualified vars must already exist
        if (sym.ns != null) {
            Namespace ns = namespaceFor(n, sym);
            if (ns == null)
                throw Util.runtimeException("No such namespace: " + sym.ns);

            Var v = ns.findInternedVar(Symbol.intern(sym.name));
            if (v == null)
                throw Util.runtimeException("No such var: " + sym);
            else if (v.ns != currentNS() && !v.isPublic() && !allowPrivate)
                throw new IllegalStateException("var: " + sym + " is not public");
            return v;
        } else if (sym.name.indexOf('.') > 0 || sym.name.charAt(0) == '[') {
            return RT.classForName(sym.name);
        } else if (sym.equals(Compiler.NS))
            return RT.NS_VAR;
        else if (sym.equals(Compiler.IN_NS))
            return RT.IN_NS_VAR;
        else {
            if (Util.equals(sym, Compiler.COMPILE_STUB_SYM.get()))
                return Compiler.COMPILE_STUB_CLASS.get();
            Object o = n.getMapping(sym);
            if (o == null) {
                if (RT.booleanCast(RT.ALLOW_UNRESOLVED_VARS.deref())) {
                    return sym;
                } else {
                    throw Util.runtimeException("Unable to resolve symbol: " + sym + " in this context");
                }
            }
            return o;
        }
    }

    static Var lookupVar(Symbol sym, boolean internNew, boolean registerMacro) {
        Var var = null;

        //note - ns-qualified vars in other namespaces must already exist
        if (sym.ns != null) {
            Namespace ns = namespaceFor(sym);
            if (ns == null)
                return null;
            //throw Util.runtimeException("No such namespace: " + sym.ns);
            Symbol name = Symbol.intern(sym.name);
            if (internNew && ns == currentNS())
                var = currentNS().intern(name);
            else
                var = ns.findInternedVar(name);
        } else if (sym.equals(Compiler.NS))
            var = RT.NS_VAR;
        else if (sym.equals(Compiler.IN_NS))
            var = RT.IN_NS_VAR;
        else {
            //is it mapped?
            Object o = currentNS().getMapping(sym);
            if (o == null) {
                //introduce a new var in the current ns
                if (internNew)
                    var = currentNS().intern(Symbol.intern(sym.name));
            } else if (o instanceof Var) {
                var = (Var) o;
            } else {
                throw Util.runtimeException("Expecting var, but " + sym + " is mapped to " + o);
            }
        }
        if (var != null && (!var.isMacro() || registerMacro))
            registerVar(var);
        return var;
    }

    public static Var lookupVar(Symbol sym, boolean internNew) {
        return lookupVar(sym, internNew, true);
    }

    private static void registerVar(Var var) {
        if (!Compiler.VARS.isBound())
            return;
        IPersistentMap varsMap = (IPersistentMap) Compiler.VARS.deref();
        Object id = RT.get(varsMap, var);
        if (id == null) {
            Compiler.VARS.set(RT.assoc(varsMap, var, registerConstant(var)));
        }
//	if(varsMap != null && RT.get(varsMap, var) == null)
//		VARS.set(RT.assoc(varsMap, var, var));
    }

    public static Namespace currentNS() {
        return (Namespace) RT.CURRENT_NS.deref();
    }

    static void closeOver(LocalBinding b, ObjMethod method) {
        if (b != null && method != null) {
            if (RT.get(method.locals, b) == null) {
                method.objx.closes = (IPersistentMap) RT.assoc(method.objx.closes, b, b);
                closeOver(b, method.parent);
            } else if (Compiler.IN_CATCH_FINALLY.deref() != null) {
                method.localsUsedInCatchFinally = (PersistentHashSet) method.localsUsedInCatchFinally.cons(b.idx);
            }
        }
    }

    static LocalBinding referenceLocal(Symbol sym) {
        if (!Compiler.LOCAL_ENV.isBound())
            return null;
        LocalBinding b = (LocalBinding) RT.get(Compiler.LOCAL_ENV.deref(), sym);
        if (b != null) {
            ObjMethod method = (ObjMethod) Compiler.METHOD.deref();
            closeOver(b, method);
        }
        return b;
    }

    public static Symbol tagOf(Object o) {
        Object tag = RT.get(RT.meta(o), RT.TAG_KEY);
        if (tag instanceof Symbol)
            return (Symbol) tag;
        else if (tag instanceof String)
            return Symbol.intern(null, (String) tag);
        return null;
    }
}
