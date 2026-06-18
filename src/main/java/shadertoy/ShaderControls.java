package shadertoy;

import java.util.Locale;

import jvre.core.Color;
import jvre.core.Font;
import jvre.core.Input;
import jvre.core.Renderer2D;

/**
 * The playback controls that overlay the bottom of the shader pane: Play/Pause,
 * Restart, and a time + fps readout. Immediate-mode like {@link TopBar} -- {@link
 * #draw} renders the strip and returns what was clicked.
 */
public final class ShaderControls {

    public enum Action { NONE, TOGGLE_PAUSE, RESTART }

    private static final Color BG = Color.rgba(16, 17, 22, 205);
    private static final Color FG = Color.rgb(190, 196, 208);

    private final Font font;

    public ShaderControls(Font font) {
        this.font = font;
    }

    public float height(float scale) {
        return 30f * scale;
    }

    /** Draw the strip along the bottom of the shader pane and handle its buttons. */
    public Action draw(Renderer2D g, Input in, float x, float paneBottom, float w, float scale,
                       boolean paused, float time, float fps) {
        float h = height(scale);
        float y = paneBottom - h;
        float fontPx = 13f * scale;
        float pad = 10f * scale;
        float gap = 6f * scale;
        float bh = h - 8f * scale;
        float by = y + 4f * scale;

        g.pushClip(x, y, w, h);
        g.fillRect(x, y, w, h, BG);

        Action action = Action.NONE;
        float bx = x + pad;

        // Pause/Play -- sized for the wider label so it doesn't jump when toggling.
        float playW = Ui.buttonWidth(g, font, "Pause", fontPx, scale);
        if (Ui.button(g, in, font, paused ? "Play" : "Pause", bx, by, playW, bh, fontPx)) {
            action = Action.TOGGLE_PAUSE;
        }
        bx += playW + gap;

        float restartW = Ui.buttonWidth(g, font, "Restart", fontPx, scale);
        if (Ui.button(g, in, font, "Restart", bx, by, restartW, bh, fontPx)) {
            action = Action.RESTART;
        }
        bx += restartW + pad;

        String info = String.format(Locale.ROOT, "%.2fs   %.0f fps", time, fps);
        g.text(font, info, bx, y + (h - g.lineHeight(font, fontPx)) * 0.5f, fontPx, FG);

        g.popClip();
        return action;
    }
}
