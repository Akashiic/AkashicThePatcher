package cpw.mods.fml.common.network.simpleimpl;

import cpw.mods.fml.relauncher.Side;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;

/** FML's MessageContext as seen at run time (the universal JAR on the class path names Minecraft classes obfuscated). */
public class MessageContext {
    public final INetHandler netHandler;
    public final Side side;

    public MessageContext(INetHandler netHandler, Side side) {
        this.netHandler = netHandler;
        this.side = side;
    }

    public NetHandlerPlayServer getServerHandler() {
        return (NetHandlerPlayServer) netHandler;
    }
}
