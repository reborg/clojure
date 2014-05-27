/**
 *   Copyright (c) Rich Hickey. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

/* rich Aug 21, 2007 */

package clojure.lang;

//*

import clojure.asm.*;
import clojure.asm.commons.GeneratorAdapter;
import clojure.asm.commons.Method;
import clojure.lang.analyzer.Analyzer;
import clojure.lang.analyzer.Registry;
import clojure.lang.compiler.*;
import clojure.lang.compiler.expr.*;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//*/
/*

import org.objectweb.asm.*;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.CheckClassAdapter;
//*/

public class Compiler implements Opcodes {

    static final Symbol DEF = Symbol.intern("def");
    public static final Symbol LOOP = Symbol.intern("loop*");
    static final Symbol RECUR = Symbol.intern("recur");
    static final Symbol IF = Symbol.intern("if");
    static final Symbol LET = Symbol.intern("let*");
    static final Symbol LETFN = Symbol.intern("letfn*");
    public static final Symbol DO = Symbol.intern("do");
    public static final Symbol FN = Symbol.intern("fn*");
    public static final Symbol FNONCE = (Symbol) Symbol.intern("fn*").withMeta(RT.map(Keyword.intern(null, "once"), RT.T));
    public static final Symbol QUOTE = Symbol.intern("quote");
    static final Symbol THE_VAR = Symbol.intern("var");
    public static final Symbol DOT = Symbol.intern(".");
    static final Symbol ASSIGN = Symbol.intern("set!");
    //static final Symbol TRY_FINALLY = Symbol.intern("try-finally");
    static final Symbol TRY = Symbol.intern("try");
    public static final Symbol CATCH = Symbol.intern("catch");
    public static final Symbol FINALLY = Symbol.intern("finally");
    static final Symbol THROW = Symbol.intern("throw");
    static final Symbol MONITOR_ENTER = Symbol.intern("monitor-enter");
    static final Symbol MONITOR_EXIT = Symbol.intern("monitor-exit");
    static final Symbol IMPORT = Symbol.intern("clojure.core", "import*");
    //static final Symbol INSTANCE = Symbol.intern("instance?");
    static final Symbol DEFTYPE = Symbol.intern("deftype*");
    static final Symbol CASE = Symbol.intern("case*");

    //static final Symbol THISFN = Symbol.intern("thisfn");
    public static final Symbol CLASS = Symbol.intern("Class");
    public static final Symbol NEW = Symbol.intern("new");
    static final Symbol THIS = Symbol.intern("this");
    static final Symbol REIFY = Symbol.intern("reify*");
    //static final Symbol UNQUOTE = Symbol.intern("unquote");
//static final Symbol UNQUOTE_SPLICING = Symbol.intern("unquote-splicing");
//static final Symbol SYNTAX_QUOTE = Symbol.intern("clojure.core", "syntax-quote");
    static final Symbol LIST = Symbol.intern("clojure.core", "list");
    static final Symbol HASHMAP = Symbol.intern("clojure.core", "hash-map");
    static final Symbol VECTOR = Symbol.intern("clojure.core", "vector");
    public static final Symbol IDENTITY = Symbol.intern("clojure.core", "identity");

    public static final Symbol _AMP_ = Symbol.intern("&");
    public static final Symbol ISEQ = Symbol.intern("clojure.lang.ISeq");

    public static final Keyword inlineKey = Keyword.intern(null, "inline");
    public static final Keyword inlineAritiesKey = Keyword.intern(null, "inline-arities");
    static final Keyword staticKey = Keyword.intern(null, "static");
    public static final Keyword arglistsKey = Keyword.intern(null, "arglists");
    static final Symbol INVOKE_STATIC = Symbol.intern("invokeStatic");

    static final Keyword volatileKey = Keyword.intern(null, "volatile");
    public static final Keyword implementsKey = Keyword.intern(null, "implements");
    public static final String COMPILE_STUB_PREFIX = "compile__stub";

    public static final Keyword protocolKey = Keyword.intern(null, "protocol");
    static final Keyword onKey = Keyword.intern(null, "on");
    public static Keyword dynamicKey = Keyword.intern("dynamic");

    public static final Symbol NS = Symbol.intern("ns");
    public static final Symbol IN_NS = Symbol.intern("in-ns");

//static final Symbol IMPORT = Symbol.intern("import");
//static final Symbol USE = Symbol.intern("use");

//static final Symbol IFN = Symbol.intern("clojure.lang", "IFn");

    static final public IPersistentMap specials = PersistentHashMap.create(
            DEF, new DefExpr.Parser(),
            LOOP, new LetExpr.Parser(),
            RECUR, new RecurExpr.Parser(),
            IF, new IfExpr.Parser(),
            CASE, new CaseExpr.Parser(),
            LET, new LetExpr.Parser(),
            LETFN, new LetFnExpr.Parser(),
            DO, new BodyExpr.Parser(),
            FN, null,
            QUOTE, new ConstantExpr.Parser(),
            THE_VAR, new TheVarExpr.Parser(),
            IMPORT, new ImportExpr.Parser(),
            DOT, new HostExpr.Parser(),
            ASSIGN, new AssignExpr.Parser(),
            DEFTYPE, new NewInstanceExpr.DeftypeParser(),
            REIFY, new NewInstanceExpr.ReifyParser(),
//		TRY_FINALLY, new TryFinallyExpr.Parser(),
            TRY, new TryExpr.Parser(),
            THROW, new ThrowExpr.Parser(),
            MONITOR_ENTER, new MonitorEnterExpr.Parser(),
            MONITOR_EXIT, new MonitorExitExpr.Parser(),
//		INSTANCE, new InstanceExpr.Parser(),
//		IDENTICAL, new IdenticalExpr.Parser(),
//THISFN, null,
            CATCH, null,
            FINALLY, null,
//		CLASS, new ClassExpr.Parser(),
            NEW, new NewExpr.Parser(),
//		UNQUOTE, null,
//		UNQUOTE_SPLICING, null,
//		SYNTAX_QUOTE, null,
            _AMP_, null
    );

    public static final int MAX_POSITIONAL_ARITY = 20;
    public static final Type OBJECT_TYPE;
    private static final Type KEYWORD_TYPE = Type.getType(Keyword.class);
    public static final Type VAR_TYPE = Type.getType(Var.class);
    private static final Type SYMBOL_TYPE = Type.getType(Symbol.class);
    //private static final Type NUM_TYPE = Type.getType(Num.class);
    public static final Type IFN_TYPE = Type.getType(IFn.class);
    private static final Type AFUNCTION_TYPE = Type.getType(AFunction.class);
    public static final Type RT_TYPE = Type.getType(RT.class);
    public static final Type NUMBERS_TYPE = Type.getType(Numbers.class);
    public final static Type CLASS_TYPE = Type.getType(Class.class);
    public final static Type NS_TYPE = Type.getType(Namespace.class);
    public final static Type UTIL_TYPE = Type.getType(Util.class);
    public final static Type REFLECTOR_TYPE = Type.getType(Reflector.class);
    public final static Type THROWABLE_TYPE = Type.getType(Throwable.class);
    public final static Type BOOLEAN_OBJECT_TYPE = Type.getType(Boolean.class);
    public final static Type IPERSISTENTMAP_TYPE = Type.getType(IPersistentMap.class);
    public final static Type IOBJ_TYPE = Type.getType(IObj.class);

    public static final Type[][] ARG_TYPES;
    //private static final Type[] EXCEPTION_TYPES = {Type.getType(Exception.class)};
    public static final Type[] EXCEPTION_TYPES = {};

    static {
        OBJECT_TYPE = Type.getType(Object.class);
        ARG_TYPES = new Type[MAX_POSITIONAL_ARITY + 2][];
        for (int i = 0; i <= MAX_POSITIONAL_ARITY; ++i) {
            Type[] a = new Type[i];
            for (int j = 0; j < i; j++)
                a[j] = OBJECT_TYPE;
            ARG_TYPES[i] = a;
        }
        Type[] a = new Type[MAX_POSITIONAL_ARITY + 1];
        for (int j = 0; j < MAX_POSITIONAL_ARITY; j++)
            a[j] = OBJECT_TYPE;
        a[MAX_POSITIONAL_ARITY] = Type.getType("[Ljava/lang/Object;");
        ARG_TYPES[MAX_POSITIONAL_ARITY + 1] = a;


    }


    //symbol->localbinding
    static final public Var LOCAL_ENV = Var.create(null).setDynamic();

    //vector<localbinding>
    static final public Var LOOP_LOCALS = Var.create().setDynamic();

    //Label
    static final public Var LOOP_LABEL = Var.create().setDynamic();

    //vector<object>
    static final public Var CONSTANTS = Var.create().setDynamic();

    //IdentityHashMap
    static final public Var CONSTANT_IDS = Var.create().setDynamic();

    //vector<keyword>
    static final public Var KEYWORD_CALLSITES = Var.create().setDynamic();

    //vector<var>
    static final public Var PROTOCOL_CALLSITES = Var.create().setDynamic();

    //set<var>
    static final public Var VAR_CALLSITES = Var.create().setDynamic();

    //keyword->constid
    static final public Var KEYWORDS = Var.create().setDynamic();

    //var->constid
    static final public Var VARS = Var.create().setDynamic();

    //FnFrame
    static final public Var METHOD = Var.create(null).setDynamic();

    //null or not
    static final public Var IN_CATCH_FINALLY = Var.create(null).setDynamic();

    static final public Var NO_RECUR = Var.create(null).setDynamic();

    //DynamicClassLoader
    static final public Var LOADER = Var.create().setDynamic();

    //String
    static final public Var SOURCE = Var.intern(Namespace.findOrCreate(Symbol.intern("clojure.core")),
            Symbol.intern("*source-path*"), "NO_SOURCE_FILE").setDynamic();

    //String
    static final public Var SOURCE_PATH = Var.intern(Namespace.findOrCreate(Symbol.intern("clojure.core")),
            Symbol.intern("*file*"), "NO_SOURCE_PATH").setDynamic();

    //String
    static final public Var COMPILE_PATH = Var.intern(Namespace.findOrCreate(Symbol.intern("clojure.core")),
            Symbol.intern("*compile-path*"), null).setDynamic();
    //boolean
    static final public Var COMPILE_FILES = Var.intern(Namespace.findOrCreate(Symbol.intern("clojure.core")),
            Symbol.intern("*compile-files*"), Boolean.FALSE).setDynamic();

    static final public Var INSTANCE = Var.intern(Namespace.findOrCreate(Symbol.intern("clojure.core")),
            Symbol.intern("instance?"));

    static final public Var ADD_ANNOTATIONS = Var.intern(Namespace.findOrCreate(Symbol.intern("clojure.core")),
            Symbol.intern("add-annotations"));

    static final public Keyword disableLocalsClearingKey = Keyword.intern("disable-locals-clearing");
    static final public Keyword elideMetaKey = Keyword.intern("elide-meta");

    static final public Var COMPILER_OPTIONS = Var.intern(Namespace.findOrCreate(Symbol.intern("clojure.core")),
            Symbol.intern("*compiler-options*"), null).setDynamic();

    static public Object getCompilerOption(Keyword k) {
        return RT.get(COMPILER_OPTIONS.deref(), k);
    }

    public static Object elideMeta(Object m) {
        Collection<Object> elides = (Collection<Object>) getCompilerOption(elideMetaKey);
        if (elides != null) {
            for (Object k : elides) {
//                System.out.println("Eliding:" + k + " : " + RT.get(m, k));
                m = RT.dissoc(m, k);
            }
//            System.out.println("Remaining: " + RT.keys(m));
        }
        return m;
    }

    //Integer
    static final public Var LINE = Var.create(0).setDynamic();
    static final public Var COLUMN = Var.create(0).setDynamic();

    //Integer
    static final public Var LINE_BEFORE = Var.create(0).setDynamic();
    static final public Var COLUMN_BEFORE = Var.create(0).setDynamic();
    static final public Var LINE_AFTER = Var.create(0).setDynamic();
    static final public Var COLUMN_AFTER = Var.create(0).setDynamic();

    //Integer
    static final public Var NEXT_LOCAL_NUM = Var.create(0).setDynamic();

    //Integer
    static final public Var RET_LOCAL_NUM = Var.create().setDynamic();


    static final public Var COMPILE_STUB_SYM = Var.create(null).setDynamic();
    static final public Var COMPILE_STUB_CLASS = Var.create(null).setDynamic();


    //PathNode chain
    static final public Var CLEAR_PATH = Var.create(null).setDynamic();

    //tail of PathNode chain
    static final public Var CLEAR_ROOT = Var.create(null).setDynamic();

    //LocalBinding -> Set<LocalBindingExpr>
    static final public Var CLEAR_SITES = Var.create(null).setDynamic();

    static final public Class RECUR_CLASS = Recur.class;

    static Symbol resolveSymbol(Symbol sym) {
        //already qualified or classname?
        if (sym.name.indexOf('.') > 0)
            return sym;
        if (sym.ns != null) {
            Namespace ns = Registry.namespaceFor(sym);
            if (ns == null || ns.name.name == sym.ns)
                return sym;
            return Symbol.intern(ns.name.name, sym.name);
        }
        Object o = Registry.currentNS().getMapping(sym);
        if (o == null)
            return Symbol.intern(Registry.currentNS().name.name, sym.name);
        else if (o instanceof Class)
            return Symbol.intern(null, ((Class) o).getName());
        else if (o instanceof Var) {
            Var v = (Var) o;
            return Symbol.intern(v.ns.name.name, v.sym.name);
        }
        return null;

    }

    public static Class maybePrimitiveType(Expr e) {
        if (e instanceof MaybePrimitiveExpr && e.hasJavaClass() && ((MaybePrimitiveExpr) e).canEmitPrimitive()) {
            Class c = e.getJavaClass();
            if (Util.isPrimitive(c))
                return c;
        }
        return null;
    }

    public static Class maybeJavaClass(Collection<Expr> exprs) {
        Class match = null;
        try {
            for (Expr e : exprs) {
                if (e instanceof ThrowExpr)
                    continue;
                if (!e.hasJavaClass())
                    return null;
                Class c = e.getJavaClass();
                if (match == null)
                    match = c;
                else if (match != c)
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
        return match;
    }


    public final static NilExpr NIL_EXPR = new NilExpr();

    public final static BooleanExpr TRUE_EXPR = new BooleanExpr(true);
    public final static BooleanExpr FALSE_EXPR = new BooleanExpr(false);


    //static class TryFinallyExpr implements Expr{
//	final Expr tryExpr;
//	final Expr finallyExpr;
//
//
//	public TryFinallyExpr(Expr tryExpr, Expr finallyExpr){
//		this.tryExpr = tryExpr;
//		this.finallyExpr = finallyExpr;
//	}
//
//	public Object eval() {
//		throw new UnsupportedOperationException("Can't eval try");
//	}
//
//	public void emit(C context, FnExpr fn, GeneratorAdapter gen){
//		Label startTry = gen.newLabel();
//		Label endTry = gen.newLabel();
//		Label end = gen.newLabel();
//		Label finallyLabel = gen.newLabel();
//		gen.visitTryCatchBlock(startTry, endTry, finallyLabel, null);
//		gen.mark(startTry);
//		tryExpr.emit(context, fn, gen);
//		gen.mark(endTry);
//		finallyExpr.emit(C.STATEMENT, fn, gen);
//		gen.goTo(end);
//		gen.mark(finallyLabel);
//		//exception should be on stack
//		finallyExpr.emit(C.STATEMENT, fn, gen);
//		gen.throwException();
//		gen.mark(end);
//	}
//
//	public boolean hasJavaClass() {
//		return tryExpr.hasJavaClass();
//	}
//
//	public Class getJavaClass() {
//		return tryExpr.getJavaClass();
//	}
//
//	static class Parser implements IParser{
//		public Expr parse(C context, Object frm) {
//			ISeq form = (ISeq) frm;
//			//(try-finally try-expr finally-expr)
//			if(form.count() != 3)
//				throw new IllegalArgumentException(
//						"Wrong number of arguments, expecting: (try-finally try-expr finally-expr) ");
//
//			if(context == C.EVAL || context == C.EXPRESSION)
//				return analyze(context, RT.list(RT.list(FN, PersistentVector.EMPTY, form)));
//
//			return new TryFinallyExpr(analyze(context, RT.second(form)),
//			                          analyze(C.STATEMENT, RT.third(form)));
//		}
//	}
//}


    static public boolean subsumes(Class[] c1, Class[] c2) {
        //presumes matching lengths
        Boolean better = false;
        for (int i = 0; i < c1.length; i++) {
            if (c1[i] != c2[i])// || c2[i].isPrimitive() && c1[i] == Object.class))
            {
                if (!c1[i].isPrimitive() && c2[i].isPrimitive()
                        //|| Number.class.isAssignableFrom(c1[i]) && c2[i].isPrimitive()
                        ||
                        c2[i].isAssignableFrom(c1[i]))
                    better = true;
                else
                    return false;
            }
        }
        return better;
    }

    public static String getTypeStringForArgs(IPersistentVector args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.count(); i++) {
            Expr arg = (Expr) args.nth(i);
            if (i > 0) sb.append(", ");
            sb.append(arg.hasJavaClass() ? arg.getJavaClass().getName() : "unknown");
        }
        return sb.toString();
    }

    public static int getMatchingParams(String methodName, ArrayList<Class[]> paramlists, IPersistentVector argexprs,
                                        List<Class> rets) {
        //presumes matching lengths
        int matchIdx = -1;
        boolean tied = false;
        boolean foundExact = false;
        for (int i = 0; i < paramlists.size(); i++) {
            boolean match = true;
            ISeq aseq = argexprs.seq();
            int exact = 0;
            for (int p = 0; match && p < argexprs.count() && aseq != null; ++p, aseq = aseq.next()) {
                Expr arg = (Expr) aseq.first();
                Class aclass = arg.hasJavaClass() ? arg.getJavaClass() : Object.class;
                Class pclass = paramlists.get(i)[p];
                if (arg.hasJavaClass() && aclass == pclass)
                    exact++;
                else
                    match = Reflector.paramArgTypeMatch(pclass, aclass);
            }
            if (exact == argexprs.count()) {
                if (!foundExact || matchIdx == -1 || rets.get(matchIdx).isAssignableFrom(rets.get(i)))
                    matchIdx = i;
                tied = false;
                foundExact = true;
            } else if (match && !foundExact) {
                if (matchIdx == -1)
                    matchIdx = i;
                else {
                    if (subsumes(paramlists.get(i), paramlists.get(matchIdx))) {
                        matchIdx = i;
                        tied = false;
                    } else if (Arrays.equals(paramlists.get(matchIdx), paramlists.get(i))) {
                        if (rets.get(matchIdx).isAssignableFrom(rets.get(i)))
                            matchIdx = i;
                    } else if (!(subsumes(paramlists.get(matchIdx), paramlists.get(i))))
                        tied = true;
                }
            }
        }
        if (tied)
            throw new IllegalArgumentException("More than one matching method found: " + methodName);

        return matchIdx;
    }

    static final public IPersistentMap CHAR_MAP =
            PersistentHashMap.create('-', "_",
//		                         '.', "_DOT_",
                    ':', "_COLON_",
                    '+', "_PLUS_",
                    '>', "_GT_",
                    '<', "_LT_",
                    '=', "_EQ_",
                    '~', "_TILDE_",
                    '!', "_BANG_",
                    '@', "_CIRCA_",
                    '#', "_SHARP_",
                    '\'', "_SINGLEQUOTE_",
                    '"', "_DOUBLEQUOTE_",
                    '%', "_PERCENT_",
                    '^', "_CARET_",
                    '&', "_AMPERSAND_",
                    '*', "_STAR_",
                    '|', "_BAR_",
                    '{', "_LBRACE_",
                    '}', "_RBRACE_",
                    '[', "_LBRACK_",
                    ']', "_RBRACK_",
                    '/', "_SLASH_",
                    '\\', "_BSLASH_",
                    '?', "_QMARK_");

    static final public IPersistentMap DEMUNGE_MAP;
    static final public Pattern DEMUNGE_PATTERN;

    static {
        // DEMUNGE_MAP maps strings to characters in the opposite
        // direction that CHAR_MAP does, plus it maps "$" to '/'
        IPersistentMap m = RT.map("$", '/');
        for (ISeq s = RT.seq(CHAR_MAP); s != null; s = s.next()) {
            IMapEntry e = (IMapEntry) s.first();
            Character origCh = (Character) e.key();
            String escapeStr = (String) e.val();
            m = m.assoc(escapeStr, origCh);
        }
        DEMUNGE_MAP = m;

        // DEMUNGE_PATTERN searches for the first of any occurrence of
        // the strings that are keys of DEMUNGE_MAP.
        // Note: Regex matching rules mean that #"_|_COLON_" "_COLON_"
        // returns "_", but #"_COLON_|_" "_COLON_" returns "_COLON_"
        // as desired.  Sorting string keys of DEMUNGE_MAP from longest to
        // shortest ensures correct matching behavior, even if some strings are
        // prefixes of others.
        Object[] mungeStrs = RT.toArray(RT.keys(m));
        Arrays.sort(mungeStrs, new Comparator() {
            public int compare(Object s1, Object s2) {
                return ((String) s2).length() - ((String) s1).length();
            }
        });
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object s : mungeStrs) {
            String escapeStr = (String) s;
            if (!first)
                sb.append("|");
            first = false;
            sb.append("\\Q");
            sb.append(escapeStr);
            sb.append("\\E");
        }
        DEMUNGE_PATTERN = Pattern.compile(sb.toString());
    }

    static public String munge(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            String sub = (String) CHAR_MAP.valAt(c);
            if (sub != null)
                sb.append(sub);
            else
                sb.append(c);
        }
        return sb.toString();
    }

    static public String demunge(String mungedName) {
        StringBuilder sb = new StringBuilder();
        Matcher m = DEMUNGE_PATTERN.matcher(mungedName);
        int lastMatchEnd = 0;
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            // Keep everything before the match
            sb.append(mungedName.substring(lastMatchEnd, start));
            lastMatchEnd = end;
            // Replace the match with DEMUNGE_MAP result
            Character origCh = (Character) DEMUNGE_MAP.valAt(m.group());
            sb.append(origCh);
        }
        // Keep everything after the last match
        sb.append(mungedName.substring(lastMatchEnd));
        return sb.toString();
    }

    //static class KeywordSiteInvokeExpr implements Expr{
//	public final Expr site;
//	public final Object tag;
//	public final Expr target;
//	public final int line;
//	public final int column;
//	public final String source;
//
//	public KeywordSiteInvokeExpr(String source, int line, int column, Symbol tag, Expr site, Expr target){
//		this.source = source;
//		this.site = site;
//		this.target = target;
//		this.line = line;
//		this.column = column;
//		this.tag = tag;
//	}
//
//	public Object eval() {
//		try
//			{
//			KeywordCallSite s = (KeywordCallSite) site.eval();
//			return s.thunk.invoke(s,target.eval());
//			}
//		catch(Throwable e)
//			{
//			if(!(e instanceof CompilerException))
//				throw new CompilerException(source, line, column, e);
//			else
//				throw (CompilerException) e;
//			}
//	}
//
//	public void emit(C context, ObjExpr objx, GeneratorAdapter gen){
//		gen.visitLineNumber(line, gen.mark());
//		site.emit(C.EXPRESSION, objx, gen);
//		gen.dup();
//		gen.getField(Type.getType(KeywordCallSite.class),"thunk",IFN_TYPE);
//		gen.swap();
//		target.emit(C.EXPRESSION, objx, gen);
//
//		gen.invokeInterface(IFN_TYPE, new Method("invoke", OBJECT_TYPE, ARG_TYPES[2]));
//		if(context == C.STATEMENT)
//			gen.pop();
//	}
//
//	public boolean hasJavaClass() {
//		return tag != null;
//	}
//
//	public Class getJavaClass() {
//		return HostExpr.tagToClass(tag);
//	}
//
//}

    static PathNode clearPathRoot() {
        return (PathNode) CLEAR_ROOT.get();
    }

    public static LocalBinding registerLocal(Symbol sym, Symbol tag, Expr init, boolean isArg) {
        int num = getAndIncLocalNum();
        LocalBinding b = new LocalBinding(num, sym, tag, init, isArg, clearPathRoot());
        IPersistentMap localsMap = (IPersistentMap) LOCAL_ENV.deref();
        LOCAL_ENV.set(RT.assoc(localsMap, b.sym, b));
        ObjMethod method = (ObjMethod) METHOD.deref();
        method.locals = (IPersistentMap) RT.assoc(method.locals, b, b);
        method.indexlocals = (IPersistentMap) RT.assoc(method.indexlocals, num, b);
        return b;
    }

    public static int getAndIncLocalNum() {
        int num = ((Number) NEXT_LOCAL_NUM.deref()).intValue();
        ObjMethod m = (ObjMethod) METHOD.deref();
        if (num > m.maxLocal)
            m.maxLocal = num;
        NEXT_LOCAL_NUM.set(num + 1);
        return num;
    }

    static Object macroexpand(Object form) {
        Object exf = Analyzer.macroexpand1(form);
        if (exf != form)
            return macroexpand(exf);
        return form;
    }

    public static String errorMsg(String source, int line, int column, String s) {
        return String.format("%s, compiling:(%s:%d:%d)", s, source, line, column);
    }

    public static Object eval(Object form) {
        return eval(form, true);
    }

    public static Object eval(Object form, boolean freshLoader) {
        boolean createdLoader = false;
        if (true)//!LOADER.isBound())
        {
            Var.pushThreadBindings(RT.map(LOADER, RT.makeClassLoader()));
            createdLoader = true;
        }
        try {
            Object line = Analyzer.lineDeref();
            Object column = Analyzer.columnDeref();
            if (RT.meta(form) != null && RT.meta(form).containsKey(RT.LINE_KEY))
                line = RT.meta(form).valAt(RT.LINE_KEY);
            if (RT.meta(form) != null && RT.meta(form).containsKey(RT.COLUMN_KEY))
                column = RT.meta(form).valAt(RT.COLUMN_KEY);
            Var.pushThreadBindings(RT.map(LINE, line, COLUMN, column));
            try {
                form = macroexpand(form);
                if (form instanceof ISeq && Util.equals(RT.first(form), DO)) {
                    ISeq s = RT.next(form);
                    for (; RT.next(s) != null; s = RT.next(s))
                        eval(RT.first(s), false);
                    return eval(RT.first(s), false);
                } else if ((form instanceof IType) ||
                        (form instanceof IPersistentCollection
                                && !(RT.first(form) instanceof Symbol
                                && ((Symbol) RT.first(form)).name.startsWith("def")))) {
                    ObjExpr fexpr = (ObjExpr) Analyzer.analyze(C.EXPRESSION, RT.list(FN, PersistentVector.EMPTY, form),
                            "eval" + RT.nextID());
                    IFn fn = (IFn) fexpr.eval();
                    return fn.invoke();
                } else {
                    Expr expr = Analyzer.analyze(C.EVAL, form);
                    return expr.eval();
                }
            } finally {
                Var.popThreadBindings();
            }
        } finally {
            if (createdLoader)
                Var.popThreadBindings();
        }
    }

    public static int registerKeywordCallsite(Keyword keyword) {
        if (!KEYWORD_CALLSITES.isBound())
            throw new IllegalAccessError("KEYWORD_CALLSITES is not bound");

        IPersistentVector keywordCallsites = (IPersistentVector) KEYWORD_CALLSITES.deref();

        keywordCallsites = keywordCallsites.cons(keyword);
        KEYWORD_CALLSITES.set(keywordCallsites);
        return keywordCallsites.count() - 1;
    }

    public static int registerProtocolCallsite(Var v) {
        if (!PROTOCOL_CALLSITES.isBound())
            throw new IllegalAccessError("PROTOCOL_CALLSITES is not bound");

        IPersistentVector protocolCallsites = (IPersistentVector) PROTOCOL_CALLSITES.deref();

        protocolCallsites = protocolCallsites.cons(v);
        PROTOCOL_CALLSITES.set(protocolCallsites);
        return protocolCallsites.count() - 1;
    }

    static ISeq fwdPath(PathNode p1) {
        ISeq ret = null;
        for (; p1 != null; p1 = p1.parent)
            ret = RT.cons(p1, ret);
        return ret;
    }

    public static PathNode commonPath(PathNode n1, PathNode n2) {
        ISeq xp = fwdPath(n1);
        ISeq yp = fwdPath(n2);
        if (RT.first(xp) != RT.first(yp))
            return null;
        while (RT.second(xp) != null && RT.second(xp) == RT.second(yp)) {
            xp = xp.next();
            yp = yp.next();
        }
        return (PathNode) RT.first(xp);
    }

    public static void addAnnotation(Object visitor, IPersistentMap meta) {
        if (meta != null && ADD_ANNOTATIONS.isBound())
            ADD_ANNOTATIONS.invoke(visitor, meta);
    }

    public static void addParameterAnnotation(Object visitor, IPersistentMap meta, int i) {
        if (meta != null && ADD_ANNOTATIONS.isBound())
            ADD_ANNOTATIONS.invoke(visitor, meta, i);
    }

    public static String destubClassName(String className) {
        //skip over prefix + '.' or '/'
        if (className.startsWith(COMPILE_STUB_PREFIX))
            return className.substring(COMPILE_STUB_PREFIX.length() + 1);
        return className;
    }

    public static Type getType(Class c) {
        String descriptor = Type.getType(c).getDescriptor();
        if (descriptor.startsWith("L"))
            descriptor = "L" + destubClassName(descriptor.substring(1));
        return Type.getType(descriptor);
    }

    public static Object loadFile(String file) throws IOException {
//	File fo = new File(file);
//	if(!fo.exists())
//		return null;

        FileInputStream f = new FileInputStream(file);
        try {
            return load(new InputStreamReader(f, RT.UTF8), new File(file).getAbsolutePath(), (new File(file)).getName());
        } finally {
            f.close();
        }
    }

    public static Object load(Reader rdr) {
        return load(rdr, null, "NO_SOURCE_FILE");
    }

    public static Object load(Reader rdr, String sourcePath, String sourceName) {
        Object EOF = new Object();
        Object ret = null;
        LineNumberingPushbackReader pushbackReader =
                (rdr instanceof LineNumberingPushbackReader) ? (LineNumberingPushbackReader) rdr :
                        new LineNumberingPushbackReader(rdr);
        Var.pushThreadBindings(
                RT.mapUniqueKeys(LOADER, RT.makeClassLoader(),
                        SOURCE_PATH, sourcePath,
                        SOURCE, sourceName,
                        METHOD, null,
                        LOCAL_ENV, null,
                        LOOP_LOCALS, null,
                        NEXT_LOCAL_NUM, 0,
                        RT.READEVAL, RT.T,
                        RT.CURRENT_NS, RT.CURRENT_NS.deref(),
                        LINE_BEFORE, pushbackReader.getLineNumber(),
                        COLUMN_BEFORE, pushbackReader.getColumnNumber(),
                        LINE_AFTER, pushbackReader.getLineNumber(),
                        COLUMN_AFTER, pushbackReader.getColumnNumber()
                        , RT.UNCHECKED_MATH, RT.UNCHECKED_MATH.deref()
                        , RT.WARN_ON_REFLECTION, RT.WARN_ON_REFLECTION.deref()
                        , RT.DATA_READERS, RT.DATA_READERS.deref()
                ));

        try {
            for (Object r = LispReader.read(pushbackReader, false, EOF, false); r != EOF;
                 r = LispReader.read(pushbackReader, false, EOF, false)) {
                LINE_AFTER.set(pushbackReader.getLineNumber());
                COLUMN_AFTER.set(pushbackReader.getColumnNumber());
                ret = eval(r, false);
                LINE_BEFORE.set(pushbackReader.getLineNumber());
                COLUMN_BEFORE.set(pushbackReader.getColumnNumber());
            }
        } catch (LispReader.ReaderException e) {
            throw new CompilerException(sourcePath, e.line, e.column, e.getCause());
        } catch (Throwable e) {
            if (!(e instanceof CompilerException))
                throw new CompilerException(sourcePath, (Integer) LINE_BEFORE.deref(), (Integer) COLUMN_BEFORE.deref(), e);
            else
                throw (CompilerException) e;
        } finally {
            Var.popThreadBindings();
        }
        return ret;
    }

    static public void writeClassFile(String internalName, byte[] bytecode) throws IOException {
        String genPath = (String) COMPILE_PATH.deref();
        if (genPath == null)
            throw Util.runtimeException("*compile-path* not set");
        String[] dirs = internalName.split("/");
        String p = genPath;
        for (int i = 0; i < dirs.length - 1; i++) {
            p += File.separator + dirs[i];
            (new File(p)).mkdir();
        }
        String path = genPath + File.separator + internalName + ".class";
        File cf = new File(path);
        cf.createNewFile();
        FileOutputStream cfs = new FileOutputStream(cf);
        try {
            cfs.write(bytecode);
            cfs.flush();
            cfs.getFD().sync();
        } finally {
            cfs.close();
        }
    }

    public static void pushNS() {
        Var.pushThreadBindings(PersistentHashMap.create(Var.intern(Symbol.intern("clojure.core"),
                Symbol.intern("*ns*")).setDynamic(), null));
    }

    public static void pushNSandLoader(ClassLoader loader) {
        Var.pushThreadBindings(RT.map(Var.intern(Symbol.intern("clojure.core"),
                Symbol.intern("*ns*")).setDynamic(),
                null,
                RT.FN_LOADER_VAR, loader,
                RT.READEVAL, RT.T
        ));
    }

    public static ILookupThunk getLookupThunk(Object target, Keyword k) {
        return null;  //To change body of created methods use File | Settings | File Templates.
    }

    static void compile1(GeneratorAdapter gen, ObjExpr objx, Object form) {
        Object line = Analyzer.lineDeref();
        Object column = Analyzer.columnDeref();
        if (RT.meta(form) != null && RT.meta(form).containsKey(RT.LINE_KEY))
            line = RT.meta(form).valAt(RT.LINE_KEY);
        if (RT.meta(form) != null && RT.meta(form).containsKey(RT.COLUMN_KEY))
            column = RT.meta(form).valAt(RT.COLUMN_KEY);
        Var.pushThreadBindings(
                RT.map(LINE, line, COLUMN, column
                        , LOADER, RT.makeClassLoader()
                ));
        try {
            form = macroexpand(form);
            if (form instanceof ISeq && Util.equals(RT.first(form), DO)) {
                for (ISeq s = RT.next(form); s != null; s = RT.next(s)) {
                    compile1(gen, objx, RT.first(s));
                }
            } else {
                Expr expr = Analyzer.analyze(C.EVAL, form);
                objx.keywords = (IPersistentMap) KEYWORDS.deref();
                objx.vars = (IPersistentMap) VARS.deref();
                objx.constants = (PersistentVector) CONSTANTS.deref();
                expr.emit(C.EXPRESSION, objx, gen);
                expr.eval();
            }
        } finally {
            Var.popThreadBindings();
        }
    }

    public static Object compile(Reader rdr, String sourcePath, String sourceName) throws IOException {
        if (COMPILE_PATH.deref() == null)
            throw Util.runtimeException("*compile-path* not set");

        Object EOF = new Object();
        Object ret = null;
        LineNumberingPushbackReader pushbackReader =
                (rdr instanceof LineNumberingPushbackReader) ? (LineNumberingPushbackReader) rdr :
                        new LineNumberingPushbackReader(rdr);
        Var.pushThreadBindings(
                RT.mapUniqueKeys(SOURCE_PATH, sourcePath,
                        SOURCE, sourceName,
                        METHOD, null,
                        LOCAL_ENV, null,
                        LOOP_LOCALS, null,
                        NEXT_LOCAL_NUM, 0,
                        RT.READEVAL, RT.T,
                        RT.CURRENT_NS, RT.CURRENT_NS.deref(),
                        LINE_BEFORE, pushbackReader.getLineNumber(),
                        COLUMN_BEFORE, pushbackReader.getColumnNumber(),
                        LINE_AFTER, pushbackReader.getLineNumber(),
                        COLUMN_AFTER, pushbackReader.getColumnNumber(),
                        CONSTANTS, PersistentVector.EMPTY,
                        CONSTANT_IDS, new IdentityHashMap(),
                        KEYWORDS, PersistentHashMap.EMPTY,
                        VARS, PersistentHashMap.EMPTY
                        , RT.UNCHECKED_MATH, RT.UNCHECKED_MATH.deref()
                        , RT.WARN_ON_REFLECTION, RT.WARN_ON_REFLECTION.deref()
                        , RT.DATA_READERS, RT.DATA_READERS.deref()
                        //    ,LOADER, RT.makeClassLoader()
                ));

        try {
            //generate loader class
            ObjExpr objx = new ObjExpr(null);
            objx.internalName = sourcePath.replace(File.separator, "/").substring(0, sourcePath.lastIndexOf('.'))
                    + RT.LOADER_SUFFIX;

            objx.objtype = Type.getObjectType(objx.internalName);
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassVisitor cv = cw;
            cv.visit(V1_5, ACC_PUBLIC + ACC_SUPER, objx.internalName, null, "java/lang/Object", null);

            //static load method
            GeneratorAdapter gen = new GeneratorAdapter(ACC_PUBLIC + ACC_STATIC,
                    Method.getMethod("void load ()"),
                    null,
                    null,
                    cv);
            gen.visitCode();

            for (Object r = LispReader.read(pushbackReader, false, EOF, false); r != EOF;
                 r = LispReader.read(pushbackReader, false, EOF, false)) {
                LINE_AFTER.set(pushbackReader.getLineNumber());
                COLUMN_AFTER.set(pushbackReader.getColumnNumber());
                compile1(gen, objx, r);
                LINE_BEFORE.set(pushbackReader.getLineNumber());
                COLUMN_BEFORE.set(pushbackReader.getColumnNumber());
            }
            //end of load
            gen.returnValue();
            gen.endMethod();

            //static fields for constants
            for (int i = 0; i < objx.constants.count(); i++) {
                cv.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, objx.constantName(i), objx.constantType(i).getDescriptor(),
                        null, null);
            }

            final int INITS_PER = 100;
            int numInits = objx.constants.count() / INITS_PER;
            if (objx.constants.count() % INITS_PER != 0)
                ++numInits;

            for (int n = 0; n < numInits; n++) {
                GeneratorAdapter clinitgen = new GeneratorAdapter(ACC_PUBLIC + ACC_STATIC,
                        Method.getMethod("void __init" + n + "()"),
                        null,
                        null,
                        cv);
                clinitgen.visitCode();
                try {
                    Var.pushThreadBindings(RT.map(RT.PRINT_DUP, RT.T));

                    for (int i = n * INITS_PER; i < objx.constants.count() && i < (n + 1) * INITS_PER; i++) {
                        objx.emitValue(objx.constants.nth(i), clinitgen);
                        clinitgen.checkCast(objx.constantType(i));
                        clinitgen.putStatic(objx.objtype, objx.constantName(i), objx.constantType(i));
                    }
                } finally {
                    Var.popThreadBindings();
                }
                clinitgen.returnValue();
                clinitgen.endMethod();
            }

            //static init for constants, keywords and vars
            GeneratorAdapter clinitgen = new GeneratorAdapter(ACC_PUBLIC + ACC_STATIC,
                    Method.getMethod("void <clinit> ()"),
                    null,
                    null,
                    cv);
            clinitgen.visitCode();
            Label startTry = clinitgen.newLabel();
            Label endTry = clinitgen.newLabel();
            Label end = clinitgen.newLabel();
            Label finallyLabel = clinitgen.newLabel();

//		if(objx.constants.count() > 0)
//			{
//			objx.emitConstants(clinitgen);
//			}
            for (int n = 0; n < numInits; n++)
                clinitgen.invokeStatic(objx.objtype, Method.getMethod("void __init" + n + "()"));

            clinitgen.push(objx.internalName.replace('/', '.'));
            clinitgen.invokeStatic(CLASS_TYPE, Method.getMethod("Class forName(String)"));
            clinitgen.invokeVirtual(CLASS_TYPE, Method.getMethod("ClassLoader getClassLoader()"));
            clinitgen.invokeStatic(Type.getType(Compiler.class), Method.getMethod("void pushNSandLoader(ClassLoader)"));
            clinitgen.mark(startTry);
            clinitgen.invokeStatic(objx.objtype, Method.getMethod("void load()"));
            clinitgen.mark(endTry);
            clinitgen.invokeStatic(VAR_TYPE, Method.getMethod("void popThreadBindings()"));
            clinitgen.goTo(end);

            clinitgen.mark(finallyLabel);
            //exception should be on stack
            clinitgen.invokeStatic(VAR_TYPE, Method.getMethod("void popThreadBindings()"));
            clinitgen.throwException();
            clinitgen.mark(end);
            clinitgen.visitTryCatchBlock(startTry, endTry, finallyLabel, null);

            //end of static init
            clinitgen.returnValue();
            clinitgen.endMethod();

            //end of class
            cv.visitEnd();

            writeClassFile(objx.internalName, cw.toByteArray());
        } catch (LispReader.ReaderException e) {
            throw new CompilerException(sourcePath, e.line, e.column, e.getCause());
        } finally {
            Var.popThreadBindings();
        }
        return ret;
    }

    public static Class primClass(Symbol sym) {
        if (sym == null)
            return null;
        Class c = null;
        if (sym.name.equals("int"))
            c = int.class;
        else if (sym.name.equals("long"))
            c = long.class;
        else if (sym.name.equals("float"))
            c = float.class;
        else if (sym.name.equals("double"))
            c = double.class;
        else if (sym.name.equals("char"))
            c = char.class;
        else if (sym.name.equals("short"))
            c = short.class;
        else if (sym.name.equals("byte"))
            c = byte.class;
        else if (sym.name.equals("boolean"))
            c = boolean.class;
        else if (sym.name.equals("void"))
            c = void.class;
        return c;
    }

    public static Class tagClass(Object tag) {
        if (tag == null)
            return Object.class;
        Class c = null;
        if (tag instanceof Symbol)
            c = primClass((Symbol) tag);
        if (c == null)
            c = HostExpr.tagToClass(tag);
        return c;
    }

    public static Class primClass(Class c) {
        return c.isPrimitive() ? c : Object.class;
    }

    public static Class boxClass(Class p) {
        if (!p.isPrimitive())
            return p;

        Class c = null;

        if (p == Integer.TYPE)
            c = Integer.class;
        else if (p == Long.TYPE)
            c = Long.class;
        else if (p == Float.TYPE)
            c = Float.class;
        else if (p == Double.TYPE)
            c = Double.class;
        else if (p == Character.TYPE)
            c = Character.class;
        else if (p == Short.TYPE)
            c = Short.class;
        else if (p == Byte.TYPE)
            c = Byte.class;
        else if (p == Boolean.TYPE)
            c = Boolean.class;

        return c;
    }

    public static IPersistentCollection emptyVarCallSites() {
        return PersistentHashSet.EMPTY;
    }

}
