package com.akashic.chromaplayerapifix;

import net.minecraft.launchwrapper.IClassTransformer;

/** Stands for the standalone Akashic-ChromaPlayerApiPreloadFix 1.0.0 jar's armed Hide transformer. Never shipped. */
public class ChromaPlayerApiPreloadFixPlugin {
    public static final class Hide implements IClassTransformer {
        @Override
        public byte[] transform(String name, String transformedName, byte[] bytes) {
            return bytes;
        }
    }
}
