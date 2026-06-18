package shadertoy;

import jvre.core.Color;
import jvre.core.Font;
import jvre.core.Input;
import jvre.core.MouseButton;
import jvre.core.Renderer2D;

/**
 * The application's top bar: a strip of chrome across the top of the window holding
 * the title, action buttons, and the zoom readout. It is immediate-mode -- {@link
 * #draw} both renders the bar and reports which button (if any) was clicked this
 * frame, so the host can act on it. New actions (Save, Open, time controls) drop in
 * as more buttons here.
 */
public final class TopBar {

    /** What the user clicked this frame. */
    public enum Action { NONE, RESET }

    private static final Color BG       = Color.rgb(30, 33, 40);
    private static final Color BORDER    = Color.rgb(46, 50, 60);
    private static final Color TITLE     = Color.rgb(150, 200, 235);
    private static final Color FG        = Color.rgb(200, 206, 218);
    private static final Color BTN       = Color.rgb(48, 52, 64);
    private static final Color BTN_HOT   = Color.rgb(68, 74, 92);

    private final Font font;

    public TopBar(Font font) {
        this.font = font;
    }

    /** Bar height in pixels at the given UI scale. */
    public float height(float scale) {
        return 34f * scale;
    }

    /** Draw the bar across the top {@code w} pixels and handle its buttons. */
    public Action draw(Renderer2D g, Input in, float w, float scale, int zoomPercent) {
        float barH = height(scale);
        float fontPx = 15f * scale;
        float pad = 12f * scale;
        float gap = 8f * scale;
        float textY = (barH - g.lineHeight(font, fontPx)) * 0.5f;

        g.fillRect(0, 0, w, barH, BG);
        g.fillRect(0, barH - Math.max(1f, scale), w, Math.max(1f, scale), BORDER);
        g.text(font, "shadertoy", pad, textY, fontPx, TITLE);

        Action action = Action.NONE;
        float x = w - pad;

        // Zoom % (rightmost, not a button).
        String z = zoomPercent + "%";
        x -= g.textWidth(font, z, fontPx);
        g.text(font, z, x, textY, fontPx, FG);
        x -= pad + gap;

        // App actions (only what isn't already an editor shortcut). More land here as
        // the features behind them do: Open/Save/Export, time controls, examples.
        if (button(g, in, "Reset", x, barH, scale, fontPx)) action = Action.RESET;

        return action;
    }

    // Left edge of the most recently drawn button.
    private float leftEdge;

    /** Draw a button whose RIGHT edge is at {@code rightX}; record its left edge in
     *  {@link #leftEdge}. Returns true if clicked this frame. */
    private boolean button(Renderer2D g, Input in, String label, float rightX,
                           float barH, float scale, float fontPx) {
        float bw = g.textWidth(font, label, fontPx) + 18f * scale;
        float bh = barH - 10f * scale;
        float x = rightX - bw;
        float y = (barH - bh) * 0.5f;
        leftEdge = x;

        boolean over = in.mouseX() >= x && in.mouseX() < x + bw
                    && in.mouseY() >= y && in.mouseY() < y + bh;
        g.fillRoundedRect(x, y, bw, bh, 5f * scale, over ? BTN_HOT : BTN);
        float tw = g.textWidth(font, label, fontPx);
        g.text(font, label, x + (bw - tw) * 0.5f, (barH - g.lineHeight(font, fontPx)) * 0.5f, fontPx, FG);
        return over && in.mousePressed(MouseButton.LEFT);
    }
}
