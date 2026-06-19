package shadertoy;

import java.util.Set;

import jvre.core.Color;
import jvre.core.Font;
import jvre.core.Input;
import jvre.core.Key;
import jvre.core.MouseButton;
import jvre.core.Renderer2D;
import jvre.core.Window;

/**
 * The code editor pane: a {@link Document} plus everything needed to drive and draw
 * it -- keyboard input (typing, navigation, selection, clipboard, undo/redo), mouse
 * click-to-place and drag-select, a blinking caret, a line-number gutter, selection
 * highlighting, and vertical scroll that follows the caret.
 *
 * The host sets the pane rectangle and a UI scale each frame via {@link #setViewport}
 * before calling {@link #handleInput} and {@link #render}; all sizes (font, padding,
 * caret) are multiplied by that scale so the editor can be DPI- or size-aware.
 * Drawing is clipped to the pane via {@link Renderer2D#pushClip}.
 */
public final class Editor {

    private static final float PAD = 10f;   // base padding, multiplied by scale

    private static final Color BG        = Color.rgb(24, 26, 31);
    private static final Color GUTTER_BG  = Color.rgb(20, 22, 26);
    private static final Color GUTTER_FG  = Color.rgb(92, 98, 112);
    private static final Color CARET      = Color.rgb(232, 235, 242);
    private static final Color SELECTION  = Color.rgba(72, 104, 176, 95);
    private static final Color ERROR_FG   = Color.rgb(240, 92, 92);

    private Document doc;          // the pass currently being edited (swapped on tab change)
    private final Font font;
    private final float baseFontSize;
    private final KeyRepeat repeat = new KeyRepeat();
    private final GlslHighlighter highlighter = new GlslHighlighter();

    // Viewport (pane rect) and scale. dpiScale is set by the host each frame; zoom is
    // the user's Ctrl+wheel adjustment. The effective UI scale is their product.
    private float vx, vy, vw, vh;
    private float dpiScale = 1f;
    private float zoom = 1f;

    private float scrollY;        // pixels scrolled down from the top
    private float scrollX;        // pixels scrolled right (caret-follow, no wheel)
    private float wheelAccum;     // mouse-wheel notches gathered this frame
    private float blink;          // caret blink phase, in seconds
    private boolean dragging;     // left button held after pressing in the pane
    private int visibleLines = 1; // computed during render, used by PageUp/Down

    public Editor(Font font, float baseFontSize, Document initial) {
        this.font = font;
        this.baseFontSize = baseFontSize;
        this.doc = initial;
    }

    public String text() {
        return doc.text();
    }

    /** Switch which document the editor is editing (on a tab change). Resets the view
     *  but not the document's own caret/undo, which belong to the pass. No-op if it is
     *  already the current document, so the host can call this every frame safely. */
    public void setDocument(Document d) {
        if (d == doc) return;
        doc = d;
        scrollY = 0f;
        scrollX = 0f;
        blink = 0f;
        dragging = false;
    }

    /** Current zoom as a percentage (for the top bar's readout). */
    public int zoomPercent() {
        return Math.round(zoom * 100f);
    }

    /** Replace the entire document with new source (drag-drop import, Reset). Done as
     *  one edit so it is a single Ctrl+Z away from the previous content. */
    public void loadSource(String s) {
        doc.selectAll();
        if (s.isEmpty()) doc.deleteBackward();
        else doc.insert(s);
    }

    /** Pane rectangle and DPI scale for this frame. Call before handleInput/render. */
    public void setViewport(float x, float y, float w, float h, float dpiScale) {
        this.vx = x; this.vy = y; this.vw = w; this.vh = h;
        this.dpiScale = dpiScale;
    }

    /** Effective UI scale: monitor DPI times the user's Ctrl+wheel zoom. */
    private float uiScale() {
        return dpiScale * zoom;
    }

    // ---- input -----------------------------------------------------------------

    /** Process one frame of input. Returns true if the text changed (so the host can
     *  schedule a recompile). */
    public boolean handleInput(Window window, Input in, float dt, Renderer2D g) {
        int rev = doc.revision();
        int caretBefore = doc.caret();
        boolean ctrl = in.keyDown(Key.LEFT_CONTROL) || in.keyDown(Key.RIGHT_CONTROL);
        boolean shift = in.keyDown(Key.LEFT_SHIFT) || in.keyDown(Key.RIGHT_SHIFT);

        blink += dt;

        // Mouse wheel: Ctrl+wheel zooms the editor, otherwise it scrolls. The step is
        // clamped so a high-resolution wheel can't snap zoom straight to the limit.
        float wheel = in.scrollY();
        if (ctrl && wheel != 0f) {
            float step = Math.max(-3f, Math.min(3f, wheel));
            zoom = Math.max(0.4f, Math.min(4f, zoom * (float) Math.pow(1.12f, step)));
        } else {
            wheelAccum += wheel;
        }

        // ---- mouse: click to place caret, drag to select ----
        float mx = in.mouseX(), my = in.mouseY();
        boolean overPane = vw > 0 && mx >= vx && mx < vx + vw && my >= vy && my < vy + vh;
        if (in.mousePressed(MouseButton.LEFT) && overPane) {
            dragging = true;
            doc.placeCaret(offsetAt(g, mx, my), shift);
        } else if (dragging && in.mouseDown(MouseButton.LEFT)) {
            doc.placeCaret(offsetAt(g, mx, my), true);
        }
        if (in.mouseReleased(MouseButton.LEFT)) dragging = false;

        // ---- keyboard ----
        if (ctrl) {
            if (in.keyPressed(Key.A)) doc.selectAll();
            if (in.keyPressed(Key.C) && doc.hasSelection()) window.setClipboard(doc.selectedText());
            if (in.keyPressed(Key.X) && doc.hasSelection()) {
                window.setClipboard(doc.selectedText());
                doc.deleteBackward();
            }
            if (in.keyPressed(Key.V)) {
                String p = window.clipboard();
                if (p != null && !p.isEmpty()) doc.insert(p.replace("\r\n", "\n").replace('\r', '\n'));
            }
            if (in.keyPressed(Key.Z)) { if (shift) doc.redo(); else doc.undo(); }
            if (in.keyPressed(Key.Y)) doc.redo();
            if (in.keyPressed(Key.NUM_0)) zoom = 1f;   // Ctrl+0 resets zoom
        } else {
            String typed = stripControl(in.typedChars());
            if (!typed.isEmpty()) doc.insert(typed);
        }

        // Editing keys (auto-repeating when held).
        if (repeat.fire(in, Key.ENTER, dt)) doc.insert("\n" + currentIndent());
        if (!ctrl && repeat.fire(in, Key.TAB, dt)) doc.insert("    ");
        if (repeat.fire(in, Key.BACKSPACE, dt)) doc.deleteBackward();
        if (repeat.fire(in, Key.DELETE, dt)) doc.deleteForward();

        // Navigation (Ctrl makes Left/Right jump by word).
        if (repeat.fire(in, Key.LEFT, dt))  { if (ctrl) doc.moveWordLeft(shift); else doc.moveLeft(shift); }
        if (repeat.fire(in, Key.RIGHT, dt)) { if (ctrl) doc.moveWordRight(shift); else doc.moveRight(shift); }
        if (repeat.fire(in, Key.UP, dt))    doc.moveVertical(-1, shift);
        if (repeat.fire(in, Key.DOWN, dt))  doc.moveVertical(1, shift);
        if (repeat.fire(in, Key.HOME, dt))  doc.moveHome(shift);
        if (repeat.fire(in, Key.END, dt))   doc.moveEnd(shift);
        if (repeat.fire(in, Key.PAGE_UP, dt))   doc.moveVertical(-visibleLines, shift);
        if (repeat.fire(in, Key.PAGE_DOWN, dt)) doc.moveVertical(visibleLines, shift);

        boolean changed = doc.revision() != rev;
        if (changed || doc.caret() != caretBefore) blink = 0f;   // show the caret right after activity
        return changed;
    }

    // ---- rendering -------------------------------------------------------------

    public void render(Renderer2D g, Set<Integer> errorLines) {
        float scale = uiScale();
        float fontPx = baseFontSize * scale;
        float pad = PAD * scale;
        float lh = g.lineHeight(font, fontPx);
        String content = doc.text();
        int lineCount = doc.lineCount();
        Color[] hl = highlighter.update(content, doc.revision());

        // Apply mouse-wheel scroll now that we know the line height.
        scrollY -= wheelAccum * lh * 3f;
        wheelAccum = 0f;

        float gutterW = g.textWidth(font, Integer.toString(lineCount), fontPx) + 2 * pad;
        float top = vy + pad;
        float viewH = vh - 2 * pad;
        float viewW = vw - gutterW - 2 * pad;
        visibleLines = Math.max(1, (int) (viewH / lh));

        // Keep the caret in view vertically, then clamp to document bounds.
        int caretLine = doc.lineOfOffset(doc.caret());
        float caretTop = caretLine * lh;
        if (caretTop < scrollY) scrollY = caretTop;
        if (caretTop + lh > scrollY + viewH) scrollY = caretTop + lh - viewH;
        scrollY = Math.max(0f, Math.min(scrollY, Math.max(0f, lineCount * lh - viewH)));

        // Keep the caret in view horizontally (caret-follow; no wheel binding needed).
        float caretCharX = g.textWidth(font, content.substring(doc.lineStart(caretLine), doc.caret()), fontPx);
        if (caretCharX < scrollX) scrollX = caretCharX;
        if (caretCharX > scrollX + viewW) scrollX = caretCharX - viewW + pad;
        scrollX = Math.max(0f, scrollX);

        float textX = vx + gutterW + pad - scrollX;

        g.pushClip(vx, vy, vw, vh);
        g.fillRect(vx, vy, vw, vh, BG);
        g.fillRect(vx, vy, gutterW, vh, GUTTER_BG);

        int first = Math.max(0, (int) (scrollY / lh));
        int last = Math.min(lineCount - 1, first + visibleLines + 1);
        int selS = doc.selStart(), selE = doc.selEnd();
        boolean hasSel = doc.hasSelection();

        for (int line = first; line <= last; line++) {
            float lineY = top + line * lh - scrollY;
            int ls = doc.lineStart(line), le = doc.lineEnd(line);

            if (hasSel) {
                int a = Math.max(selS, ls), b = Math.min(selE, le);
                if (b > a) {
                    float ax = textX + g.textWidth(font, content.substring(ls, a), fontPx);
                    float bx = textX + g.textWidth(font, content.substring(ls, b), fontPx);
                    g.fillRect(ax, lineY, Math.max(2f, bx - ax), lh, SELECTION);
                }
                // If the newline ending this line is selected, draw a trailing nub.
                if (le < content.length() && selS <= le && selE > le) {
                    float ex = textX + g.textWidth(font, content.substring(ls, le), fontPx);
                    g.fillRect(ex, lineY, fontPx * 0.4f, lh, SELECTION);
                }
            }

            String num = Integer.toString(line + 1);
            Color numColor = errorLines.contains(line) ? ERROR_FG : GUTTER_FG;
            g.text(font, num, vx + gutterW - pad - g.textWidth(font, num, fontPx), lineY, fontPx, numColor);

            // Draw line text as colored runs (syntax highlighting).
            float rx = textX;
            int ci = ls;
            while (ci < le) {
                Color hc = hl[ci];
                int cj = ci + 1;
                while (cj < le && hl[cj] == hc) cj++;
                String seg = content.substring(ci, cj);
                g.text(font, seg, rx, lineY, fontPx, hc);
                rx += g.textWidth(font, seg, fontPx);
                ci = cj;
            }
        }

        // Blinking caret: on for half a second, off for half a second.
        if ((int) (blink * 2f) % 2 == 0) {
            int cl = doc.lineOfOffset(doc.caret());
            int cls = doc.lineStart(cl);
            float cx = textX + g.textWidth(font, content.substring(cls, doc.caret()), fontPx);
            float cy = top + cl * lh - scrollY;
            g.fillRect(cx, cy, Math.max(1.5f, scale * 1.5f), lh, CARET);
        }

        g.popClip();
    }

    // ---- helpers ---------------------------------------------------------------

    /** The document offset under a pixel position, for mouse caret placement. */
    private int offsetAt(Renderer2D g, float mx, float my) {
        float scale = uiScale();
        float fontPx = baseFontSize * scale;
        float pad = PAD * scale;
        float lh = g.lineHeight(font, fontPx);
        float gutterW = g.textWidth(font, Integer.toString(doc.lineCount()), fontPx) + 2 * pad;
        float textX = vx + gutterW + pad - scrollX;
        float top = vy + pad;

        int line = (int) Math.floor((my - top + scrollY) / lh);
        line = Math.max(0, Math.min(line, doc.lineCount() - 1));
        int ls = doc.lineStart(line), le = doc.lineEnd(line);
        String lineStr = doc.text().substring(ls, le);

        float target = mx - textX;
        if (target <= 0f) return ls;
        int col = lineStr.length();
        float prevW = 0f;
        for (int i = 1; i <= lineStr.length(); i++) {
            float w = g.textWidth(font, lineStr.substring(0, i), fontPx);
            if (target < (prevW + w) * 0.5f) { col = i - 1; break; }
            prevW = w;
        }
        return ls + col;
    }

    /** The leading whitespace of the caret's current line -- copied on Enter so a new
     *  line keeps the indentation of the one above. */
    private String currentIndent() {
        String t = doc.text();
        int line = doc.lineOfOffset(doc.caret());
        int i = doc.lineStart(line);
        int end = doc.caret();
        int j = i;
        while (j < end && (t.charAt(j) == ' ' || t.charAt(j) == '\t')) j++;
        return t.substring(i, j);
    }

    private static String stripControl(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c != 0x7f) b.append(c);
        }
        return b.toString();
    }
}
