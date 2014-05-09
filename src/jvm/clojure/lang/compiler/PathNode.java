package clojure.lang.compiler;

public class PathNode {
    public final PATHTYPE type;
    public final PathNode parent;

    public PathNode(PATHTYPE type, PathNode parent) {
        this.type = type;
        this.parent = parent;
    }
}
