package shadertoy;

import java.util.Set;

import jvre.core.Color;

/**
 * Tokenizes GLSL source into per-character colors. Re-tokenizes only when the document
 * revision changes; otherwise returns the cached result. Block comment state is tracked
 * across the whole document so multi-line comments highlight correctly.
 */
final class GlslHighlighter {

    static final Color FG          = Color.rgb(213, 218, 228);
    static final Color COMMENT     = Color.rgb( 98, 114, 164);
    static final Color PREPROCESSOR= Color.rgb(255, 184, 108);
    static final Color KEYWORD     = Color.rgb(189, 147, 249);
    static final Color TYPE        = Color.rgb(139, 233, 253);
    static final Color NUMBER      = Color.rgb(255, 184, 108);
    static final Color BUILTIN     = Color.rgb( 80, 250, 123);

    private static final Set<String> KEYWORDS = Set.of(
        "if", "else", "for", "while", "do", "return", "break", "continue",
        "discard", "switch", "case", "default", "struct", "true", "false",
        "in", "out", "inout", "uniform", "const", "layout", "precision",
        "highp", "mediump", "lowp", "flat", "smooth", "centroid"
    );

    private static final Set<String> TYPES = Set.of(
        "void", "float", "int", "uint", "bool", "double",
        "vec2", "vec3", "vec4", "dvec2", "dvec3", "dvec4",
        "ivec2", "ivec3", "ivec4", "uvec2", "uvec3", "uvec4",
        "bvec2", "bvec3", "bvec4",
        "mat2", "mat3", "mat4",
        "mat2x2", "mat2x3", "mat2x4", "mat3x2", "mat3x3", "mat3x4",
        "mat4x2", "mat4x3", "mat4x4",
        "sampler2D", "sampler3D", "samplerCube", "sampler2DArray", "sampler2DShadow"
    );

    private static final Set<String> BUILTINS = Set.of(
        "texture", "textureLod", "textureSize", "texelFetch", "textureGrad",
        "mix", "clamp", "step", "smoothstep", "fract", "floor", "ceil", "round",
        "abs", "sign", "min", "max", "mod", "pow", "sqrt", "inversesqrt",
        "exp", "exp2", "log", "log2",
        "sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh",
        "dot", "cross", "length", "distance", "normalize", "reflect", "refract",
        "transpose", "inverse", "determinant",
        "dFdx", "dFdy", "fwidth",
        "any", "all", "not", "equal", "notEqual",
        "lessThan", "greaterThan", "lessThanEqual", "greaterThanEqual",
        "radians", "degrees", "isinf", "isnan",
        "mainImage"
    );

    private Color[] colors = new Color[0];
    private int cachedRevision = -1;

    /** Returns the per-character color array for {@code text}, re-tokenizing only when
     *  {@code revision} changes. Length always equals {@code text.length()}. */
    Color[] update(String text, int revision) {
        if (revision == cachedRevision) return colors;
        if (colors.length != text.length()) colors = new Color[text.length()];
        tokenize(text, colors);
        cachedRevision = revision;
        return colors;
    }

    private static void tokenize(String text, Color[] out) {
        boolean inBlock = false;
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);

            if (inBlock) {
                if (c == '*' && i + 1 < n && text.charAt(i + 1) == '/') {
                    out[i++] = COMMENT; out[i++] = COMMENT;
                    inBlock = false;
                } else {
                    out[i++] = COMMENT;
                }
                continue;
            }

            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                while (i < n && text.charAt(i) != '\n') out[i++] = COMMENT;
                continue;
            }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                out[i++] = COMMENT; out[i++] = COMMENT;
                inBlock = true;
                continue;
            }

            if (c == '#') {
                while (i < n && text.charAt(i) != '\n') out[i++] = PREPROCESSOR;
                continue;
            }

            if (Character.isLetter(c) || c == '_') {
                int s = i;
                while (i < n && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) i++;
                String word = text.substring(s, i);
                Color wc = TYPES.contains(word)    ? TYPE
                         : KEYWORDS.contains(word) ? KEYWORD
                         : BUILTINS.contains(word) ? BUILTIN
                         : FG;
                for (int j = s; j < i; j++) out[j] = wc;
                continue;
            }

            if (Character.isDigit(c)) {
                while (i < n && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '.')) out[i++] = NUMBER;
                continue;
            }

            out[i++] = FG;
        }
    }
}
