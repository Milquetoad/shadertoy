package shadertoy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jvre.core.Color;
import jvre.core.Font;
import jvre.core.Input;
import jvre.core.Instance;
import jvre.core.Renderer;
import jvre.core.Renderer2D;
import jvre.core.RendererOptions;
import jvre.core.ShaderDiagnostic;
import jvre.core.Surface;
import jvre.core.Window;

/**
 * M2 of the shadertoy clone: a live editor. Type GLSL in the left pane; the shader
 * recompiles (debounced) and updates in the right pane. A failed compile keeps the
 * last working shader running and shows the errors -- in the gutter (red line
 * numbers) and a panel along the bottom of the editor.
 *
 * jvre is consumed purely as a published Maven Central library here.
 */
public final class Main {

    // Shadertoy's default shader, used to seed the editor. (The text block's closing
    // delimiter is aligned so the content keeps "void" at column 0, body indented 4.)
    private static final String DEFAULT_SHADER = """
        void mainImage( out vec4 fragColor, in vec2 fragCoord )
        {
            // Normalized pixel coordinates (from 0 to 1)
            vec2 uv = fragCoord/iResolution.xy;

            // Time varying pixel color
            vec3 col = 0.5 + 0.5*cos(iTime+uv.xyx+vec3(0,2,4));

            // Output to screen
            fragColor = vec4(col,1.0);
        }
        """;

    private static final float FONT_SIZE = 18f;
    private static final float RECOMPILE_DELAY = 0.25f;   // seconds of quiet before recompiling
    private static final float REFERENCE_HEIGHT = 900f;   // render height at which UI is 1:1

    public static void main(String[] args) {
        Window window = new Window(1280, 720, "Shadertoy (jvre dogfood)");
        Instance instance = new Instance("shadertoy", false);
        Surface surface = new Surface(instance, window);
        Renderer renderer = new Renderer(instance, surface, window,
                RendererOptions.builder()
                        .clearColor(0.08f, 0.09f, 0.11f)
                        .build());
        Renderer2D g = renderer.renderer2D();

        // A monospace font for the editor (jvre's built-in font is proportional).
        // Baked large so the SDF stays crisp even when scaled up on hi-DPI displays.
        Font mono = renderer.loadFont("/fonts/JetBrainsMono-Regular.ttf", 64f);
        Editor editor = new Editor(mono, FONT_SIZE, DEFAULT_SHADER.stripTrailing());
        TopBar topBar = new TopBar(mono);

        int initSplit = Math.round(g.width() * 0.5f);
        ShaderPane shaderPane = new ShaderPane(renderer, g.width() - initSplit, g.height(), editor.text());

        List<ShaderDiagnostic> errors = List.of();
        boolean dirty = false;
        float lastEdit = 0f;

        final Color divider = Color.rgb(44, 47, 54);

        while (!window.shouldClose()) {
            window.pollEvents();
            Input in = window.input();
            float dt = renderer.dt();

            int w = g.width(), h = g.height();
            int split = Math.round(w * 0.5f);
            int shaderW = Math.max(1, w - split);
            // Scale the UI off the render height (not DPI): a small window gets small
            // text, a large window large text -- consistent relative size, and this
            // already accounts for hi-DPI (more pixels -> bigger). REF is the height
            // at which the base font sizes render 1:1.
            float scale = Math.max(0.5f, h / REFERENCE_HEIGHT);
            int barH = Math.round(topBar.height(scale));
            int paneH = Math.max(1, h - barH);
            editor.setViewport(0, barH, split, paneH, scale);

            // 1) edit -> mark dirty
            if (editor.handleInput(window, in, dt, g)) {
                dirty = true;
                lastEdit = renderer.time();
            }
            // Drag-and-drop a text file to import it as the shader.
            String[] dropped = in.droppedFiles();
            if (dropped.length > 0) {
                String loaded = readTextFile(dropped[0]);
                if (loaded != null) {
                    editor.loadSource(loaded);
                    dirty = true;
                    lastEdit = renderer.time();
                }
            }
            // 2) recompile once typing settles (keeps the last good shader on error)
            if (dirty && renderer.time() - lastEdit > RECOMPILE_DELAY) {
                errors = shaderPane.reload(editor.text());
                dirty = false;
            }

            // Map error lines (full-source -> editor line, 0-based) for the gutter.
            Set<Integer> errorLines = new HashSet<>();
            for (ShaderDiagnostic d : errors) {
                int line0 = d.line() - ShaderTemplate.USER_LINE_OFFSET - 1;
                if (line0 >= 0) errorLines.add(line0);
            }

            // 3) render the shader offscreen, sized to its pane
            shaderPane.resize(shaderW, paneH);
            shaderPane.render(renderer.time());

            // 4) composite: top bar, editor left, shader right, errors over the editor
            g.begin();
            editor.render(g, errorLines);
            g.image(shaderPane.texture(), split, barH, shaderW, paneH);
            g.fillRect(split - 1, barH, 2, paneH, divider);
            drawErrorPanel(g, errors, split, h, scale);
            switch (topBar.draw(g, in, w, scale, editor.zoomPercent())) {
                case RESET -> {
                    editor.loadSource(DEFAULT_SHADER.stripTrailing());
                    dirty = true;
                    lastEdit = renderer.time();
                }
                case NONE -> { }
            }
            g.end();

            renderer.drawFrame();
        }

        shaderPane.close();
        mono.close();
        renderer.waitIdle();
        renderer.close();
        surface.close();
        instance.close();
        window.close();
    }

    /** A compact error list along the bottom of the editor pane (Shadertoy-style),
     *  clipped to the editor's width so long messages don't bleed into the shader. */
    private static void drawErrorPanel(Renderer2D g, List<ShaderDiagnostic> errors, float editorW, float h, float scale) {
        if (errors.isEmpty()) return;
        float fontPx = 14f * scale;
        int show = Math.min(errors.size(), 4);
        float lh = g.lineHeight(fontPx);
        float panelH = lh * show + 12f * scale;
        float y = h - panelH;

        g.pushClip(0, y, editorW, panelH);
        g.fillRect(0, y, editorW, panelH, Color.rgba(40, 12, 14, 235));
        g.fillRect(0, y, 3f * scale, panelH, Color.rgb(240, 92, 92));
        for (int i = 0; i < show; i++) {
            ShaderDiagnostic d = errors.get(i);
            int line = d.line() - ShaderTemplate.USER_LINE_OFFSET;   // 1-based editor line
            String where = line >= 1 ? "line " + line : "shader";
            g.text(where + ": " + d.message(), 10f * scale, y + 6f * scale + i * lh, fontPx, Color.rgb(250, 180, 180));
        }
        g.popClip();
    }

    /** Read a dropped file as text, normalising newlines. Returns null if it can't be
     *  read as text (e.g. a binary file was dropped). */
    private static String readTextFile(String path) {
        try {
            String s = java.nio.file.Files.readString(java.nio.file.Path.of(path));
            return s.replace("\r\n", "\n").replace('\r', '\n');
        } catch (Exception e) {
            return null;
        }
    }
}
