package shadertoy;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The editable text buffer behind the shader editor. Every mutation goes through a
 * single primitive -- {@link #apply(Edit)} -- so undo/redo is just recording each
 * edit and replaying its inverse. That is the whole point of this design: undo is not
 * bolted on, it falls out of how edits are represented.
 *
 * Offsets are character indices into the full text. A "line" is the run between
 * newlines. The selection is the half-open range [min(anchor, caret),
 * max(anchor, caret)); when {@code anchor == caret} there is no selection.
 *
 * Consecutive single-character typing is coalesced into one undo step (so Ctrl+Z
 * removes a typed run, not one letter at a time); any other action -- delete, caret
 * move, paste, newline -- ends the run.
 */
public final class Document {

    /** A replacement: drop text[offset, offset+removed.length()) and put
     *  {@code inserted} there. The inverse simply swaps removed and inserted. */
    private record Edit(int offset, String removed, String inserted) {
        Edit inverse() { return new Edit(offset, inserted, removed); }
    }

    /** One undo unit: the edit plus where the caret sat before and after it. */
    private record UndoStep(Edit edit, int caretBefore, int caretAfter) {}

    private final StringBuilder text;
    private int caret;
    private int anchor;       // selection origin; == caret when nothing is selected
    private int revision;     // bumped on every applied edit -- a cheap change flag

    private final Deque<UndoStep> undo = new ArrayDeque<>();
    private final Deque<UndoStep> redo = new ArrayDeque<>();
    private boolean coalescing;   // is the top undo step an open typing run?

    public Document(String initial) {
        this.text = new StringBuilder(initial);
        this.caret = this.anchor = text.length();
    }

    // ---- queries ---------------------------------------------------------------

    public String text() { return text.toString(); }
    public int length() { return text.length(); }
    public int caret() { return caret; }
    public int revision() { return revision; }
    public boolean hasSelection() { return caret != anchor; }
    public int selStart() { return Math.min(caret, anchor); }
    public int selEnd() { return Math.max(caret, anchor); }
    public String selectedText() { return text.substring(selStart(), selEnd()); }

    public int lineCount() {
        int n = 1;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') n++;
        return n;
    }

    public int lineOfOffset(int off) {
        int line = 0;
        int end = Math.min(off, text.length());
        for (int i = 0; i < end; i++) if (text.charAt(i) == '\n') line++;
        return line;
    }

    public int lineStart(int line) {
        if (line <= 0) return 0;
        int seen = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n' && ++seen == line) return i + 1;
        }
        return text.length();
    }

    public int lineEnd(int line) {
        int start = lineStart(line);
        int nl = text.indexOf("\n", start);
        return nl < 0 ? text.length() : nl;
    }

    // ---- mutations (each builds one Edit) --------------------------------------

    /** Insert text at the caret, replacing the selection if there is one. */
    public void insert(String s) {
        if (s.isEmpty()) return;
        boolean typingRun = s.length() == 1 && s.charAt(0) != '\n' && !hasSelection();
        pushEdit(selStart(), selectedText(), s, typingRun);
    }

    /** Backspace: delete the selection, else the character before the caret. */
    public void deleteBackward() {
        if (hasSelection()) { deleteSelection(); return; }
        if (caret == 0) return;
        pushEdit(caret - 1, text.substring(caret - 1, caret), "", false);
    }

    /** Forward-delete: delete the selection, else the character after the caret. */
    public void deleteForward() {
        if (hasSelection()) { deleteSelection(); return; }
        if (caret == text.length()) return;
        pushEdit(caret, text.substring(caret, caret + 1), "", false);
    }

    private void deleteSelection() {
        pushEdit(selStart(), selectedText(), "", false);
    }

    // ---- caret movement --------------------------------------------------------

    public void moveLeft(boolean extend) {
        if (!extend && hasSelection()) { setCaret(selStart(), false); return; }
        setCaret(caret - 1, extend);
    }

    public void moveRight(boolean extend) {
        if (!extend && hasSelection()) { setCaret(selEnd(), false); return; }
        setCaret(caret + 1, extend);
    }

    /** Move the caret by {@code delta} lines, keeping the column where possible. */
    public void moveVertical(int delta, boolean extend) {
        int line = lineOfOffset(caret);
        int col = caret - lineStart(line);
        int target = clampLine(line + delta);
        setCaret(Math.min(lineStart(target) + col, lineEnd(target)), extend);
    }

    public void moveHome(boolean extend) { setCaret(lineStart(lineOfOffset(caret)), extend); }
    public void moveEnd(boolean extend)  { setCaret(lineEnd(lineOfOffset(caret)), extend); }

    public void moveWordLeft(boolean extend) {
        int p = caret;
        while (p > 0 && isSeparator(text.charAt(p - 1))) p--;
        while (p > 0 && !isSeparator(text.charAt(p - 1))) p--;
        setCaret(p, extend);
    }

    public void moveWordRight(boolean extend) {
        int n = text.length(), p = caret;
        while (p < n && isSeparator(text.charAt(p))) p++;
        while (p < n && !isSeparator(text.charAt(p))) p++;
        setCaret(p, extend);
    }

    public void selectAll() {
        coalescing = false;
        anchor = 0;
        caret = text.length();
    }

    /** Place the caret at an absolute offset (for mouse clicks). With {@code extend}
     *  the selection anchor is kept; without it the selection collapses. */
    public void placeCaret(int offset, boolean extend) {
        setCaret(offset, extend);
    }

    // ---- undo / redo -----------------------------------------------------------

    public void undo() {
        coalescing = false;
        if (undo.isEmpty()) return;
        UndoStep step = undo.pop();
        apply(step.edit().inverse());
        caret = anchor = step.caretBefore();
        redo.push(step);
    }

    public void redo() {
        coalescing = false;
        if (redo.isEmpty()) return;
        UndoStep step = redo.pop();
        apply(step.edit());
        caret = anchor = step.caretAfter();
        undo.push(step);
    }

    // ---- internals -------------------------------------------------------------

    /** Record and apply one user edit, coalescing contiguous typing into the
     *  previous step when asked. */
    private void pushEdit(int offset, String removed, String inserted, boolean typingRun) {
        int caretBefore = caret;
        Edit e = new Edit(offset, removed, inserted);
        apply(e);
        redo.clear();

        if (typingRun && coalescing && !undo.isEmpty()) {
            UndoStep top = undo.peek();
            Edit t = top.edit();
            // Only merge contiguous pure inserts: ...[prev insert][this char].
            if (t.removed().isEmpty() && removed.isEmpty()
                    && t.offset() + t.inserted().length() == offset) {
                undo.pop();
                undo.push(new UndoStep(
                        new Edit(t.offset(), "", t.inserted() + inserted),
                        top.caretBefore(), caret));
                return;
            }
        }
        undo.push(new UndoStep(e, caretBefore, caret));
        coalescing = typingRun;
    }

    /** The one place the text actually changes. Leaves the caret after the insert. */
    private void apply(Edit e) {
        text.replace(e.offset(), e.offset() + e.removed().length(), e.inserted());
        caret = anchor = e.offset() + e.inserted().length();
        revision++;
    }

    private void setCaret(int pos, boolean extend) {
        coalescing = false;
        caret = clamp(pos);
        if (!extend) anchor = caret;
    }

    private int clamp(int p) { return p < 0 ? 0 : Math.min(p, text.length()); }
    private int clampLine(int l) { return l < 0 ? 0 : Math.min(l, lineCount() - 1); }

    private static boolean isSeparator(char c) {
        return !(Character.isLetterOrDigit(c) || c == '_');
    }
}
