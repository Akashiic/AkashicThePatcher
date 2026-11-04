package software.bernie.geckolib3.core.builder;

import java.io.Serializable;
import java.util.List;

/** A GeckoLib Animation whose copy is not the serialization copy: the beta.51 transformer must leave it alone. */
public class Animation implements Serializable {

    private static final long serialVersionUID = 1L;

    public String animationName;
    public Double animationLength;
    public ILoopType loop;
    public List<Object> boneAnimations;

    public static Animation copy(Animation animation) {
        if (animation == null) {
            return null;
        }
        Animation c = new Animation();
        c.animationName = animation.animationName;
        c.animationLength = animation.animationLength;
        c.loop = animation.loop;
        c.boneAnimations = new java.util.ArrayList<Object>(animation.boneAnimations);
        return c;
    }
}
