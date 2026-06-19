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
    private static final int SUPERSAMPLE = 2;             // shader renders at SSx, downsampled on display

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
        final Color dividerHot = Color.rgb(90, 130, 200);

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
            int paneW = Math.max(1, w - split);

            // Letterbox the shader to Shadertoy's 16:9 canvas inside the right pane, so a
            // shader is framed identically regardless of how the editor split is sized.
            int shaderW, shaderH, shaderX, shaderY;
            if ((float) paneW / paneH > 16f / 9f) {
                shaderH = paneH;  shaderW = Math.round(shaderH * 16f / 9f);
            } else {
                shaderW = paneW;  shaderH = Math.round(shaderW * 9f / 16f);
            }
            shaderX = split + (paneW - shaderW) / 2;
            shaderY = barH + (paneH - shaderH) / 2;

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
            uniforms.update(in, dt, shaderX, shaderY, shaderW, shaderH, controlsH, SUPERSAMPLE);
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

            // 3) render the shader offscreen at SUPERSAMPLE x the (letterboxed) canvas;
            //    the display pass downsamples it, giving cheap edge antialiasing.
            int rw = shaderW * SUPERSAMPLE, rh = shaderH * SUPERSAMPLE;
            project.resize(rw, rh);
            project.render(uniforms.pack(rw, rh));

            // 4) composite: top bar, tab strip + editor (left), shader (right), errors,
            //    controls.
            g.begin();
            editor.render(g, errorLines);
            g.fillRect(split, barH, paneW, paneH, divider);   // letterbox margins
            g.image(project.imageTexture(), shaderX, shaderY, shaderW, shaderH);

            // Draggable splitter: a 2px line with a grip of dots, brightened on hover/drag.
            boolean splitHot = draggingSplit || overDivider;
            g.fillRect(split - 1, barH, 2, paneH, splitHot ? dividerHot : divider);
            float dotR = 2f * scale, gap = 6f * scale;
            float cy = barH + (h - barH) * 0.5f;
            for (int d = -1; d <= 1; d++) {
                g.fillRect(split - dotR, cy + d * gap - dotR, 2 * dotR, 2 * dotR,
                        splitHot ? Color.rgb(180, 188, 205) : Color.rgb(96, 102, 116));
            }

            // Channel strip for the active pass, then the error panel just above it.
            if (activeChannels != null) {
                channelStrip.draw(g, in, 0, h - stripH, split, scale, activeChannels, project);
            }
            drawErrorPanel(g, marks, project.tabs(), split, h - stripH, scale);

            int clickedTab = tabBar.draw(g, in, 0, barH, split, scale, project.tabs(), project.activeIndex());
            if (clickedTab >= 0) project.setActive(clickedTab);

            switch (controls.draw(g, in, shaderX, shaderY + shaderH, shaderW, scale,
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
                case EXPORT -> exportFrame(renderer, project);
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

    /** Read the current Image pass output and write it as a PNG (native Save dialog). */
    private static void exportFrame(Renderer renderer, Project project) {
        java.awt.FileDialog fd = new java.awt.FileDialog((java.awt.Frame) null, "Export Frame", java.awt.FileDialog.SAVE);
        fd.setFile("frame.png");
        fd.setVisible(true);
        if (fd.getFile() == null) return;
        renderer.waitIdle();
        exportFrameTo(renderer, project, fd.getDirectory() + fd.getFile());
    }

    /** Read the Image pass output and write it as a PNG to {@code path}. */
    private static void exportFrameTo(Renderer renderer, Project project, String path) {
        jvre.core.RenderTarget rt = project.imageRenderTarget();
        byte[] raw = renderer.readPixels(rt);
        int w = rt.width(), h = rt.height();
        // readPixels returns RGBA bytes; pack into ARGB for BufferedImage.
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int o = (y * w + x) * 4;
                int r = raw[o]     & 0xFF;
                int g = raw[o + 1] & 0xFF;
                int b = raw[o + 2] & 0xFF;
                int a = raw[o + 3] & 0xFF;
                img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        try { javax.imageio.ImageIO.write(img, "PNG", new java.io.File(path)); }
        catch (Exception ignored) {}
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
