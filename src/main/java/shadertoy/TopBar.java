package shadertoy;

import jvre.core.Color;
import jvre.core.Font;
import jvre.core.Input;
import jvre.core.Renderer2D;

/**
 * The application's top bar: a strip of chrome across the top of the window holding
 * the title, the zoom readout, and app-level action buttons. Immediate-mode -- {@link
 * #draw} renders the bar and reports which button (if any) was clicked. New actions
 * (Open, Save, Export, examples) drop in here as the features behind them land.
 */
public final class TopBar {

    /** What the user clicked this frame. */
    public enum Action { NONE, RESET }

    private static final Color BG     = Color.rgb(30, 33, 40);
    private static final Color BORDER  = Color.rgb(46, 50, 60);
    private static final Color TITLE   = Color.rgb(150, 200, 235);
    private static final Color FG      = Color.rgb(200, 206, 218);

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
        x -= pad;

        // App actions (only what isn't already an editor shortcut). More land here as
        // the features behind them do: Open/Save/Export, examples.
        float bw = Ui.buttonWidth(g, font, "Reset", fontPx, scale);
        float bh = barH - 10f * scale;
        if (Ui.button(g, in, font, "Reset", x - bw, (barH - bh) * 0.5f, bw, bh, fontPx)) {
            action = Action.RESET;
        }

        return action;
    }
}
