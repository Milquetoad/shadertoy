package shadertoy;

import java.util.EnumMap;
import java.util.Map;

import jvre.core.Input;
import jvre.core.Key;

/**
 * Auto-repeat for non-text keys (backspace, arrows, delete, ...). jvre's
 * {@code typedChars()} already repeats character input, but editing/navigation keys
 * come through {@code keyDown}/{@code keyPressed} with no repeat, so we time it here:
 * fire once on press, then after an initial delay repeat at a steady interval while
 * the key is held.
 */
public final class KeyRepeat {

    private static final float DELAY = 0.40f;      // before the first repeat
    private static final float INTERVAL = 0.035f;  // between repeats after that

    private final Map<Key, Float> held = new EnumMap<>(Key.class);

    /** True on the frames {@code key}'s action should fire (press, then repeats). */
    public boolean fire(Input in, Key key, float dt) {
        if (!in.keyDown(key)) {
            held.remove(key);
            return false;
        }
        Float prev = held.get(key);
        if (prev == null) {           // just pressed this frame
            held.put(key, 0f);
            return true;
        }
        float now = prev + dt;
        held.put(key, now);
        if (now < DELAY) return false;                 // still waiting out the delay
        // In the repeat phase, fire each time we cross an INTERVAL boundary.
        float a = prev - DELAY, b = now - DELAY;
        return Math.floor(Math.max(0f, a) / INTERVAL) != Math.floor(b / INTERVAL);
    }
}
