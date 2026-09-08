package com.akashic.thepatcher.module.dimdoors;
import java.io.*;
import com.akashic.thepatcher.core.ModuleGate;
public final class SideScopeTest {
    public static void main(String[] args)throws Exception {
        File root=new File(args[0]);new File(root,"config").mkdirs();
        ModuleGate.bootstrap(root);
        if(args.length>1)try(FileWriter out=new FileWriter(new File(root,"config/akashic-thepatcher.properties"))){
            out.write(DimDoorsClientModule.KEY+"="+args[1]+"\n");
        }
        String[] input={"previous.one","previous.two"};
        boolean client=System.getProperty("fixture.side","CLIENT").equals("CLIENT");
        boolean enabled=args.length==1 || args[1].equals("true");
        String[] output=DimDoorsClientModule.extendTransformers(input);
        if(client && enabled){
            if(output.length!=3 || !output[0].equals(input[0]) || !output[1].equals(input[1]))throw new AssertionError("original order");
            if(DimDoorsClientModule.extendTransformers(output)!=output)throw new AssertionError("duplicate registration");
        }else if(output!=input)throw new AssertionError("server or disabled scope changed");
        System.out.println("SIDE_SCOPE_PASS side="+System.getProperty("fixture.side","CLIENT")+" enabled="+enabled);
    }
}
