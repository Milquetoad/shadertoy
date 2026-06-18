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
