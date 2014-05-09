package clojure.lang.compiler;

public class CompilerException extends RuntimeException {
    final public String source;

    final public int line;

    public CompilerException(String source, int line, int column, Throwable cause) {
        super(clojure.lang.Compiler.errorMsg(source, line, column, cause.toString()), cause);
        this.source = source;
        this.line = line;
    }

    public String toString() {
        return getMessage();
    }
}
