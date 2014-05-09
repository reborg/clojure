package clojure.lang.compiler;

public enum C {
    STATEMENT,  //value ignored
    EXPRESSION, //value required
    RETURN,      //tail position relative to enclosing recur frame
    EVAL
}
