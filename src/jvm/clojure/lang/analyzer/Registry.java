package clojure.lang.analyzer;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.compiler.expr.KeywordExpr;

import java.util.IdentityHashMap;

public class Registry {

    public static int registerConstant(Object o) {
        if (!clojure.lang.Compiler.CONSTANTS.isBound())
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

    static void registerVar(Var var) {
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

    static public Object maybeResolveIn(Namespace n, Symbol sym) {
        //note - ns-qualified vars must already exist
        if (sym.ns != null) {
            Namespace ns = namespaceFor(n, sym);
            if (ns == null)
                return null;
            Var v = ns.findInternedVar(Symbol.intern(sym.name));
            if (v == null)
                return null;
            return v;
        } else if (sym.name.indexOf('.') > 0 && !sym.name.endsWith(".")
                || sym.name.charAt(0) == '[') {
            return RT.classForName(sym.name);
        } else if (sym.equals(Compiler.NS))
            return RT.NS_VAR;
        else if (sym.equals(Compiler.IN_NS))
            return RT.IN_NS_VAR;
        else {
            Object o = n.getMapping(sym);
            return o;
        }
    }

    static KeywordExpr registerKeyword(Keyword keyword) {
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

    private static void registerVarCallsite(Var v) {
        if (!Compiler.VAR_CALLSITES.isBound())
            throw new IllegalAccessError("VAR_CALLSITES is not bound");

        IPersistentCollection varCallsites = (IPersistentCollection) Compiler.VAR_CALLSITES.deref();

        varCallsites = varCallsites.cons(v);
        Compiler.VAR_CALLSITES.set(varCallsites);
//	return varCallsites.count()-1;
    }

    static Object resolve(Symbol sym, boolean allowPrivate) {
        return resolveIn(currentNS(), sym, allowPrivate);
    }
}
