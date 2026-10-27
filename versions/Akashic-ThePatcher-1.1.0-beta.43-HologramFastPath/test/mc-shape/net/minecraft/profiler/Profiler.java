package net.minecraft.profiler;
/** Gate shape: records the sections in order. Never shipped. */
public class Profiler {
    public final java.util.List<String> sections = new java.util.ArrayList<String>();
    public void endStartSection(String name) { sections.add(name); }
}
