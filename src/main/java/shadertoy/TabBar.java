package shadertoy;

import java.util.List;

import jvre.core.Color;
import jvre.core.Font;
import jvre.core.Input;
import jvre.core.MouseButton;
import jvre.core.Renderer2D;

/**
 * The pass tab strip above the editor: one tab per {@link Pass} (Common, Image,
 * Buffer A-D). Immediate-mode -- {@link #draw} renders the tabs and returns the index
 * clicked this frame, or -1.
 */
public final class TabBar {

    private static final Color BG          = Color.rgb(18, 20, 25);
    private static final Color TAB          = Color.rgb(28, 31, 38);
    private static final Color TAB_ACTIVE   = Color.rgb(24, 26, 31);   // matches editor bg
    private static final Color TAB_HOT      = Color.rgb(40, 44, 54);
    private static final Color FG           = Color.rgb(205, 210, 220);
    private static final Color FG_DIM       = Color.rgb(128, 134, 148);
    private static final Color ACCENT       = Color.rgb(150, 200, 235);

    private final Font font;

    public TabBar(Font font) {
        this.font = font;
    }

    public float height(float scale) {
        return 28f * scale;
    }

    /** Draw the tabs across {@code (x, y, w)} and return the index clicked, or -1. */
    public int draw(Renderer2D g, Input in, float x, float y, float w, float scale,
                    List<Pass> tabs, int active) {
        float h = height(scale);
        float fontPx = 13f * scale;
        float padX = 14f * scale;

        g.pushClip(x, y, w, h);
        g.fillRect(x, y, w, h, BG);

        int clicked = -1;
        float tx = x;
        for (int i = 0; i < tabs.size(); i++) {
            String label = tabs.get(i).name();
            float tw = g.textWidth(font, label, fontPx) + 2 * padX;
            boolean isActive = i == active;
            boolean over = in.mouseX() >= tx && in.mouseX() < tx + tw
                        && in.mouseY() >= y && in.mouseY() < y + h;

            g.fillRect(tx, y, tw, h, isActive ? TAB_ACTIVE : over ? TAB_HOT : TAB);
            if (isActive) g.fillRect(tx, y + h - 2f * scale, tw, 2f * scale, ACCENT);
            g.text(font, label, tx + padX, y + (h - g.lineHeight(font, fontPx)) * 0.5f, fontPx,
                    isActive ? FG : FG_DIM);

            if (over && in.mousePressed(MouseButton.LEFT)) clicked = i;
            tx += tw + 1f * scale;
        }

        g.popClip();
        return clicked;
    }
}
