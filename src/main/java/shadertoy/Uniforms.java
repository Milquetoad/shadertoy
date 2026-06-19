package shadertoy;

import java.time.LocalDateTime;

import jvre.core.Input;
import jvre.core.MouseButton;

/**
 * The Shadertoy uniform state: the playback clock (time / frame / pause) and the
 * mouse, packed each frame into the std140 buffer the shader reads. Keeping it in one
 * place means the shader template, the time controls, and the render loop all agree
 * on what "iTime" and "iMouse" mean.
 *
 * Mouse follows Shadertoy's convention, in shader pixels with a bottom-left origin:
 * {@code iMouse.xy} = current position while dragging (frozen on release);
 * {@code iMouse.zw} = the click position, with {@code z > 0} while the button is held
 * and {@code w > 0} only on the frame the click started.
 */
public final class Uniforms {

    // The buffer is 16 floats (64 bytes), matching the std140 block in ShaderTemplate.
    public static final int FLOAT_COUNT = 16;

    // Clock.
    private float time;
    private int frame;
    private boolean paused;
    private float lastDt;

    // Mouse, in shader pixels (bottom-left origin).
    private float curX, curY, clickX, clickY;
    private boolean down, justPressed;

    /** Advance the clock and sample the mouse. The shader pane occupies
     *  {@code (paneX, paneY, paneW, paneH)} in window pixels; {@code reservedBottom}
     *  excludes an overlay strip (the controls) from registering mouse clicks. */
    public void update(Input in, float dt, float paneX, float paneY, float paneW, float paneH,
                       float reservedBottom, float renderScale) {
        lastDt = dt;
        if (!paused) { time += dt; frame++; }

        float localX = in.mouseX() - paneX;
        float localYTop = in.mouseY() - paneY;
        boolean over = localX >= 0 && localX < paneW
                    && localYTop >= 0 && localYTop < paneH - reservedBottom;
        // Pane is shown at 1x but the shader renders at renderScale x; report the mouse in
        // shader (iResolution) pixels so iMouse matches fragCoord.
        float x = clamp(localX, 0, paneW) * renderScale;
        float y = clamp(paneH - localYTop, 0, paneH) * renderScale;   // flip to bottom-left origin

        justPressed = false;
        if (in.mousePressed(MouseButton.LEFT) && over) {
            down = true;
            justPressed = true;
            clickX = x; clickY = y;
            curX = x; curY = y;
        } else if (down && in.mouseDown(MouseButton.LEFT)) {
            curX = x; curY = y;
        }
        if (in.mouseReleased(MouseButton.LEFT)) down = false;
    }

    public void togglePause() { paused = !paused; }
    public void restart() { time = 0f; frame = 0; }
    public boolean paused() { return paused; }
    public float time() { return time; }
    public int frame() { return frame; }
    public float fps() { return lastDt > 0f ? 1f / lastDt : 0f; }

    /** Pack the std140 buffer for a shader pane of the given pixel size. */
    public float[] pack(float resW, float resH) {
        LocalDateTime now = LocalDateTime.now();
        float[] u = new float[FLOAT_COUNT];
        u[0] = resW; u[1] = resH; u[2] = 1f;                 // iResolution (z = pixel aspect)
        u[3] = time;                                          // iTime
        u[4] = curX; u[5] = curY;                             // iMouse.xy
        u[6] = down ? clickX : -clickX;                       // iMouse.z (>0 while held)
        u[7] = justPressed ? clickY : -clickY;                // iMouse.w (>0 on click frame)
        u[8] = now.getYear();                                 // iDate.x
        u[9] = now.getMonthValue() - 1;                       // iDate.y (0-based, Shadertoy)
        u[10] = now.getDayOfMonth();                          // iDate.z
        u[11] = now.toLocalTime().toSecondOfDay() + now.getNano() * 1e-9f;  // iDate.w
        u[12] = paused ? 0f : lastDt;                         // iTimeDelta
        u[13] = fps();                                        // iFrameRate
        u[14] = Float.intBitsToFloat(frame);                  // iFrame (int, written as raw bits)
        u[15] = 44100f;                                       // iSampleRate
        return u;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
