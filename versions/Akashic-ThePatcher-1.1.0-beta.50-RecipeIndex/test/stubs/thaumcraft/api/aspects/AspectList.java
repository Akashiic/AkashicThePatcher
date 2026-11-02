package thaumcraft.api.aspects;

/** Gate stub: the shape returns which recipe object won and its cost. */
public class AspectList {
    public final Object recipe;
    public final int cost;

    public AspectList(Object recipe, int cost) {
        this.recipe = recipe;
        this.cost = cost;
    }
}
