Compile-only stubs of the Minecraft 1.7.10 classes (and of FML's `MessageContext`, whose copy in the Forge
universal JAR uses obfuscated Minecraft names) the hooks call, with the SRG member names the server uses at
run time (checked against MCP's `joined.srg` for 1.7.10). They declare only what the hooks use and are **not**
packaged in the JAR: on the server the real classes are used.
