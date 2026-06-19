package shadertoy;

import java.nio.ByteBuffer;

import org.lwjgl.util.shaderc.Shaderc;

/**
 * Compiles GLSL to SPIR-V at optimization level ZERO.
 *
 * jvre's built-in {@code ShaderCompiler} hardcodes shaderc's <em>performance</em>
 * optimization, which reorders and fuses floating-point operations. That wrecks
 * precision-sensitive procedural hashes like {@code fract(sin(dot(p,k))*43758.5)} --
 * adjacent value-noise cells end up correlated, so smooth cloudy noise degrades into
 * visible blocky cells that real Shadertoy (which compiles unoptimized) never shows.
 *
 * We use jvre's compiler only for error diagnostics (errors are reported the same at
 * any optimization level) and feed the pipeline our own unoptimized SPIR-V instead.
 */
final class GlslCompiler {

    static final int VERTEX   = Shaderc.shaderc_glsl_vertex_shader;
    static final int FRAGMENT = Shaderc.shaderc_glsl_fragment_shader;

    private GlslCompiler() {}

    /** Compile {@code source} (a {@link #VERTEX}/{@link #FRAGMENT} stage) to SPIR-V
     *  bytes with no optimization. Throws {@link RuntimeException} on failure -- callers
     *  validate via jvre's compiler first, so this only sees already-valid source. */
    static byte[] compile(String source, int kind, String name) {
        long compiler = Shaderc.shaderc_compiler_initialize();
        long options  = Shaderc.shaderc_compile_options_initialize();
        try {
            Shaderc.shaderc_compile_options_set_target_env(options,
                    Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_3);
            Shaderc.shaderc_compile_options_set_optimization_level(options,
                    Shaderc.shaderc_optimization_level_zero);
            long result = Shaderc.shaderc_compile_into_spv(compiler, source, kind, name, "main", options);
            try {
                if (Shaderc.shaderc_result_get_compilation_status(result)
                        != Shaderc.shaderc_compilation_status_success) {
                    throw new RuntimeException(Shaderc.shaderc_result_get_error_message(result));
                }
                ByteBuffer spv = Shaderc.shaderc_result_get_bytes(result);
                byte[] bytes = new byte[spv.remaining()];
                spv.get(bytes);
                return bytes;
            } finally {
                Shaderc.shaderc_result_release(result);
            }
        } finally {
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }
}
