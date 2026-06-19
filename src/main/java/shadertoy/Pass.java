package shadertoy;

/**
 * One editable section of a multipass project: the shared Common code, the final
 * Image pass, or a Buffer (A-D). Each owns its own {@link Document} (so each has
 * independent text, caret, and undo history). Rendering state (pipeline, targets,
 * channel assignments) is attached as the multipass machinery grows; for now a Pass
 * is just a named document of a given kind.
 */
public final class Pass {

    public enum Kind { COMMON, IMAGE, BUFFER }

    private final String name;
    private final Kind kind;
    private final Document doc;

    public Pass(String name, Kind kind, Document doc) {
        this.name = name;
        this.kind = kind;
        this.doc = doc;
    }

    public String name() { return name; }
    public Kind kind() { return kind; }
    public Document doc() { return doc; }
}
