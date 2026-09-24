package com.akashic.compatlab.tools;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.akashic.thepatcher.module.forestry.ForestryGenomeC2Transformer;

public final class RunForestryTransform {
    private RunForestryTransform() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("expected forestry.jar output.class");
        }

        byte[] original;
        try (ZipFile zip = new ZipFile(args[0])) {
            ZipEntry entry = zip.getEntry("forestry/core/genetics/Genome.class");
            if (entry == null) throw new IllegalStateException("Genome.class missing");
            try (InputStream in = zip.getInputStream(entry)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                }
                original = out.toByteArray();
            }
        }

        ForestryGenomeC2Transformer transformer =
            new ForestryGenomeC2Transformer();
        byte[] patched = transformer.transform(
            "forestry.core.genetics.Genome",
            "forestry.core.genetics.Genome",
            original);

        if (patched == null) {
            throw new IllegalStateException("transform returned null");
        }

        Path output = Paths.get(args[1]);
        Files.createDirectories(output.getParent());
        Files.write(output, patched);

        System.out.println(
            "RUN_FORESTRY_TRANSFORM PASS original="
                + original.length + " patched=" + patched.length
                + " property="
                + System.getProperty(
                    "akashic.forestryUniversalPatch.applied"));
    }
}