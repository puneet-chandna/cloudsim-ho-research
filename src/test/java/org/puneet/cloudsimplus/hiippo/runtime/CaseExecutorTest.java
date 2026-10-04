package org.puneet.cloudsimplus.hiippo.runtime;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CaseExecutorTest {
    private List<Callable<Integer>> tasks(int count,AtomicInteger started) {
        var result=new ArrayList<Callable<Integer>>();
        for(int i=0;i<count;i++) { int index=i; result.add(()->{ started.incrementAndGet(); return index; }); }
        return result;
    }
    @Test void researchDefaultRejectsMorePendingCasesThanWorkers() {
        var started=new AtomicInteger();
        assertThrows(IllegalArgumentException.class,()->new CaseExecutor<>(2,tasks(3,started)));
        assertEquals(0,started.get());
    }
    @Test void explicitLookaheadRejectsOverflowBeforeStartingAnyTask() {
        var started=new AtomicInteger();
        assertThrows(IllegalArgumentException.class,()->new CaseExecutor<>(2,8,tasks(9,started)));
        assertThrows(IllegalArgumentException.class,()->new CaseExecutor<>(32,128,tasks(129,started)));
        assertThrows(IllegalArgumentException.class,()->new CaseExecutor<>(32,129,tasks(1,started)));
        assertThrows(IllegalArgumentException.class,()->new CaseExecutor<>(2,1,tasks(1,started)));
        assertEquals(0,started.get());
    }
    @Test void boundedLookaheadCompletesEveryCaseWithOnlyTwoActiveWorkers() throws Exception {
        var entered=new CountDownLatch(2); var active=new AtomicInteger(); var maximum=new AtomicInteger();
        var tasks=new ArrayList<Callable<Integer>>();
        for(int i=0;i<8;i++) {
            int index=i;
            tasks.add(()->{
                maximum.accumulateAndGet(active.incrementAndGet(),Math::max); entered.countDown();
                try { if(!entered.await(3,TimeUnit.SECONDS)) throw new IllegalStateException("Workers did not overlap"); return index; }
                finally { active.decrementAndGet(); }
            });
        }
        try(var executor=new CaseExecutor<>(2,8,tasks)) { for(int i=0;i<8;i++) assertEquals(i,executor.await(i)); }
        assertEquals(2,maximum.get()); assertEquals(0,active.get());
    }
}
