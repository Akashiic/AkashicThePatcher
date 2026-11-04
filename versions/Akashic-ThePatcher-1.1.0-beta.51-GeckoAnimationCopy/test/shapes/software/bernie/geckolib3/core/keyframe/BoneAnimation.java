package software.bernie.geckolib3.core.keyframe;

import java.io.Serializable;
import java.util.List;

/** Shape of GeckoLib's BoneAnimation (for the beta.51 gate). */
public class BoneAnimation implements Serializable {

    private static final long serialVersionUID = 1L;

    public String boneName;
    public VectorKeyFrameList<KeyFrame<Double>> rotationKeyFrames;
    public VectorKeyFrameList<KeyFrame<Double>> positionKeyFrames;
    public VectorKeyFrameList<KeyFrame<Double>> scaleKeyFrames;

    public static final class VectorKeyFrameList<T> implements Serializable {

        private static final long serialVersionUID = 1L;

        public List<T> xKeyFrames;
        public List<T> yKeyFrames;
        public List<T> zKeyFrames;

        public VectorKeyFrameList(List<T> x, List<T> y, List<T> z) {
            xKeyFrames = x;
            yKeyFrames = y;
            zKeyFrames = z;
        }
    }

    public static final class KeyFrame<T> implements Serializable {

        private static final long serialVersionUID = 1L;

        public Double length;
        public T startValue;
        public T endValue;

        public KeyFrame(Double length, T startValue, T endValue) {
            this.length = length;
            this.startValue = startValue;
            this.endValue = endValue;
        }
    }
}
