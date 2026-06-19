package shadertoy;

import java.util.List;
import java.util.Set;

import jvre.core.Color;
import jvre.core.Font;
import jvre.core.Input;
import jvre.core.Instance;
import jvre.core.MouseButton;
import jvre.core.Renderer;
import jvre.core.Renderer2D;
import jvre.core.RendererOptions;
import jvre.core.Surface;
import jvre.core.Window;

/**
 * The shadertoy clone. The window is a top bar, a left editor (with pass tabs:
 * Common, Image, and -- later -- Buffer A-D), and a right shader pane with playback
 * controls. Editing recompiles the project (debounced); a failed compile keeps the
 * last working shader running and surfaces the errors on the right tab.
 *
 * jvre is consumed purely as a published Maven Central library here.
 */
public final class Main {

    // Shadertoy's default Image shader, used to seed a new project. (The text block's
    // closing delimiter is aligned so "void" keeps column 0, the body indented 4.)
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

        int initSplit = Math.round(g.width() * 0.5f);
        Project project = new Project(renderer, g.width() - initSplit, g.height(), DEFAULT_SHADER.stripTrailing());
        Editor editor = new Editor(mono, FONT_SIZE, project.activeDocument());
        TopBar topBar = new TopBar(mono);
        TabBar tabBar = new TabBar(mono);
        ChannelStrip channelStrip = new ChannelStrip(mono);
        ShaderControls controls = new ShaderControls(mono);
        Uniforms uniforms = new Uniforms();

        boolean dirty = false;
        float lastEdit = 0f;
        float splitFrac = 0.5f;
        boolean draggingSplit = false;

        final Color divider = Color.rgb(44, 47, 54);

        while (!window.shouldClose()) {
            window.pollEvents();
            Input in = window.input();
            float dt = renderer.dt();

            int w = g.width(), h = g.height();
            // Scale the UI off the render height (not DPI): a small window gets small
            // text, a large window large text -- consistent relative size, and this
            // already accounts for hi-DPI (more pixels -> bigger).
            float scale = Math.max(0.5f, h / REFERENCE_HEIGHT);
            int barH = Math.round(topBar.height(scale));
            int tabH = Math.round(tabBar.height(scale));
            int paneH = Math.max(1, h - barH);                 // shader pane spans below the top bar
            float controlsH = controls.height(scale);

            // Splitter drag: grab the 8px zone around the divider line.
            int split = Math.round(w * splitFrac);
            boolean overDivider = Math.abs(in.mouseX() - split) <= 4 && in.mouseY() >= barH;
            if (in.mousePressed(MouseButton.LEFT) && overDivider) draggingSplit = true;
            if (!in.mouseDown(MouseButton.LEFT)) draggingSplit = false;
            if (draggingSplit) splitFrac = Math.clamp((float) in.mouseX() / w, 0.2f, 0.8f);
            split = Math.round(w * splitFrac);
            int shaderW = Math.max(1, w - split);

            // The active pass's channel strip sits at the bottom of the editor (Common
            // has no channels, so no strip there).
            Channel[] activeChannels = project.activeChannels();
            int stripH = activeChannels != null ? Math.round(channelStrip.height(scale)) : 0;
            int editorH = Math.max(1, h - barH - tabH - stripH);

            // The editor always edits the active tab's document.
            editor.setDocument(project.activeDocument());
            editor.setViewport(0, barH + tabH, split, editorH, scale);

            // 1) edit -> mark dirty (suppress while the splitter or Examples dropdown is active)
            if (!draggingSplit && !topBar.isDropdownOpen() && editor.handleInput(window, in, dt, g)) {
                dirty = true;
                lastEdit = renderer.time();
            }
            // Advance the Shadertoy clock and sample the mouse over the shader pane
            // (excluding the controls strip at the bottom).
            uniforms.update(in, dt, split, barH, shaderW, paneH, controlsH);
            // Drag-and-drop: a file dropped on a channel slot becomes that channel's
            // texture; dropped anywhere else in the editor it's imported as source.
            String[] dropped = in.droppedFiles();
            if (dropped.length > 0) {
                String path = dropped[0];
                int slot = activeChannels != null
                        ? channelStrip.slotAt(0, h - stripH, split, scale, in.mouseX(), in.mouseY())
                        : -1;
                if (slot >= 0) {
                    project.assignTexture(activeChannels[slot], path);
                } else {
                    String loaded = readTextFile(path);
                    if (loaded != null) {
                        editor.loadSource(loaded);
                        dirty = true;
                        lastEdit = renderer.time();
                    }
                }
            }
            // 2) recompile once typing settles (keeps the last good shader on error)
            if (dirty && renderer.time() - lastEdit > RECOMPILE_DELAY) {
                project.reloadActive();
                dirty = false;
            }

            Set<Integer> errorLines = project.activeErrorLines();
            List<Project.ErrorMark> marks = project.errorMarks();

            // 3) render the shader offscreen, sized to its pane
            project.resize(shaderW, paneH);
            project.render(uniforms.pack(shaderW, paneH));

            // 4) composite: top bar, tab strip + editor (left), shader (right), errors,
            //    controls.
            g.begin();
            editor.render(g, errorLines);
            g.image(project.imageTexture(), split, barH, shaderW, paneH);
            g.fillRect(split - 1, barH, 2, paneH, divider);

            // Channel strip for the active pass, then the error panel just above it.
            if (activeChannels != null) {
                channelStrip.draw(g, in, 0, h - stripH, split, scale, activeChannels, project);
            }
            drawErrorPanel(g, marks, project.tabs(), split, h - stripH, scale);

            int clickedTab = tabBar.draw(g, in, 0, barH, split, scale, project.tabs(), project.activeIndex());
            if (clickedTab >= 0) project.setActive(clickedTab);

            switch (controls.draw(g, in, split, h, shaderW, scale,
                    uniforms.paused(), uniforms.time(), uniforms.fps())) {
                case TOGGLE_PAUSE -> uniforms.togglePause();
                case RESTART -> uniforms.restart();
                case NONE -> { }
            }
            switch (topBar.draw(g, in, w, scale, editor.zoomPercent())) {
                case RESET -> {
                    project.resetImage(DEFAULT_SHADER.stripTrailing());
                    dirty = true;
                    lastEdit = renderer.time();
                }
                case SAVE -> saveShader(project.activeDocument().text());
                case LOAD -> {
                    String loaded = openShader();
                    if (loaded != null) { editor.loadSource(loaded); dirty = true; lastEdit = renderer.time(); }
                }
                case EXAMPLE -> {
                    project.setActive(1);   // always load examples into the Image tab
                    editor.loadSource(Examples.SHADERS[topBar.selectedExample()].source());
                    dirty = true;
                    lastEdit = renderer.time();
                }
                case NONE -> { }
            }
            g.end();

            renderer.drawFrame();
        }

        project.close();
        mono.close();
        renderer.waitIdle();
        renderer.close();
        surface.close();
        instance.close();
        window.close();
    }

    /** A compact error list along the bottom of the editor pane (Shadertoy-style),
     *  clipped to the editor's width. Each entry names its tab and line. */
    private static void drawErrorPanel(Renderer2D g, List<Project.ErrorMark> marks, List<Pass> tabs,
                                       float editorW, float bottomY, float scale) {
        if (marks.isEmpty()) return;
        float fontPx = 14f * scale;
        int show = Math.min(marks.size(), 4);
        float lh = g.lineHeight(fontPx);
        float panelH = lh * show + 12f * scale;
        float y = bottomY - panelH;

        g.pushClip(0, y, editorW, panelH);
        g.fillRect(0, y, editorW, panelH, Color.rgba(40, 12, 14, 235));
        g.fillRect(0, y, 3f * scale, panelH, Color.rgb(240, 92, 92));
        for (int i = 0; i < show; i++) {
            Project.ErrorMark m = marks.get(i);
            String tab = tabs.get(m.tabIndex()).name();
            g.text(tab + " line " + m.line() + ": " + m.message(),
                    10f * scale, y + 6f * scale + i * lh, fontPx, Color.rgb(250, 180, 180));
        }
        g.popClip();
    }

    /** Open a native Save dialog and write {@code text} to the chosen path. No-op on cancel. */
    private static void saveShader(String text) {
        java.awt.FileDialog fd = new java.awt.FileDialog((java.awt.Frame) null, "Save Shader", java.awt.FileDialog.SAVE);
        fd.setFile("shader.glsl");
        fd.setVisible(true);
        if (fd.getFile() == null) return;
        try { java.nio.file.Files.writeString(java.nio.file.Path.of(fd.getDirectory() + fd.getFile()), text); }
        catch (Exception ignored) {}
    }

    /** Open a native Load dialog and return the file's text, or null on cancel/error. */
    private static String openShader() {
        java.awt.FileDialog fd = new java.awt.FileDialog((java.awt.Frame) null, "Open Shader", java.awt.FileDialog.LOAD);
        fd.setFile("*.glsl;*.frag;*.fs");
        fd.setVisible(true);
        if (fd.getFile() == null) return null;
        return readTextFile(fd.getDirectory() + fd.getFile());
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
