package cpw.mods.fml.common.network;

import io.netty.util.AttributeKey;
import net.minecraft.network.INetHandler;

/** Only the attribute InvTweaks reads (the universal JAR's class needs obfuscated Minecraft classes to load). */
public class NetworkRegistry {
    public static final AttributeKey<INetHandler> NET_HANDLER = new AttributeKey<INetHandler>("fml:nethandler");
}
