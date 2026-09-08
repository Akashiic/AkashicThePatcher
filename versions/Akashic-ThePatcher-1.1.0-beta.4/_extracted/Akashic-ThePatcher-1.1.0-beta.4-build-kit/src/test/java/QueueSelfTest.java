package com.akashic.thepatcher.module.dimdoors;
import java.util.*;

public final class QueueSelfTest {
    static int checks;
    static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    static final class Tile { final Object world; boolean valid = true; Tile(Object w) { world = w; } }
    static final class Fixture implements DeferredLinks.Access {
        Object world = new Object(), net = new Object(); boolean ready, succeeds = true;
        int calls; Object last;
        public Object world(Object tile) { return ((Tile)tile).world; }
        public boolean current(Object tile,Object w,Object n) { return w==world && n==net && ((Tile)tile).valid; }
        public boolean ready(Object watcher,Object link) { return ready; }
        public boolean apply(Object watcher,Object link) { calls++;last=link;return succeeds; }
    }
    public static void main(String[] args) throws Exception {
        Fixture f=new Fixture(); DeferredLinks q=new DeferredLinks(); Tile t=new Tile(f.world); Object link=new Object();
        q.submit(f,link,t,f.net,f,true); check(q.size()==1 && f.calls==0,"wait without losing update");
        q.tick(f); check(q.size()==1 && f.calls==0,"no fabricated metadata");
        f.ready=true; q.tick(f); check(q.size()==0 && f.calls==1 && f.last==link,"replay exact object");
        q.tick(f); check(f.calls==1,"exactly once");
        f.ready=false; q.submit(f,link,t,f.net,f,true); Object newer=new Object();
        q.submit(f,newer,t,f.net,f,true); check(q.size()==1 && q.replaced==1,"coalescing");
        f.ready=true; q.tick(f); check(f.last==newer,"latest notification wins");
        f.ready=false; q.submit(f,link,t,f.net,f,true); f.world=new Object();
        f.ready=true; q.tick(f); check(q.size()==0 && q.stale==1,"same dimension new world cannot replay old state");
        f=new Fixture();q=new DeferredLinks();t=new Tile(f.world);
        q.submit(f,link,t,f.net,f,true); f.net=new Object();f.ready=true;q.tick(f);
        check(q.size()==0 && f.calls==0,"new connection cannot replay");
        f=new Fixture();q=new DeferredLinks();t=new Tile(f.world);
        q.submit(f,link,t,f.net,f,true);q.invalidate();f.ready=true;q.tick(f);
        check(q.size()==0 && f.calls==0,"disconnect invalidates generation");
        f=new Fixture();q=new DeferredLinks();t=new Tile(f.world);
        q.submit(f,link,t,f.net,f,true);t.valid=false;f.ready=true;q.tick(f);
        check(q.size()==0 && f.calls==0,"replaced or invalid tile discarded");
        f=new Fixture();q=new DeferredLinks();t=new Tile(f.world);
        q.submit(f,link,t,f.net,f,true);for(int i=0;i<DeferredLinks.TTL;i++)q.tick(f);
        check(q.size()==0 && q.expired==1 && f.calls==0,"bounded lifetime without metadata");
        f=new Fixture();q=new DeferredLinks();List<Tile> strong=new ArrayList<>();
        for(int i=0;i<600;i++){t=new Tile(f.world);strong.add(t);q.submit(f,link,t,f.net,f,true);}
        check(q.size()==512 && q.overflow==88,"hard capacity");
        f.ready=true;q.tick(f);check(f.calls==64 && q.size()==448,"per tick budget");
        for(int i=0;i<7;i++)q.tick(f);check(q.size()==0 && f.calls==512,"bounded backlog drains");
        f=new Fixture();q=new DeferredLinks();t=new Tile(f.world);f.ready=true;
        q.submit(f,link,t,f.net,f,false);check(f.calls==0 && q.size()==1,"off-thread notifications only queued");
        q.tick(f);check(f.calls==1 && q.size()==0,"main tick processes notification");
        f=new Fixture();q=new DeferredLinks();t=new Tile(f.world);f.ready=true;f.succeeds=false;
        q.submit(f,link,t,f.net,f,true);check(q.size()==1,"unload race does not claim success");
        f.succeeds=true;q.tick(f);check(q.size()==0,"retry after valid metadata returns");
        f=new Fixture();q=new DeferredLinks();t=new Tile(null);q.submit(f,link,t,f.net,f,true);
        check(q.size()==0 && q.stale==1,"missing world rejected");
        check(DimDoorsClientRuntime.safeMapGet(null,0)==null,"safe null lookup");
        Map<Integer,Object> map=new HashMap<>();map.put(0,link);
        check(DimDoorsClientRuntime.safeMapGet(map,0)==link,"existing dimension identity retained");
        System.out.println("QUEUE_SELF_TEST_PASS checks="+checks);
    }
}
