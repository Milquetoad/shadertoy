package shadertoy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jvre.core.Buffer;
import jvre.core.Renderer;
import jvre.core.ShaderDiagnostic;
import jvre.core.Texture;

/**
 * A multipass shader project: the editable passes (Common, Image, Buffer A-D), their
 * GPU render passes, and the channel wiring that connects them. Each renderable pass
 * is compiled with the Common source prepended, so shared helpers are visible
 * everywhere.
 *
 * Each frame the render graph evaluates the buffers reachable from the Image pass --
 * in order A..D, double-buffered for feedback -- then the Image pass, sampling the
 * buffers' outputs. A buffer reads a previously-rendered buffer's current-frame result
 * and its own (or a not-yet-rendered buffer's) previous frame, matching Shadertoy.
 */
public final class Project {

    /** One mapped diagnostic: which tab, the 1-based line within that tab, the message. */
    public record ErrorMark(int tabIndex, int line, String message) {}

    private static final String DEFAULT_BUFFER = """
        void mainImage( out vec4 fragColor, in vec2 fragCoord )
        {
            fragColor = vec4(0.0);
        }
        """;

    /** A pass that renders: its document, GPU pass, channel assignments, last errors. */
    private static final class Renderable {
        final Pass pass;
        final RenderPass gpu;
        final Channel[] channels = { new Channel(), new Channel(), new Channel(), new Channel() };
        List<ShaderDiagnostic> errors = List.of();
        Renderable(Pass pass, RenderPass gpu) { this.pass = pass; this.gpu = gpu; }
    }

    private static final int BUFFER_COUNT = 4;

    private final Buffer triangle;        // shared fullscreen triangle
    private final Texture dummy;          // 1x1, bound to unassigned channels

    private final Pass common;
    private final List<Renderable> renderables = new ArrayList<>();   // 0 = Image, 1..4 = Buffer A..D
    private final List<Pass> tabs = new ArrayList<>();
    private int active = 1;   // start on the Image tab
    private int paneW, paneH; // current pane size; buffers size up to this on use

    public Project(Renderer renderer, int paneW, int paneH, String defaultImageSource) {
        this.triangle = renderer.createVertexBuffer(new float[] { -1f, -1f, 3f, -1f, -1f, 3f });
        this.dummy = renderer.createImage(new byte[] { 0, 0, 0, (byte) 255 }, 1, 1);
        this.paneW = Math.max(1, paneW);
        this.paneH = Math.max(1, paneH);

        common = new Pass("Common", Pass.Kind.COMMON, new Document(""));
        tabs.add(common);

        Pass imagePass = new Pass("Image", Pass.Kind.IMAGE, new Document(defaultImageSource));
        RenderPass imageGpu = new RenderPass(renderer, triangle, paneW, paneH, false, false, combined(imagePass));
        renderables.add(new Renderable(imagePass, imageGpu));
        tabs.add(imagePass);

        // Buffers start tiny and only size up to the pane when first used, so unused
        // buffers don't hold full-resolution float ping-pong targets.
        for (int b = 0; b < BUFFER_COUNT; b++) {
            Pass bp = new Pass("Buf " + (char) ('A' + b), Pass.Kind.BUFFER, new Document(DEFAULT_BUFFER.stripTrailing()));
            RenderPass bg = new RenderPass(renderer, triangle, 16, 16, true, true, combined(bp));
            renderables.add(new Renderable(bp, bg));
            tabs.add(bp);
        }
    }

    private Renderable image() { return renderables.get(0); }
    private Renderable buffer(int i) { return renderables.get(1 + i); }

    public int bufferCount() { return BUFFER_COUNT; }
    public List<Pass> tabs() { return tabs; }
    public int activeIndex() { return active; }
    public void setActive(int i) { if (i >= 0 && i < tabs.size()) active = i; }
    public Document activeDocument() { return tabs.get(active).doc(); }

    /** Channels of the active pass, or null on the Common tab (no channels). */
    public Channel[] activeChannels() {
        return active == 0 ? null : renderables.get(active - 1).channels;
    }

    /** The texture to preview for a channel's source (its buffer output), or null. */
    public Texture previewTexture(Channel ch) {
        return ch.isBuffer() ? buffer(ch.bufferIndex()).gpu.output() : null;
    }

    /** Reset the Image pass to a default source (Reset button) and switch to it. */
    public void resetImage(String source) {
        Document d = image().pass.doc();
        d.selectAll();
        d.insert(source.isEmpty() ? "\n" : source);
        active = 1;
    }

    /** Recompile the passes affected by an edit on the active tab: just that pass,
     *  unless Common changed (which affects every pass). */
    public void reloadActive() {
        if (active == 0) {
            for (Renderable r : renderables) r.errors = r.gpu.reload(combined(r.pass));
        } else {
            Renderable r = renderables.get(active - 1);
            r.errors = r.gpu.reload(combined(r.pass));
        }
    }

    public void resize(int w, int h) {
        paneW = Math.max(1, w);
        paneH = Math.max(1, h);
        image().gpu.resize(paneW, paneH);   // reachable buffers size up lazily in render()
    }

    /** Evaluate the whole render graph for this frame. */
    public void render(float[] uniforms) {
        boolean[] reachable = reachableBuffers();

        // Buffers in order A..D, into their back targets (sizing up to the pane on
        // first use).
        boolean[] rendered = new boolean[BUFFER_COUNT];
        for (int b = 0; b < BUFFER_COUNT; b++) {
            if (!reachable[b]) continue;
            buffer(b).gpu.resize(paneW, paneH);
            buffer(b).gpu.render(uniforms, texturesForBuffer(b, rendered));
            rendered[b] = true;
        }
        // Swap so each buffer's output() now holds this frame's result.
        for (int b = 0; b < BUFFER_COUNT; b++) {
            if (reachable[b]) buffer(b).gpu.swap();
        }
        // Image pass, sampling the buffers' current outputs.
        image().gpu.render(uniforms, texturesForImage());
    }

    public Texture imageTexture() { return image().gpu.output(); }

    public void close() {
        for (Renderable r : renderables) r.gpu.close();
        triangle.close();
        dummy.close();
    }

    // ---- render-graph helpers --------------------------------------------------

    private boolean[] reachableBuffers() {
        boolean[] reachable = new boolean[BUFFER_COUNT];
        Deque<Integer> stack = new ArrayDeque<>();
        for (Channel c : image().channels) {
            if (c.isBuffer() && !reachable[c.bufferIndex()]) { reachable[c.bufferIndex()] = true; stack.push(c.bufferIndex()); }
        }
        while (!stack.isEmpty()) {
            int b = stack.pop();
            for (Channel c : buffer(b).channels) {
                if (c.isBuffer() && !reachable[c.bufferIndex()]) { reachable[c.bufferIndex()] = true; stack.push(c.bufferIndex()); }
            }
        }
        return reachable;
    }

    private Texture[] texturesForBuffer(int renderingIndex, boolean[] rendered) {
        Channel[] chs = buffer(renderingIndex).channels;
        Texture[] tex = new Texture[4];
        for (int i = 0; i < 4; i++) {
            Channel ch = chs[i];
            if (!ch.isBuffer()) { tex[i] = dummy; continue; }
            int b = ch.bufferIndex();
            // A different buffer already rendered this frame -> its current result;
            // otherwise (self, or not yet rendered) -> its previous frame.
            tex[i] = (b != renderingIndex && rendered[b]) ? buffer(b).gpu.pending() : buffer(b).gpu.output();
        }
        return tex;
    }

    private Texture[] texturesForImage() {
        Channel[] chs = image().channels;
        Texture[] tex = new Texture[4];
        for (int i = 0; i < 4; i++) {
            tex[i] = chs[i].isBuffer() ? buffer(chs[i].bufferIndex()).gpu.output() : dummy;
        }
        return tex;
    }

    private String combined(Pass p) {
        return common.doc().text() + "\n" + p.doc().text();
    }

    // ---- diagnostics mapping ---------------------------------------------------

    /** Every diagnostic mapped to (tab, 1-based line within that tab, message). Common
     *  errors (which appear in every pass's log) are de-duplicated. */
    public List<ErrorMark> errorMarks() {
        LinkedHashSet<ErrorMark> marks = new LinkedHashSet<>();
        int imageStart = newlineCount(common.doc().text()) + 2;   // combined-body line where the pass source begins
        for (int ri = 0; ri < renderables.size(); ri++) {
            int tabIndex = ri + 1;   // tab 0 = Common; renderables map to tabs 1..N
            for (ShaderDiagnostic d : renderables.get(ri).errors) {
                int bodyLine = d.line() - ShaderTemplate.USER_LINE_OFFSET;
                if (bodyLine < 1) continue;
                if (bodyLine < imageStart) {
                    marks.add(new ErrorMark(0, bodyLine, d.message()));
                } else {
                    marks.add(new ErrorMark(tabIndex, bodyLine - (imageStart - 1), d.message()));
                }
            }
        }
        return new ArrayList<>(marks);
    }

    /** 0-based editor lines on the active tab that carry an error (for the gutter). */
    public Set<Integer> activeErrorLines() {
        Set<Integer> lines = new HashSet<>();
        for (ErrorMark m : errorMarks()) {
            if (m.tabIndex() == active && m.line() >= 1) lines.add(m.line() - 1);
        }
        return lines;
    }

    private static int newlineCount(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++;
        return n;
    }
}
