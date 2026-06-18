package shadertoy;

import jvre.core.Color;
import jvre.core.Instance;
import jvre.core.Renderer;
import jvre.core.Renderer2D;
import jvre.core.RendererOptions;
import jvre.core.Surface;
import jvre.core.Window;

/**
 * M1 of the shadertoy clone: a hardcoded fragment shader is compiled at runtime,
 * rendered across a fullscreen triangle into an offscreen target, and composited
 * into the right (shader) pane next to the editor placeholder. This proves the full
 * shader path the rest of the app builds on; the editor (M2) and uniforms (M3) come
 * next.
 *
 * jvre is consumed purely as a published Maven Central library here.
 */
public final class Main {

    // The M1 shader: Shadertoy's iconic animated cosine-gradient default. If this
    // renders and animates in the right pane, the whole compile -> RTT -> composite
    // path works. (Orientation was confirmed with a uv probe: green top-left.)
    private static final String SHADER = """
        void mainImage(out vec4 fragColor, in vec2 fragCoord) {
            vec2 uv = fragCoord / iResolution.xy;
            vec3 col = 0.5 + 0.5 * cos(iTime + uv.xyx + vec3(0.0, 2.0, 4.0));
            fragColor = vec4(col, 1.0);
        }
        """;

    public static void main(String[] args) {
        Window window = new Window(1280, 720, "Shadertoy (jvre dogfood)");
        Instance instance = new Instance("shadertoy", false);
        Surface surface = new Surface(instance, window);
        Renderer renderer = new Renderer(instance, surface, window,
                RendererOptions.builder()
                        .clearColor(0.08f, 0.09f, 0.11f)
                        .build());
        Renderer2D g = renderer.renderer2D();

        final Color editorBg = Color.rgb(24, 26, 31);
        final Color divider  = Color.rgb(44, 47, 54);
        final Color fg       = Color.rgb(220, 223, 230);

        // The shader pane fills the right half; size its target to that pane.
        int initSplit = Math.round(g.width() * 0.5f);
        ShaderPane shaderPane = new ShaderPane(renderer, g.width() - initSplit, g.height(), SHADER);

        while (!window.shouldClose()) {
            window.pollEvents();

            int w = g.width();
            int h = g.height();
            int split = Math.round(w * 0.5f);   // vertical divider down the middle
            int shaderW = Math.max(1, w - split);

            // 1) render the shader offscreen, sized to the shader pane.
            shaderPane.resize(shaderW, h);
            shaderPane.render(renderer.time());

            // 2) composite: editor placeholder on the left, shader image on the right.
            g.begin();
            g.fillRect(0, 0, split, h, editorBg);
            g.image(shaderPane.texture(), split, 0, shaderW, h);
            g.fillRect(split - 1, 0, 2, h, divider);
            g.text("editor", 16, 16, 18, fg);
            g.text("shader", split + 16, 16, 18, fg);
            g.end();

            renderer.drawFrame();
        }

        shaderPane.close();
        renderer.waitIdle();
        renderer.close();
        surface.close();
        instance.close();
        window.close();
    }
}
