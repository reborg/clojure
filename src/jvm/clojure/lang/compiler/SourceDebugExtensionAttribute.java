package clojure.lang.compiler;

import clojure.asm.Attribute;
import clojure.asm.ByteVector;
import clojure.asm.ClassWriter;

public class SourceDebugExtensionAttribute extends Attribute {
    public SourceDebugExtensionAttribute() {
        super("SourceDebugExtension");
    }

    void writeSMAP(ClassWriter cw, String smap) {
        ByteVector bv = write(cw, null, -1, -1, -1);
        bv.putUTF8(smap);
    }
}
