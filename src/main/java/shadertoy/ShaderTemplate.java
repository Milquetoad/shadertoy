package shadertoy;

/**
 * Wraps a user's Shadertoy-style {@code mainImage(out vec4, in vec2)} body into a
 * complete GLSL pair: a fullscreen-triangle vertex shader plus a fragment shader
 * that supplies the Shadertoy environment (globals like iResolution/iTime) and calls
 * the user's mainImage. Keeping the boilerplate here means the editor only ever deals
 * with the small, familiar snippet.
 */
public final class ShaderTemplate {

    private ShaderTemplate() {}

    /** A single oversized triangle covering clip space. The matching vertex buffer
     *  holds three vec2 positions; there is no other per-vertex data. */
    public static final String VERTEX = """
        #version 450
        layout(location = 0) in vec2 inPos;
        void main() {
            gl_Position = vec4(inPos, 0.0, 1.0);
        }
        """;

    // Everything the fragment shader puts BEFORE the user's code. The user's first
    // line lands immediately after this block -- USER_LINE_OFFSET counts it so a
    // compile error on the full source can later be mapped back to the editor line.
    private static final String FRAG_PROLOGUE = """
        #version 450
        layout(location = 0) out vec4 _outColor;

        // Per-frame inputs from the host (written in ShaderPane.render).
        layout(push_constant) uniform _Push {
            vec2 iResolution;   // render-target size in pixels
            float iTime;        // seconds since start
        } _pc;

        // Shadertoy globals, assigned in main() before mainImage runs.
        vec3 iResolution;
        float iTime;

        // ---- user shader below ----
        """;

    private static final String FRAG_EPILOGUE = """

        // ---- host entry point ----
        void main() {
            iResolution = vec3(_pc.iResolution, 1.0);
            iTime = _pc.iTime;
            // Vulkan's gl_FragCoord is top-left origin; Shadertoy's fragCoord is
            // bottom-left, so flip Y to match what shaders expect.
            vec2 fragCoord = vec2(gl_FragCoord.x, _pc.iResolution.y - gl_FragCoord.y);
            vec4 color = vec4(0.0, 0.0, 0.0, 1.0);
            mainImage(color, fragCoord);
            _outColor = color;
        }
        """;

    /** Lines the prologue adds before the user's first line -- for mapping compiler
     *  diagnostics back to editor line numbers (used from M2 onward). */
    public static final int USER_LINE_OFFSET = (int) FRAG_PROLOGUE.lines().count();

    /** Build the full fragment shader source for a user mainImage body. */
    public static String fragment(String userBody) {
        return FRAG_PROLOGUE + userBody + FRAG_EPILOGUE;
    }
}
