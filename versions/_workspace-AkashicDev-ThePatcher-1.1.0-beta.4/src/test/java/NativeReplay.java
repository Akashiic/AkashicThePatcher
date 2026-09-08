package com.akashic.thepatcher.module.dimdoors;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.world.World;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.nbt.NBTTagCompound;
import org.dimdev.dimdoors.core.*;
import org.dimdev.dimdoors.tileentities.TileEntityDimDoor;
import org.dimdev.dimdoors.watcher.ClientLinkData;
import org.dimdev.dimdoors.util.Point4D;
import io.netty.buffer.*;

public final class NativeReplay {
    static int checks;
    static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
    static Object allocate(Class<?> type)throws Exception{
        Class<?> u=Class.forName("sun.misc.Unsafe");Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);
        return u.getMethod("allocateInstance",Class.class).invoke(f.get(null),type);
    }
    static void field(Object obj,Class<?> type,String name,Object value)throws Exception{
        Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(obj,value);
    }
    static final class Context implements DimDoorsReflectionAccess.ClientContext {
        Object world,net;public Object world(){return world;}public Object network(){return net;}
        public boolean local(){return false;}
    }
    static NBTTagCompound tag(int x,int y,int z,int orientation) throws Exception {
        NBTTagCompound tag=new NBTTagCompound();
        tag.func_74768_a("x",x);tag.func_74768_a("y",y);tag.func_74768_a("z",z);
        tag.func_74768_a("orientation",orientation);tag.func_74757_a("openOrClosed",true);
        tag.func_74757_a("hasExit",true);tag.func_74757_a("isDungeonChainLink",true);
        NBTTagCompound linkTag=new NBTTagCompound();
        Constructor<?> ctor=Arrays.stream(ClientLinkData.class.getDeclaredConstructors()).filter(c -> c.getParameterTypes().length==3).findFirst().get();
        ctor.setAccessible(true);((ClientLinkData)ctor.newInstance(new Point4D(x,y,z,0),LinkType.CLIENT,null)).writeToNBT(linkTag);
        tag.func_74782_a("Link",linkTag);return tag;
    }
    public static void main(String[] args)throws Exception {
        Context context=new Context();
        World world=(World)allocate(Class.forName("fixture.ControlledWorld"));
        field(world,World.class,"field_72995_K",true);
        NetworkManager network=(NetworkManager)allocate(NetworkManager.class);
        context.world=world;context.net=network;
        TileEntityDimDoor tile=new TileEntityDimDoor();field(tile,Class.forName("net.minecraft.tileentity.TileEntity"),"field_145850_b",world);
        tile.field_145851_c=8;tile.field_145848_d=65;tile.field_145849_e=8;
        world.getClass().getField("current").set(world,tile);
        DimDoorsClientRuntime.access=new DimDoorsReflectionAccess(context);
        DimDoorsClientRuntime.noteClientThread();
        PocketManager.registerLinkWatcher(new PocketManager.ClientLinkWatcher());
        S35PacketUpdateTileEntity packet=new S35PacketUpdateTileEntity(8,65,8,1,tag(8,65,8,3));
        TileEntityDimDoor expected=new TileEntityDimDoor();
        expected.func_145839_a(packet.func_148857_g());
        if(args.length>0 && args[0].equals("baseline")) {
            try{tile.onDataPacket(network,packet);throw new AssertionError("Expected original S35 crash");}
            catch(NullPointerException failure){
                check(failure.getStackTrace()[0].getLineNumber()==494,"same crashing line");
                check(tile.orientation==expected.orientation && tile.hasExit==expected.hasExit && tile.openOrClosed==expected.openOrClosed,"NBT matches independent native decoder before original crash");
                System.out.println("NATIVE_S35_BASELINE_CRASH_PASS checks="+checks);return;
            }
        }
        check(PocketManager.getDimensionData(0)==null,"uninitialized lookup is nullable");
        tile.onDataPacket(network,packet);
        check(tile.orientation==expected.orientation && tile.hasExit==expected.hasExit && tile.openOrClosed==expected.openOrClosed && tile.isDungeonChainLink==expected.isDungeonChainLink,"native NBT retained");
        check(DimDoorsClientRuntime.QUEUE.size()==1,"S35 link queued before snapshot");
        DimDoorsClientRuntime.tick();check(DimDoorsClientRuntime.QUEUE.size()==1,"no invented dimension");
        // Construct a real, root-only DimDoors snapshot for the fixture, then receive it through the native reader.
        PocketManager.load();
        NewDimData root=PocketManager.createDimensionDataDangerously(0);
        check(root!=null,"native root fixture created");
        ByteBuf wire=Unpooled.buffer();PocketManager.writePacket(wire);
        field(null,PocketManager.class,"dimensionData",null);field(null,PocketManager.class,"rootDimensions",null);
        field(null,PocketManager.class,"isLoaded",false);field(null,PocketManager.class,"isLoading",false);
        field(null,PocketManager.class,"isConnected",false);
        PocketManager.readPacket(wire);wire.release();
        check(PocketManager.isLoaded() && PocketManager.isConnected,"native snapshot accepted");
        root=PocketManager.getDimensionData(0);check(root!=null,"authoritative root restored");
        DimDoorsClientRuntime.tick();
        check(DimDoorsClientRuntime.QUEUE.size()==0,"notification replayed");
        check(root.getLink(8,65,8)!=null,"real native link created at correct point");
        Object existing=root.getLink(8,65,8);tile.onDataPacket(network,packet);
        check(root.getLink(8,65,8)==existing,"native duplicate handling preserved");
        field(null,PocketManager.class,"dimensionData",null);
        tile.onDataPacket(network,packet);check(DimDoorsClientRuntime.QUEUE.size()==1,"unloaded map safely defers");
        context.world=allocate(Class.forName("fixture.ControlledWorld"));DimDoorsClientRuntime.tick();
        check(DimDoorsClientRuntime.QUEUE.size()==0,"proxy world identity rejects stale link");
        System.out.println("NATIVE_S35_AND_SNAPSHOT_PASS checks="+checks+"; actual DimDoors 2.2.6, NBT, S35, Compactor and watcher; controlled World/FML context");
    }
}
