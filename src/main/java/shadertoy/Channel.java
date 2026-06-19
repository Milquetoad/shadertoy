package shadertoy;

/**
 * What feeds one of a pass's four input channels (iChannel0..3). For now a channel is
 * either nothing or the output of a buffer; loaded textures arrive in the next slice.
 * The assignment is plain data -- the render graph reads it to bind the right texture,
 * and the channel strip UI mutates it.
 */
public final class Channel {

    public enum Kind { NONE, BUFFER }

    private Kind kind = Kind.NONE;
    private int bufferIndex = 0;   // 0..3 -> Buffer A..D, when kind == BUFFER

    public Kind kind() { return kind; }
    public int bufferIndex() { return bufferIndex; }
    public boolean isBuffer() { return kind == Kind.BUFFER; }

    /** Cycle to the next source: none -> Buffer A -> ... -> Buffer (count-1) -> none. */
    public void cycle(int bufferCount) {
        if (kind == Kind.NONE) {
            kind = Kind.BUFFER;
            bufferIndex = 0;
        } else if (bufferIndex + 1 < bufferCount) {
            bufferIndex++;
        } else {
            kind = Kind.NONE;
        }
    }

    public String label() {
        return kind == Kind.NONE ? "—" : "Buf " + (char) ('A' + bufferIndex);
    }
}
