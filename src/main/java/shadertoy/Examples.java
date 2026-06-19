package shadertoy;

/** Bundled example shaders for the Examples dropdown. Each is a self-contained
 *  mainImage body (no #version, no UBO declaration -- the template adds those). */
final class Examples {

    record Example(String name, String source) {}

    static final Example[] SHADERS = {
        new Example("Rainbow", """
            void mainImage( out vec4 fragColor, in vec2 fragCoord )
            {
                vec2 uv = fragCoord/iResolution.xy;
                vec3 col = 0.5 + 0.5*cos(iTime+uv.xyx+vec3(0,2,4));
                fragColor = vec4(col,1.0);
            }"""),

        new Example("Ray March Sphere", """
            float sdSphere(vec3 p, float r) { return length(p) - r; }

            float map(vec3 p) {
                return sdSphere(mod(p, 4.0) - 2.0, 0.8);
            }

            vec3 normal(vec3 p) {
                vec2 e = vec2(0.001, 0);
                return normalize(vec3(map(p+e.xyy)-map(p-e.xyy),
                                     map(p+e.yxy)-map(p-e.yxy),
                                     map(p+e.yyx)-map(p-e.yyx)));
            }

            void mainImage( out vec4 fragColor, in vec2 fragCoord )
            {
                vec2 uv = (fragCoord - 0.5*iResolution.xy) / iResolution.y;
                vec3 ro = vec3(0, 0, iTime*0.8);
                vec3 rd = normalize(vec3(uv, -1));

                float t = 0.0;
                vec3 col = vec3(0.05, 0.05, 0.08);
                for (int i = 0; i < 80; i++) {
                    float d = map(ro + rd*t);
                    if (d < 0.001) {
                        vec3 p = ro + rd*t;
                        vec3 n = normal(p);
                        vec3 light = normalize(vec3(1,2,3));
                        float diff = max(0.0, dot(n, light));
                        float spec = pow(max(0.0, dot(reflect(-light, n), -rd)), 32.0);
                        col = vec3(0.2,0.5,1.0)*diff + vec3(1)*spec*0.5;
                        break;
                    }
                    t += d;
                    if (t > 30.0) break;
                }
                fragColor = vec4(col, 1.0);
            }"""),

        new Example("Plasma", """
            void mainImage( out vec4 fragColor, in vec2 fragCoord )
            {
                vec2 uv = fragCoord / iResolution.xy;
                float t = iTime * 0.7;
                float v = sin(uv.x * 10.0 + t)
                        + sin(uv.y * 10.0 + t)
                        + sin((uv.x + uv.y) * 7.0 + t)
                        + sin(length(uv - 0.5) * 14.0 - t * 1.5);
                vec3 col = 0.5 + 0.5 * cos(v + vec3(0.0, 2.094, 4.189));
                fragColor = vec4(col, 1.0);
            }"""),

        new Example("Voronoi", """
            vec2 hash2(vec2 p) {
                p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
                return fract(sin(p) * 43758.5453);
            }

            void mainImage( out vec4 fragColor, in vec2 fragCoord )
            {
                vec2 uv = fragCoord / iResolution.y * 5.0;
                vec2 f = fract(uv), i = floor(uv);
                float minD = 1e9, minD2 = 1e9;
                for (int y = -1; y <= 1; y++)
                for (int x = -1; x <= 1; x++) {
                    vec2 g = vec2(x, y);
                    vec2 o = hash2(i + g);
                    o = 0.5 + 0.5 * sin(iTime * 0.6 + 6.283 * o);
                    float d = length(g + o - f);
                    if (d < minD) { minD2 = minD; minD = d; }
                    else if (d < minD2) { minD2 = d; }
                }
                float edge = minD2 - minD;
                vec3 col = mix(vec3(0.15, 0.35, 0.75), vec3(0.85, 0.65, 0.15), minD);
                col = mix(vec3(0), col, smoothstep(0.0, 0.06, edge));
                fragColor = vec4(col, 1.0);
            }"""),
    };

    private Examples() {}
}
