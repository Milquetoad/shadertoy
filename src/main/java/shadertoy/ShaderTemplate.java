package shadertoy;

/**
 * Wraps a user's Shadertoy-style {@code mainImage(out vec4, in vec2)} body into a
 * complete GLSL pair: a fullscreen-triangle vertex shader plus a fragment shader that
 * supplies the Shadertoy environment and calls the user's mainImage.
 *
 * The uniforms arrive in an anonymous std140 block at binding 0 (so iResolution,
 * iTime, etc. are plain globals, matching {@link Uniforms#pack}). The four input
 * channels are sampler2Ds at bindings 1-4; iChannelResolution is filled from the
 * bound channel sizes each frame.
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

        // Input channels: iChannel0..3 at bindings 1..4. Unassigned channels are bound
        // to a 1x1 dummy texture by the host.
        layout(set = 0, binding = 1) uniform sampler2D iChannel0;
        layout(set = 0, binding = 2) uniform sampler2D iChannel1;
        layout(set = 0, binding = 3) uniform sampler2D iChannel2;
        layout(set = 0, binding = 4) uniform sampler2D iChannel3;

        // Filled in main() from the bound channel sizes (Shadertoy exposes these).
        vec3  iChannelResolution[4];
        float iChannelTime[4];

        // sRGB electro-optical transfer (sRGB -> linear). The display target is an sRGB
        // format that re-encodes linear->sRGB on store; decoding here cancels that, so
        // the raw shader value lands in the pixel byte exactly as Shadertoy shows it.
        vec3 _srgbToLinear(vec3 c) {
            return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c));
        }

        // ---- user shader below ----
        """;

    // Common head of main(): channel metadata + the y-flipped fragCoord + mainImage call.
    private static final String FRAG_MAIN_HEAD = """

        // ---- host entry point ----
        void main() {
            iChannelResolution[0] = vec3(textureSize(iChannel0, 0), 1.0);
            iChannelResolution[1] = vec3(textureSize(iChannel1, 0), 1.0);
            iChannelResolution[2] = vec3(textureSize(iChannel2, 0), 1.0);
            iChannelResolution[3] = vec3(textureSize(iChannel3, 0), 1.0);
            iChannelTime[0] = iTime; iChannelTime[1] = iTime;
            iChannelTime[2] = iTime; iChannelTime[3] = iTime;
            // Vulkan's gl_FragCoord is top-left origin; Shadertoy's fragCoord is
            // bottom-left, so flip Y to match what shaders expect.
            vec2 fragCoord = vec2(gl_FragCoord.x, iResolution.y - gl_FragCoord.y);
            vec4 color = vec4(0.0, 0.0, 0.0, 1.0);
            mainImage(color, fragCoord);
        """;

    // Display (Image) pass: force opaque (Shadertoy ignores alpha) and pre-decode so the
    // sRGB target stores the shader value verbatim.
    private static final String FRAG_TAIL_DISPLAY = """
            _outColor = vec4(_srgbToLinear(color.rgb), 1.0);
        }
        """;

    // Buffer pass: linear float target, sampled by other passes -- store raw rgba.
    private static final String FRAG_TAIL_BUFFER = """
            _outColor = color;
        }
        """;

    /** Lines the prologue adds before the user's first line -- for mapping compiler
     *  diagnostics back to editor line numbers. */
    public static final int USER_LINE_OFFSET = (int) FRAG_PROLOGUE.lines().count();

    /** Build the full fragment shader. {@code toDisplay} = the Image pass (sRGB target,
     *  opaque, gamma-compensated); otherwise a buffer pass (raw linear output). */
    public static String fragment(String userBody, boolean toDisplay) {
        return FRAG_PROLOGUE + userBody + FRAG_MAIN_HEAD
             + (toDisplay ? FRAG_TAIL_DISPLAY : FRAG_TAIL_BUFFER);
    }
}
