# JVRE Shaderpad

A standalone, **[Shadertoy](https://www.shadertoy.com/)-compatible** live GLSL shader
editor, built as the first proper dogfood of
[jvre](https://github.com/Milquetoad/jvre) (Java + Vulkan rendering framework). Two
panes: write a GLSL fragment shader on the left, see it rendered live on the right.

Not affiliated with or endorsed by Shadertoy; "Shadertoy" is used only to describe
shader-format compatibility.

jvre is consumed here **exactly as an external user would** -- the published Maven
Central artifact only, never the jvre source tree. Friction and gaps found while
building drive fixes upstream in jvre; see [`FEEDBACK.md`](FEEDBACK.md).

## Requirements

- JDK 21
- A Vulkan 1.3 GPU + driver

## Run

```
./gradlew run        # or .\gradlew.bat run on Windows
```

The Gradle wrapper bootstraps everything (no system Gradle needed). On first run it
downloads Gradle, jvre, and the LWJGL natives for your platform.

## Platform

`build.gradle` defaults to `natives-windows`. For Linux/macOS change the
`lwjglNatives` value to `natives-linux`, `natives-macos`, or `natives-macos-arm64`.

## Roadmap

- **M0** -- project setup; window + two-pane shell. *(done)*
- **M1** -- single shader compiled at runtime, rendered to a target, composited into
  the shader pane.
- **M2** -- live editor + recompile on edit, with an error overlay.
- **M3** -- full Shadertoy uniform parity (`iTime`, `iMouse`, `iFrame`, ...).
- **M4** -- multipass buffers, texture/cubemap/volume/keyboard/audio channels.
- **M5** -- polish: splitter, syntax highlighting, examples, save/load, frame export.
