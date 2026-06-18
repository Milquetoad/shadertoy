package shadertoy;

import jvre.core.Color;
import jvre.core.Font;
import jvre.core.Input;
import jvre.core.MouseButton;
import jvre.core.Renderer2D;

/**
 * Tiny shared immediate-mode UI helpers, so the top bar and the shader controls draw
 * buttons the same way without duplicating the logic. Not a widget toolkit -- just
 * the one button we need.
 */
public final class Ui {

    private Ui() {}

    public static final Color BTN     = Color.rgb(48, 52, 64);
    public static final Color BTN_HOT = Color.rgb(68, 74, 92);
    public static final Color FG      = Color.rgb(205, 210, 220);

    /** Draw a labelled button in the given rect; returns true if clicked this frame. */
    public static boolean button(Renderer2D g, Input in, Font font, String label,
                                 float x, float y, float w, float h, float fontPx) {
        boolean over = in.mouseX() >= x && in.mouseX() < x + w
                    && in.mouseY() >= y && in.mouseY() < y + h;
        g.fillRoundedRect(x, y, w, h, Math.min(6f, h * 0.25f), over ? BTN_HOT : BTN);
        float tw = g.textWidth(font, label, fontPx);
        g.text(font, label, x + (w - tw) * 0.5f, y + (h - g.lineHeight(font, fontPx)) * 0.5f, fontPx, FG);
        return over && in.mousePressed(MouseButton.LEFT);
    }

    /** Width a button should be to fit {@code label} with comfortable side padding. */
    public static float buttonWidth(Renderer2D g, Font font, String label, float fontPx, float scale) {
        return g.textWidth(font, label, fontPx) + 16f * scale;
    }
}
