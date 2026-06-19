package shadertoy;

import jvre.core.Color;
import jvre.core.Font;
import jvre.core.Input;
import jvre.core.MouseButton;
import jvre.core.Renderer2D;

/**
 * The application's top bar: title, zoom readout, and app-level action buttons
 * (Load, Save, Reset, Examples). Immediate-mode -- {@link #draw} renders the bar
 * and reports which button was clicked. The Examples button opens an inline dropdown.
 */
public final class TopBar {

    public enum Action { NONE, RESET, SAVE, LOAD, EXAMPLE, EXPORT }

    private static final Color BG          = Color.rgb( 30,  33,  40);
    private static final Color BG_DROP     = Color.rgb( 38,  42,  52);
    private static final Color BG_DROP_HOT = Color.rgb( 55,  62,  78);
    private static final Color BORDER      = Color.rgb( 46,  50,  60);
    private static final Color TITLE       = Color.rgb(150, 200, 235);
    private static final Color FG          = Color.rgb(200, 206, 218);

    private final Font font;

    private boolean examplesOpen = false;
    private int selectedExample  = 0;

    public TopBar(Font font) { this.font = font; }

    public float height(float scale) { return 34f * scale; }

    /** True while the Examples dropdown is open (host should suppress editor clicks). */
    public boolean isDropdownOpen() { return examplesOpen; }

    /** Index of the example last chosen from the dropdown (valid after {@link Action#EXAMPLE}). */
    public int selectedExample() { return selectedExample; }

    public Action draw(Renderer2D g, Input in, float w, float scale, int zoomPercent) {
        float barH   = height(scale);
        float fontPx = 15f * scale;
        float pad    = 12f * scale;
        float gap    = 6f * scale;
        float bh     = barH - 10f * scale;
        float by     = (barH - bh) * 0.5f;
        float textY  = (barH - g.lineHeight(font, fontPx)) * 0.5f;

        g.fillRect(0, 0, w, barH, BG);
        g.fillRect(0, barH - Math.max(1f, scale), w, Math.max(1f, scale), BORDER);
        g.text(font, "shadertoy", pad, textY, fontPx, TITLE);

        Action action = Action.NONE;
        float x = w - pad;

        // Zoom % (non-interactive, rightmost).
        String z = zoomPercent + "%";
        x -= g.textWidth(font, z, fontPx);
        g.text(font, z, x, textY, fontPx, FG);
        x -= pad;

        // Buttons right-to-left: Reset | Export | Load | Save | Examples
        float bwReset = Ui.buttonWidth(g, font, "Reset", fontPx, scale);
        if (Ui.button(g, in, font, "Reset", x - bwReset, by, bwReset, bh, fontPx)) action = Action.RESET;
        x -= bwReset + gap;

        float bwExport = Ui.buttonWidth(g, font, "Export", fontPx, scale);
        if (Ui.button(g, in, font, "Export", x - bwExport, by, bwExport, bh, fontPx)) action = Action.EXPORT;
        x -= bwExport + gap;

        float bwLoad = Ui.buttonWidth(g, font, "Load", fontPx, scale);
        if (Ui.button(g, in, font, "Load", x - bwLoad, by, bwLoad, bh, fontPx)) action = Action.LOAD;
        x -= bwLoad + gap;

        float bwSave = Ui.buttonWidth(g, font, "Save", fontPx, scale);
        if (Ui.button(g, in, font, "Save", x - bwSave, by, bwSave, bh, fontPx)) action = Action.SAVE;
        x -= bwSave + gap;

        float bwEx   = Ui.buttonWidth(g, font, "Examples", fontPx, scale);
        float exBtnX = x - bwEx;
        if (Ui.button(g, in, font, "Examples", exBtnX, by, bwEx, bh, fontPx)) {
            examplesOpen = !examplesOpen;
        }

        // Dropdown panel (drawn on top of everything else since TopBar draws last).
        if (examplesOpen) {
            float rowH = barH;
            float ddW  = bwEx;
            for (var ex : Examples.SHADERS)
                ddW = Math.max(ddW, g.textWidth(font, ex.name(), fontPx) + 2 * pad);
            float ddX = exBtnX;
            float ddY = barH;
            float ddH = rowH * Examples.SHADERS.length;

            g.fillRect(ddX, ddY, ddW, ddH, BG_DROP);
            g.fillRect(ddX, ddY + ddH, ddW, Math.max(1f, scale), BORDER);

            for (int i = 0; i < Examples.SHADERS.length; i++) {
                float ey  = ddY + i * rowH;
                boolean ov = in.mouseX() >= ddX && in.mouseX() < ddX + ddW
                          && in.mouseY() >= ey  && in.mouseY() < ey  + rowH;
                if (ov) g.fillRect(ddX, ey, ddW, rowH, BG_DROP_HOT);
                g.text(font, Examples.SHADERS[i].name(), ddX + pad, ey + textY, fontPx, FG);
                if (ov && in.mousePressed(MouseButton.LEFT)) {
                    selectedExample = i;
                    examplesOpen    = false;
                    action          = Action.EXAMPLE;
                }
            }

            // Click outside the dropdown (and not on the toggle button) closes it.
            if (action != Action.EXAMPLE && in.mousePressed(MouseButton.LEFT)) {
                boolean inDrop = in.mouseX() >= ddX && in.mouseX() < ddX + ddW
                              && in.mouseY() >= ddY && in.mouseY() < ddY + ddH;
                boolean inBtn  = in.mouseX() >= exBtnX && in.mouseX() < exBtnX + bwEx
                              && in.mouseY() >= by     && in.mouseY() < by + bh;
                if (!inDrop && !inBtn) examplesOpen = false;
            }
        }

        return action;
    }
}
