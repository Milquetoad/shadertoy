package shadertoy;

import jvre.core.Color;
import jvre.core.Instance;
import jvre.core.Renderer;
import jvre.core.Renderer2D;
import jvre.core.RendererOptions;
import jvre.core.Surface;
import jvre.core.Window;

/**
 * M0 of the shadertoy clone: prove the jvre object stack boots, a window opens,
 * and we can draw a frame. No editor, no shader -- just the two-pane LAYOUT SHELL
 * (editor on the left, shader output on the right) so the skeleton is visible.
 *
 * jvre is consumed purely as a published Maven Central library here; nothing in
 * this project references the jvre source tree.
 */
public final class Main {

    public static void main(String[] args) {
        // Set up the object stack (see jvre's getting-started guide). Validation is
        // OFF so M0 runs without the optional Vulkan SDK installed; flip to true
        // once the SDK + validation layers are present for development checks.
        Window window = new Window(1280, 720, "Shadertoy (jvre dogfood)");
        Instance instance = new Instance("shadertoy", false);
        Surface surface = new Surface(instance, window);
        Renderer renderer = new Renderer(instance, surface, window,
                RendererOptions.builder()
                        .clearColor(0.08f, 0.09f, 0.11f)
                        .build());
        Renderer2D g = renderer.renderer2D();

        // Palette for the shell. Real theming arrives with the editor (M2).
        final Color editorBg = Color.rgb(24, 26, 31);
        final Color shaderBg = Color.rgb(12, 13, 16);
        final Color divider  = Color.rgb(44, 47, 54);
        final Color fg       = Color.rgb(220, 223, 230);

        // Loop while the window is open: poll input, draw a frame.
        while (!window.shouldClose()) {
            window.pollEvents();

            int w = g.width();
            int h = g.height();
            float split = Math.round(w * 0.5f);   // vertical divider down the middle

            g.begin();
            g.fillRect(0, 0, split, h, editorBg);               // left: editor pane
            g.fillRect(split, 0, w - split, h, shaderBg);       // right: shader pane
            g.fillRect(split - 1, 0, 2, h, divider);            // the splitter
            g.text("editor", 16, 16, 18, fg);
            g.text("shader", split + 16, 16, 18, fg);
            g.end();

            renderer.drawFrame();
        }

        // Tear down in reverse order.
        renderer.waitIdle();
        renderer.close();
        surface.close();
        instance.close();
        window.close();
    }
}
