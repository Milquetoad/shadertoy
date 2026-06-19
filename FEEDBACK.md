# jvre dogfood feedback log

This project is a Shadertoy clone built to exercise [jvre](https://github.com/Milquetoad/jvre)
as a real external consumer (published Maven Central artifact only). This file
records friction, gaps, and wins found while building against it -- the point of a
dogfood.

## Conventions
- One dated entry per finding. Tag each `[win]`, `[friction]`, `[gap]`, or `[bug]`.
- Reference the jvre version in play.

## Log

### 2026-06-18 -- jvre 1.2.0

- `[win]` POM brings the LWJGL api jars transitively via the lwjgl-bom (3.3.4) plus
  JOML; the consumer only has to add the OS-specific natives. Clean separation.
- `[win]` All four 1.2.0 features we needed verified present: `TargetFormat.HDR_FLOAT32`,
  `createCubemap`, `createVolume`, and `createDynamicTexture` (the latter also covers
  per-frame video/webcam/mic uploads -- no createImage churn).
- `[win]` M0 scaffold compiles clean against `jvre:1.2.0` on the first try -- the
  getting-started bootstrap (Window/Instance/Surface/Renderer/RendererOptions +
  Renderer2D) matches the docs exactly. `gradlew compileJava` -> BUILD SUCCESSFUL.
- `[bug]` First-launch crash for a default consumer: `new Instance(...)` throws
  `OutOfMemoryError: Out of stack space` from `MemoryStack.nmalloc`. jvre's Instance
  creation lets LWJGL's `VkInstance` enumerate device extensions
  (`getAvailableDeviceExtensions` -> `VkExtensionProperties.malloc(count)`) on
  LWJGL's shared 64KB MemoryStack; a GPU/driver that reports many extensions
  overflows it. A hello-world following the getting-started guide crashes on the
  first call with no hint why.
  - Consumer workaround (applied here): run with `-Dorg.lwjgl.system.stackSize=512`.
  - Suggested jvre fix: size the enumeration to the reported `count` off the shared
    stack -- either a dedicated `stackPush()` frame sized to need, a heap
    (`VkExtensionProperties.malloc(count)` + free), or bump the stack via
    `Configuration.STACK_SIZE` during init so consumers don't have to.
  - **RESOLVED in jvre 1.2.1.** Bumped the dependency and removed the
    `-Dorg.lwjgl.system.stackSize` workaround; a clean consumer launches with no
    flags. End-to-end dogfood loop worked: clone hit the bug -> reported -> jvre
    fixed upstream -> clone consumes the fix.
- `[win]` M0 runs: window opens, NVIDIA RTX 4090 picked, swapchain (3 images, FIFO),
  4x MSAA, DejaVuSans SDF atlas baked, two-pane shell renders, clean teardown.

### 2026-06-18 -- M1 (jvre 1.2.1)

- `[win]` Full shader path works first try: runtime GLSL compile
  (`ShaderCompiler.compileFragment`) -> L1 pipeline with push constants -> fullscreen
  triangle -> `drawToTarget(RenderTarget)` -> composite via `g.image(target.texture())`
  beside a Renderer2D pane. The "true simultaneous two panes" that was the hard
  milestone under jvre 1.0 is trivial once RTT exists.
- `[win]` RTT orientation is sane: a render target sampled with `g.image` is NOT
  Y-flipped relative to the swapchain. With a `gl_FragCoord.y` flip in the wrapper to
  honour Shadertoy's bottom-left origin, a uv probe paints green top-left as expected.
  No surprise double-flip to work around.
- `[note]` Window resize works (swapchain recreated to 3840x2001 on this 4K display);
  `ShaderPane.resize` recreates the target via the documented waitIdle/close/create
  dance without issue.

### 2026-06-18 -- M2 live editor (jvre 1.2.1)

- `[win]` `loadFont(path, size)` resolves resources off the CONSUMER's classpath, not
  just jvre's own jar -- bundling `/fonts/JetBrainsMono-Regular.ttf` in our resources
  and loading it Just Worked.
- `[win]` `pipeline.reloadShaders(vert, frag)` hot-swap is exactly what live editing
  needs: last-good-on-error (a broken edit keeps the previous shader running) and
  structured `ShaderDiagnostic.line()` that maps cleanly back to editor lines via a
  fixed prologue offset. Recompiled ~dozens of times in a session with no leak/crash.
- `[win]` `pushClip/popClip` is all the editor needed to clip text + gutter + the
  error panel to the pane.
- `[gap]` The `Key` enum has letters, digits, arrows, F-keys and modifiers, but NO
  punctuation/OEM keys (no EQUAL, MINUS, COMMA, PERIOD, SLASH, BRACKETs, etc.). That
  blocks common editor bindings like Ctrl+'+' / Ctrl+'-' for zoom and Ctrl+'/' for
  comment. Worked around zoom with Ctrl+mouse-wheel. Candidate jvre addition.
- `[note]` `Renderer2D` works in framebuffer pixels and `contentScaleY` is constant
  per monitor, so neither alone is the right basis for sizing a resizable editor.
  Drove UI scale from render height instead (small window -> small text). Not a jvre
  bug -- just a sizing lesson; a documented "logical vs framebuffer" note would help.
- `[note]` jvre ships one proportional font (DejaVu Sans); a code editor wants
  monospace, so bundling one is on the consumer. Expected, not a gap.

### 2026-06-18 -- M3 uniform parity + time controls (jvre 1.2.1)

- `[win]` Full Shadertoy uniform set fed through a single std140 UBO
  (`.uniformBuffer(64, FRAGMENT)` + `frame.uniform(float[16])`). An ANONYMOUS uniform
  block makes the members (iResolution/iTime/iMouse/...) visible as plain globals,
  exactly how shaders reference them -- jvre accepted it with no fuss.
- `[win]` Real shadertoy.com shaders mostly run unmodified once the uniforms exist;
  pasted several and they worked. One looked slightly off -- TODO investigate (likely
  needs multipass buffers/channels in M4, or is sensitive to the 16-bit default
  target precision).
- `[note]` `frame.uniform(float[])` is the only UBO upload path, so an integer member
  (iFrame) has to be packed as `Float.intBitsToFloat(n)`. Works perfectly, but a
  typed/byte-buffer uniform overload would be a touch more ergonomic. Minor.

### 2026-06-19 -- M4 multipass (jvre 1.2.1)

- `[win]` The whole multipass machinery worked on first run: `HDR_FLOAT32`
  ping-pong render targets, `createPipeline(spec, target)` to bake a target's format,
  four `sampler2D` channels bound per draw via `frame.texture(i, tex)`, and several
  `drawToTarget` passes per frame composing a render graph. A self-feedback Buffer A
  (trail effect) into the Image pass rendered correctly.
- `[win]` `textureSize(iChannelN, 0)` in-shader is a clean way to provide
  `iChannelResolution` without bloating the UBO.
- `[note]` Plain `createPipeline(spec)` bakes the swapchain format, so rendering into
  an HDR float target needs the `createPipeline(spec, target)` overload. Worth calling
  out in the RTT docs (easy to miss as a consumer).
