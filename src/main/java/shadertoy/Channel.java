package shadertoy;

import jvre.core.Texture;

/**
 * What feeds one of a pass's four input channels (iChannel0..3): nothing, the output
 * of a buffer, or a loaded image texture. The assignment is plain data -- the render
 * graph reads it to bind the right texture; the channel strip mutates it (click to
 * cycle buffers/none, drop a file to assign a texture).
 *
 * The {@link Texture} of a TEXTURE channel is owned by {@link Project}, which loads
 * and closes it; this class only holds the reference.
 */
public final class Channel {

    public enum Kind { NONE, BUFFER, TEXTURE }

    private Kind kind = Kind.NONE;
    private int bufferIndex = 0;       // 0..3 -> Buffer A..D, when kind == BUFFER
    private Texture texture;           // Y-flipped for shader sampling
    private Texture previewTex;        // unflipped, for the thumbnail
    private String textureName = "";

    public Kind kind() { return kind; }
    public boolean isBuffer() { return kind == Kind.BUFFER; }
    public boolean isTexture() { return kind == Kind.TEXTURE; }
    public int bufferIndex() { return bufferIndex; }
    public Texture texture() { return texture; }
    public Texture previewTexture() { return previewTex; }

    /** Cycle the buffer/none sources: none -> Buffer A -> ... -> Buffer (count-1) ->
     *  none. A TEXTURE channel is cleared to none first (the host releases it). */
    public void cycle(int bufferCount) {
        if (kind == Kind.NONE) {
            kind = Kind.BUFFER;
            bufferIndex = 0;
        } else if (kind == Kind.BUFFER && bufferIndex + 1 < bufferCount) {
            bufferIndex++;
        } else {
            kind = Kind.NONE;
        }
    }

    void setTexture(Texture shader, Texture preview, String name) {
        this.kind = Kind.TEXTURE;
        this.texture = shader;
        this.previewTex = preview;
        this.textureName = name;
    }

    void clearToNone() {
        this.kind = Kind.NONE;
        this.texture = null;
        this.previewTex = null;
        this.textureName = "";
    }

    public String label() {
        return switch (kind) {
            case NONE -> "—";
            case BUFFER -> "Buf " + (char) ('A' + bufferIndex);
            case TEXTURE -> textureName;
        };
    }
}
