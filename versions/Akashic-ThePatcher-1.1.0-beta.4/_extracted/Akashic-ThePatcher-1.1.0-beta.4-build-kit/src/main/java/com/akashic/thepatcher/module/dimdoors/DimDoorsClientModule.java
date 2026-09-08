package com.akashic.thepatcher.module.dimdoors;
import com.akashic.thepatcher.core.ModuleGate;
import com.akashic.thepatcher.core.RuntimeSide;
import com.akashic.thepatcher.util.BundleLog;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import cpw.mods.fml.common.FMLCommonHandler;

public final class DimDoorsClientModule {
    public static final String KEY="module.dimDoorsClientSyncGuard";
    private static final String TRANSFORMER="com.akashic.thepatcher.module.dimdoors.DimDoorsClientTransformer";
    private static Boolean enabled;
    private static boolean installed;
    private DimDoorsClientModule() {}
    public static synchronized boolean enabled() {
        if(enabled!=null)return enabled.booleanValue();
        boolean value=true;
        File file=new File(ModuleGate.gameDirectory(),"config/akashic-thepatcher.properties");
        try {
            if(file.isFile()) {
                if(file.length()>65536)throw new IOException("configuration exceeds 64 KiB");
                Properties config=new Properties();
                try(FileInputStream in=new FileInputStream(file)){config.load(in);}
                String text=config.getProperty(KEY,"true").trim();
                if(!text.equalsIgnoreCase("true")&&!text.equalsIgnoreCase("false"))throw new IOException("invalid boolean");
                value=Boolean.parseBoolean(text);
            }
        } catch(IOException ex) {
            value=false;BundleLog.info("WARN: DimDoors client sync guard disabled: "+ex);
        }
        enabled=Boolean.valueOf(value);
        BundleLog.info("DimDoors client sync guard: "+(value?"ON":"OFF"));
        return value;
    }
    public static String[] extendTransformers(String[] original) {
        if(!RuntimeSide.isClient()||!enabled())return original;
        for(String name:original)if(TRANSFORMER.equals(name))return original;
        String[] result=Arrays.copyOf(original,original.length+1);
        result[original.length]=TRANSFORMER;return result;
    }
    public static synchronized void install() {
        if(installed||!RuntimeSide.isClient()||!enabled())return;
        DimDoorsClientRuntime.noteClientThread();
        FMLCommonHandler.instance().bus().register(new DimDoorsClientLifecycle());
        installed=true;
        BundleLog.info("DimDoors client sync lifecycle installed; bounded delayed links, native tile NBT retained.");
    }
}
