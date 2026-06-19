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
    private float wheelAccum;     // mouse-wheel notches gathered this frame
    private float blink;          // caret blink phase, in seconds
    private boolean dragging;     // left button held after pressing in the pane
    private int visibleLines = 1; // computed during render, used by PageUp/Down

    // Soft-wrap layout cache (recomputed when revision or charsPerRow changes).
    private int[] lineVRStart;    // visual row where logical line L begins
    private int totalVR;          // total visual rows
    private int layoutRev  = -1;
    private int layoutCPR  = 0;   // charsPerRow used for the cached layout
    private float cachedCharW;    // monospace character width at the last layout's fontPx

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

        float gutterW   = g.textWidth(font, Integer.toString(lineCount), fontPx) + 2 * pad;
        float textX     = vx + gutterW + pad;
        float top       = vy + pad;
        float viewH     = vh - 2 * pad;
        float textAreaW = vw - gutterW - 2 * pad;
        // JetBrains Mono is monospace: all glyphs have the same advance width.
        float charW     = g.textWidth(font, "M", fontPx);
        int   cpr       = Math.max(1, (int) (textAreaW / charW));  // chars per visual row
        visibleLines = Math.max(1, (int) (viewH / lh));

        computeLayout(cpr, charW);

        // Caret visual row (for scroll tracking and drawing).
        int caretLine  = doc.lineOfOffset(doc.caret());
        int caretCol   = doc.caret() - doc.lineStart(caretLine);
        int caretVR    = lineVRStart[caretLine] + caretCol / layoutCPR;
        float caretTop = caretVR * lh;
        if (caretTop < scrollY) scrollY = caretTop;
        if (caretTop + lh > scrollY + viewH) scrollY = caretTop + lh - viewH;
        scrollY = Math.max(0f, Math.min(scrollY, Math.max(0f, totalVR * lh - viewH)));

        g.pushClip(vx, vy, vw, vh);
        g.fillRect(vx, vy, vw, vh, BG);
        g.fillRect(vx, vy, gutterW, vh, GUTTER_BG);

        int firstVR = Math.max(0, (int) (scrollY / lh));
        int lastVR  = Math.min(totalVR - 1, firstVR + visibleLines + 1);
        int selS = doc.selStart(), selE = doc.selEnd();
        boolean hasSel = doc.hasSelection();

        for (int vr = firstVR; vr <= lastVR; vr++) {
            int line   = lineForVR(vr);
            int chunk  = vr - lineVRStart[line];
            int ls     = doc.lineStart(line), le = doc.lineEnd(line);
            int vrS    = ls + chunk * layoutCPR;          // doc offset of first char in this row
            int vrE    = Math.min(le, vrS + layoutCPR);   // doc offset past last char
            float lineY = top + vr * lh - scrollY;

            if (hasSel) {
                int a = Math.max(selS, vrS), b = Math.min(selE, vrE);
                if (b > a) {
                    g.fillRect(textX + (a - vrS) * charW, lineY,
                               Math.max(2f, (b - a) * charW), lh, SELECTION);
                }
                // Trailing nub for the newline at the end of this logical line.
                if (vrE == le && le < content.length() && selS <= le && selE > le) {
                    g.fillRect(textX + (le - vrS) * charW, lineY, fontPx * 0.4f, lh, SELECTION);
                }
            }

            // Line number only on the first visual row of each logical line.
            if (chunk == 0) {
                String num = Integer.toString(line + 1);
                Color numColor = errorLines.contains(line) ? ERROR_FG : GUTTER_FG;
                g.text(font, num, vx + gutterW - pad - g.textWidth(font, num, fontPx),
                       lineY, fontPx, numColor);
            }

            // Syntax-highlighted text as colored runs.
            float rx = textX;
            int ci = vrS;
            while (ci < vrE) {
                Color hc = hl[ci];
                int cj = ci + 1;
                while (cj < vrE && hl[cj] == hc) cj++;
                g.text(font, content.substring(ci, cj), rx, lineY, fontPx, hc);
                rx += (cj - ci) * charW;   // monospace: multiply instead of measuring
                ci = cj;
            }
        }

        // Blinking caret.
        if ((int) (blink * 2f) % 2 == 0) {
            float cx = textX + (caretCol % layoutCPR) * charW;
            float cy = top + caretVR * lh - scrollY;
            g.fillRect(cx, cy, Math.max(1.5f, scale * 1.5f), lh, CARET);
        }

        g.popClip();
    }

    /** Recompute the visual-row layout if the document or column count changed. */
    private void computeLayout(int cpr, float charW) {
        int rev = doc.revision();
        if (cpr == layoutCPR && rev == layoutRev && lineVRStart != null) return;
        int n = doc.lineCount();
        lineVRStart = new int[n];
        int vr = 0;
        for (int i = 0; i < n; i++) {
            lineVRStart[i] = vr;
            int len = doc.lineEnd(i) - doc.lineStart(i);
            vr += Math.max(1, (int) Math.ceil((double) len / cpr));
        }
        totalVR    = vr;
        layoutRev  = rev;
        layoutCPR  = cpr;
        cachedCharW = charW;
    }

    /** Binary search: which logical line contains visual row {@code vr}. */
    private int lineForVR(int vr) {
        int lo = 0, hi = lineVRStart.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lineVRStart[mid] <= vr) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    // ---- helpers ---------------------------------------------------------------

    /** The document offset under a pixel position, for mouse caret placement. */
    private int offsetAt(Renderer2D g, float mx, float my) {
        float scale   = uiScale();
        float fontPx  = baseFontSize * scale;
        float pad     = PAD * scale;
        float lh      = g.lineHeight(font, fontPx);
        float gutterW = g.textWidth(font, Integer.toString(doc.lineCount()), fontPx) + 2 * pad;
        float textX   = vx + gutterW + pad;
        float top     = vy + pad;
        float cw      = cachedCharW > 0 ? cachedCharW : g.textWidth(font, "M", fontPx);
        int   cpr     = layoutCPR   > 0 ? layoutCPR   : 1;

        if (lineVRStart == null || totalVR == 0) return 0;
        int vr   = Math.max(0, Math.min(totalVR - 1, (int) ((my - top + scrollY) / lh)));
        int line = lineForVR(vr);
        int chunk = vr - lineVRStart[line];
        int ls    = doc.lineStart(line), le = doc.lineEnd(line);
        int vrS   = ls + chunk * cpr;
        int vrE   = Math.min(le, vrS + cpr);

        float target = mx - textX;
        if (target <= 0f) return vrS;
        int col = Math.min(vrE - vrS, Math.max(0, (int) Math.round(target / cw)));
        return vrS + col;
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
