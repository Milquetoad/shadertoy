package shadertoy;

import jvre.core.AttribFormat;
import jvre.core.Buffer;
import jvre.core.Pipeline;
import jvre.core.PipelineSpec;
import jvre.core.RenderTarget;
import jvre.core.Renderer;
import jvre.core.ShaderCompiler;
import jvre.core.Stage;
import jvre.core.Texture;
import jvre.core.VertexLayout;

/**
 * The shader pane: compiles a user fragment shader, renders it across a fullscreen
 * triangle into an offscreen {@link RenderTarget}, and exposes that target's texture
 * so the main frame can composite it (via {@code Renderer2D.image}) beside the
 * editor. This is the core path the whole app is built on -- live editing, full
 * uniform parity, and multipass all extend it.
 */
public final class ShaderPane {

    private final Renderer renderer;
    private final Pipeline pipeline;
    private final Buffer fullscreenTri;

    private RenderTarget target;
    private int width;
    private int height;

    public ShaderPane(Renderer renderer, int width, int height, String userBody) {
        this.renderer = renderer;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);

        byte[] vert = ShaderCompiler.compileVertex(ShaderTemplate.VERTEX, "pane.vert");
        byte[] frag = ShaderCompiler.compileFragment(ShaderTemplate.fragment(userBody), "pane.frag");

        // One vec2 position per vertex.
        VertexLayout layout = VertexLayout.builder(2 * Float.BYTES)
                .attribute(0, AttribFormat.VEC2, 0)
                .build();

        this.pipeline = renderer.createPipeline(PipelineSpec.builder()
                .vertexShader(vert)
                .fragmentShader(frag)
                .vertexLayout(layout)
                .pushConstants(3 * Float.BYTES, Stage.FRAGMENT)   // vec2 iResolution + float iTime
                .label("shader-pane")
                .build());

        // The classic fullscreen triangle: a single triangle large enough to cover
        // the whole [-1, 1] clip square, so every pane pixel runs the fragment.
        this.fullscreenTri = renderer.createVertexBuffer(new float[] {
            -1f, -1f,
             3f, -1f,
            -1f,  3f,
        });

        this.target = renderer.createRenderTarget(this.width, this.height);
    }

    /** Re-create the offscreen target when the pane's pixel size changes (jvre's
     *  documented resize dance: wait idle, close, recreate). */
    public void resize(int newWidth, int newHeight) {
        newWidth = Math.max(1, newWidth);
        newHeight = Math.max(1, newHeight);
        if (newWidth == width && newHeight == height) return;
        renderer.waitIdle();          // the target may be in use by an in-flight frame
        target.close();
        width = newWidth;
        height = newHeight;
        target = renderer.createRenderTarget(width, height);
    }

    /** Render the shader into the offscreen target for this frame. */
    public void render(float time) {
        renderer.drawToTarget(target, frame -> {
            frame.bind(pipeline);
            frame.bindVertexBuffer(fullscreenTri);
            frame.pushConstants(new float[] { width, height, time });
            frame.draw(3);
        });
    }

    /** The rendered result, for compositing with {@code Renderer2D.image(...)}. */
    public Texture texture() {
        return target.texture();
    }

    public void close() {
        target.close();
        fullscreenTri.close();
        pipeline.close();
    }
}
