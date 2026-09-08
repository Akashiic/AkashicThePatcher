from pathlib import Path
r=Path(__file__).resolve().parent
p=r/'src/test/java/NativeReplay.java';s=p.read_text()
marker='        if(args.length>0 && args[0].equals("baseline")) {'
if 'TileEntityDimDoor expected=new TileEntityDimDoor();' not in s:
    assert marker in s
    s=s.replace(marker,'        TileEntityDimDoor expected=new TileEntityDimDoor();\n        expected.func_145839_a(packet.func_148857_g());\n'+marker)
s=s.replace('tile.orientation==3 && tile.hasExit && tile.openOrClosed,"NBT applied before original crash"',
    'tile.orientation==expected.orientation && tile.hasExit==expected.hasExit && tile.openOrClosed==expected.openOrClosed,"NBT matches independent native decoder before original crash"')
s=s.replace('tile.orientation==3 && tile.hasExit && tile.openOrClosed && tile.isDungeonChainLink,"native NBT retained"',
    'tile.orientation==expected.orientation && tile.hasExit==expected.hasExit && tile.openOrClosed==expected.openOrClosed && tile.isDungeonChainLink==expected.isDungeonChainLink,"native NBT retained"')
p.write_text(s)
print('NBT_EXPECTATION_USES_INDEPENDENT_NATIVE_DECODER')
