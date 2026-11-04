package software.bernie.geckolib3.core.builder;

import java.io.Serializable;

/** Shape of GeckoLib Unofficial 1.7.10 1.0.4's ILoopType (for the beta.51 gate). */
public interface ILoopType extends Serializable {

    boolean isRepeatingAfterEnd();

    enum EDefaultLoopTypes implements ILoopType {
        LOOP(true), PLAY_ONCE, HOLD_ON_LAST_FRAME;

        private final boolean looping;

        EDefaultLoopTypes(boolean looping) {
            this.looping = looping;
        }

        EDefaultLoopTypes() {
            this(false);
        }

        @Override
        public boolean isRepeatingAfterEnd() {
            return looping;
        }
    }
}
