package shadertoy;

import java.util.List;

import jvre.core.AttribFormat;
import jvre.core.Buffer;
import jvre.core.Filter;
import jvre.core.Pipeline;
import jvre.core.PipelineSpec;
import jvre.core.RenderTarget;
import jvre.core.Renderer;
import jvre.core.ShaderCompileException;
import jvre.core.ShaderCompiler;
import jvre.core.ShaderDiagnostic;
import jvre.core.Stage;
import jvre.core.TargetFormat;
import jvre.core.Texture;
import jvre.core.VertexLayout;

/**
 * The GPU side of one shader pass: a pipeline (Shadertoy UBO + four iChannel samplers)
 * that renders a fullscreen triangle into an offscreen target. A buffer pass is
 * double-buffered (ping-pong) so it can read its own previous frame for feedback; the
 * Image pass is single-buffered.
 *
 * The pipeline always declares four texture channels, so its resource interface is
 * fixed -- editing the shader hot-reloads in place, and reassigning channels just
 * binds different textures (no rebuild).
 */
public final class RenderPass {

    private final Renderer renderer;
    private final Buffer triangle;      // shared fullscreen-triangle vertex buffer
    private final boolean pingPong;
    private final boolean toDisplay;     // sRGB display target (Image pass) vs linear float buffer
    private final TargetFormat format;

    private Pipeline pipeline;
    private RenderTarget front;          // last completed output (sampled by others)
    private RenderTarget back;           // ping-pong write target (null when single-buffered)
    private int width, height;

    public RenderPass(Renderer renderer, Buffer triangle, int w, int h,
                      boolean pingPong, boolean hdr, String combinedSource) {
        this.renderer = renderer;
        this.triangle = triangle;
        this.pingPong = pingPong;
        this.toDisplay = !hdr;
        this.format = hdr ? TargetFormat.HDR_FLOAT32 : TargetFormat.DEFAULT;
        this.width = Math.max(1, w);
        this.height = Math.max(1, h);

        // LINEAR sampling so the supersampled output downsamples smoothly on display
        // (and buffer reads filter cleanly).
        front = renderer.createRenderTarget(width, height, format, Filter.LINEAR);
        if (pingPong) back = renderer.createRenderTarget(width, height, format, Filter.LINEAR);

        byte[] vs = ShaderCompiler.compileVertex(ShaderTemplate.VERTEX, "pass.vert");
        byte[] fs = ShaderCompiler.compileFragment(ShaderTemplate.fragment(combinedSource, toDisplay), "pass.frag");
        VertexLayout layout = VertexLayout.builder(2 * Float.BYTES)
                .attribute(0, AttribFormat.VEC2, 0)
                .build();
        PipelineSpec.Builder spec = PipelineSpec.builder()
                .vertexShader(vs)
                .fragmentShader(fs)
                .vertexLayout(layout)
                .uniformBuffer(Uniforms.FLOAT_COUNT * Float.BYTES, Stage.FRAGMENT);
        for (int i = 0; i < 4; i++) spec = spec.texture(Stage.FRAGMENT);   // iChannel0..3 -> binding 1..4
        // Bake the target's format into the pipeline (HDR float vs LDR).
        pipeline = renderer.createPipeline(spec.label("pass").build(), front);
    }

    /** Hot-swap a new shader body; last-good-on-error, returns diagnostics. */
    public List<ShaderDiagnostic> reload(String combinedSource) {
        try {
            pipeline.reloadShaders(ShaderTemplate.VERTEX, ShaderTemplate.fragment(combinedSource, toDisplay));
            return List.of();
        } catch (ShaderCompileException e) {
            return e.errors();
        }
    }

    public void resize(int w, int h) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        if (w == width && h == height) return;
        renderer.waitIdle();
        front.close();
        front = renderer.createRenderTarget(w, h, format, Filter.LINEAR);
        if (pingPong) {
            back.close();
            back = renderer.createRenderTarget(w, h, format, Filter.LINEAR);
        }
        width = w;
        height = h;
    }

    /** Render into the write target, binding the four channel textures (one per
     *  iChannel; pass the shared dummy for unassigned channels). */
    public void render(float[] uniforms, Texture[] channels) {
        RenderTarget out = pingPong ? back : front;
        renderer.drawToTarget(out, frame -> {
            frame.bind(pipeline);
            frame.uniform(uniforms);
            for (int i = 0; i < 4; i++) frame.texture(i, channels[i]);
            frame.bindVertexBuffer(triangle);
            frame.draw(3);
        });
    }

    /** Swap front/back so {@link #output()} now holds this frame's result. No-op for
     *  a single-buffered pass. */
    public void swap() {
        if (!pingPong) return;
        RenderTarget t = front;
        front = back;
        back = t;
    }

    /** The last completed output -- previous frame during the buffer phase, current
     *  frame after {@link #swap()}. */
    public Texture output() {
        return front.texture();
    }

    /** The render target backing {@link #output()} -- used by {@code readPixels}. */
    public RenderTarget outputTarget() {
        return front;
    }

    /** The target just written this frame, before a swap (a buffer rendered earlier
     *  this frame, read by a later buffer in the same frame). */
    public Texture pending() {
        return (pingPong ? back : front).texture();
    }

    public void close() {
        front.close();
        if (back != null) back.close();
        pipeline.close();
    }
}
