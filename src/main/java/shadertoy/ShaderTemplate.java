package shadertoy;

/**
 * Wraps a user's Shadertoy-style {@code mainImage(out vec4, in vec2)} body into a
 * complete GLSL pair: a fullscreen-triangle vertex shader plus a fragment shader that
 * supplies the Shadertoy environment and calls the user's mainImage.
 *
 * The environment uniforms arrive in a single std140 uniform block at binding 0. The
 * block is ANONYMOUS, so its members are visible as plain globals -- {@code
 * iResolution}, {@code iTime}, etc. -- exactly as a shader expects to reference them.
 * The byte layout here must match {@link Uniforms#pack}.
 */
public final class ShaderTemplate {

    private ShaderTemplate() {}

    /** A single oversized triangle covering clip space (matching vertex buffer holds
     *  three vec2 positions; no other per-vertex data). */
    public static final String VERTEX = """
        #version 450
        layout(location = 0) in vec2 inPos;
        void main() {
            gl_Position = vec4(inPos, 0.0, 1.0);
        }
        """;

    // Fragment prologue: the Shadertoy environment. The user's first line follows
    // immediately after, which is what USER_LINE_OFFSET counts so compile errors map
    // back to editor lines.
    private static final String FRAG_PROLOGUE = """
        #version 450
        layout(location = 0) out vec4 _outColor;

        // The Shadertoy uniform set (std140, matches Uniforms.pack). Anonymous block,
        // so iResolution/iTime/iMouse/... are plain globals.
        layout(std140, set = 0, binding = 0) uniform _Globals {
            vec3  iResolution;
            float iTime;
            vec4  iMouse;
            vec4  iDate;
            float iTimeDelta;
            float iFrameRate;
            int   iFrame;
            float iSampleRate;
        };

        // Channel inputs (iChannel0..3) arrive in M4; declare zeroed placeholders now
        // so shaders that reference these still compile.
        const vec3  iChannelResolution[4] = vec3[4](vec3(0.0), vec3(0.0), vec3(0.0), vec3(0.0));
        const float iChannelTime[4]       = float[4](0.0, 0.0, 0.0, 0.0);

        // ---- user shader below ----
        """;

    private static final String FRAG_EPILOGUE = """

        // ---- host entry point ----
        void main() {
            // Vulkan's gl_FragCoord is top-left origin; Shadertoy's fragCoord is
            // bottom-left, so flip Y to match what shaders expect.
            vec2 fragCoord = vec2(gl_FragCoord.x, iResolution.y - gl_FragCoord.y);
            vec4 color = vec4(0.0, 0.0, 0.0, 1.0);
            mainImage(color, fragCoord);
            _outColor = color;
        }
        """;

    /** Lines the prologue adds before the user's first line -- for mapping compiler
     *  diagnostics back to editor line numbers. */
    public static final int USER_LINE_OFFSET = (int) FRAG_PROLOGUE.lines().count();

    /** Build the full fragment shader source for a user mainImage body. */
    public static String fragment(String userBody) {
        return FRAG_PROLOGUE + userBody + FRAG_EPILOGUE;
    }
}
