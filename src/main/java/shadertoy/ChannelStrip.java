package shadertoy;

import jvre.core.Color;
import jvre.core.Font;
import jvre.core.Input;
import jvre.core.MouseButton;
import jvre.core.Renderer2D;

/**
 * The four channel slots shown below the active pass's editor (Shadertoy-style): each
 * slot previews its assigned source (a live thumbnail of the buffer's output) and is
 * labelled with the input name. Clicking a slot cycles its source. Common has no
 * channels, so the host omits the strip there.
 */
public final class ChannelStrip {

    private static final Color BG          = Color.rgb(18, 20, 25);
    private static final Color SLOT         = Color.rgb(12, 13, 16);
    private static final Color BORDER       = Color.rgb(50, 54, 64);
    private static final Color BORDER_HOT   = Color.rgb(96, 150, 210);
    private static final Color FG           = Color.rgb(172, 178, 192);

    private final Font font;

    public ChannelStrip(Font font) {
        this.font = font;
    }

    public float height(float scale) {
        return 66f * scale;
    }

    /** Draw the four slots for {@code channels}; cycle a slot's source on click. */
    public void draw(Renderer2D g, Input in, float x, float y, float w, float scale,
                     Channel[] channels, Project project) {
        float h = height(scale);
        float pad = 8f * scale;
        float gap = 8f * scale;
        float fontPx = 11f * scale;
        float labelH = g.lineHeight(font, fontPx) + 2f * scale;
        float slotW = (w - 2 * pad - 3 * gap) / 4f;
        float thumbH = h - 2 * pad - labelH;

        g.pushClip(x, y, w, h);
        g.fillRect(x, y, w, h, BG);

        for (int i = 0; i < 4; i++) {
            float sx = x + pad + i * (slotW + gap);
            float sy = y + pad;
            boolean over = in.mouseX() >= sx && in.mouseX() < sx + slotW
                        && in.mouseY() >= sy && in.mouseY() < sy + thumbH;

            g.fillRect(sx, sy, slotW, thumbH, SLOT);
            var preview = project.previewTexture(channels[i]);
            if (preview != null) g.image(preview, sx, sy, slotW, thumbH);
            g.strokeRect(sx, sy, slotW, thumbH, Math.max(1f, scale), over ? BORDER_HOT : BORDER);

            g.text(font, "iCh" + i + "  " + channels[i].label(), sx, sy + thumbH + 2f * scale, fontPx, FG);

            if (over && in.mousePressed(MouseButton.LEFT)) {
                project.cycle(channels[i]);
            }
            if (over && in.mousePressed(MouseButton.RIGHT)) {
                project.releaseChannel(channels[i]);
            }
        }

        g.popClip();
    }

    /** Which slot (0..3) the point falls in, or -1 -- for routing a file drop to a
     *  channel. Uses the same column geometry as {@link #draw}. */
    public int slotAt(float x, float y, float w, float scale, float mx, float my) {
        float h = height(scale);
        if (my < y || my >= y + h) return -1;
        float pad = 8f * scale;
        float gap = 8f * scale;
        float slotW = (w - 2 * pad - 3 * gap) / 4f;
        for (int i = 0; i < 4; i++) {
            float sx = x + pad + i * (slotW + gap);
            if (mx >= sx && mx < sx + slotW) return i;
        }
        return -1;
    }
}

