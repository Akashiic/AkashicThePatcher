package software.bernie.geckolib3.core.builder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import software.bernie.geckolib3.core.keyframe.BoneAnimation;

/**
 * Shape of GeckoLib Unofficial 1.7.10 1.0.4's Animation (for the beta.51 gate): the same fields, constructor and
 * serialization copy (ObjectOutputStream, Base64, ObjectInputStream; on any exception, the original), as javap shows
 * the shipped class.
 */
public class Animation implements Serializable {

    private static final long serialVersionUID = 1L;

    public String animationName;
    public Double animationLength;
    public ILoopType loop = ILoopType.EDefaultLoopTypes.PLAY_ONCE;
    public List<BoneAnimation> boneAnimations;
    public List<Object> soundKeyFrames = new ArrayList<Object>();
    public List<Object> particleKeyFrames = new ArrayList<Object>();
    public List<Object> customInstructionKeyframes = new ArrayList<Object>();

    public static Animation copy(Animation animation) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bytes);
            out.writeObject(animation);
            out.flush();
            String text = Base64.getEncoder().encodeToString(bytes.toByteArray());
            byte[] decoded = Base64.getDecoder().decode(text);
            ByteArrayInputStream in = new ByteArrayInputStream(decoded);
            ObjectInputStream objects = new ObjectInputStream(in);
            return (Animation) objects.readObject();
        } catch (Exception e) {
            return animation;
        }
    }
}
