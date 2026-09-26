# Beta.34 → beta.35: verified artifact diff

Based on SHA-256 comparison of the ZIP entries in the two local JARs, only these entries differed:

1. `com/akashic/forbiddenbackhand/core/BackhandBackendTransformer.class`
2. `META-INF/MANIFEST.MF` — `Implementation-Version` changed to `1.1.0-beta.35`
3. `mcmod.info` — `version` changed to `1.1.0-beta.35`

The recovered class shows these selector changes compared to beta.34:

```diff
- NET_HANDLER = "net/minecraft/client/network/NetHandlerPlayClient"
+ NET_HANDLER = "bjb"

- PACKET_DESC = "(Lnet/minecraft/network/Packet;)V"
+ PACKET_DESC = "(Lft;)V"

- obfuscated PlayerController method alias = "j"
+ obfuscated PlayerController method alias = "k"

- JoinGame packet descriptor starts with "(Lnet/minecraft/network/play/server/S01PacketJoinGame;"
+ JoinGame packet descriptor starts with "(Lhd;"
```

The `k` alias is checked at two call sites in the class. The JAR entry count remained 159. The compared beta.35 JAR SHA-256 is `C48D3EC3B1EF35F907BACA47CD78EAD48730B4CD556592E529C0140D7A869C17`.
