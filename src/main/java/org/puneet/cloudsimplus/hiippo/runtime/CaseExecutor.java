package org.puneet.cloudsimplus.hiippo.runtime;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** One bounded window of independent cases; only its owner publishes shared evidence. */
final class CaseExecutor<T> implements AutoCloseable {
    private record Failure(int index,Throwable cause) {}
    private final ExecutorService executor;
    private final List<Future<T>> futures=new ArrayList<>();
    private final AtomicReference<Failure> failure=new AtomicReference<>();
    private final CountDownLatch ready=new CountDownLatch(1);

    static int workers() {
        String value=System.getProperty("cloudsim.workers","1");
        if(!value.matches("[1-9][0-9]?")) throw new IllegalArgumentException("cloudsim.workers must be an integer from 1 to 32");
        int workers=Integer.parseInt(value);
        if(workers>32) throw new IllegalArgumentException("cloudsim.workers must be an integer from 1 to 32");
        return workers;
    }
    CaseExecutor(int workers,List<? extends Callable<T>> tasks) {
        this(workers,workers,tasks);
    }
    CaseExecutor(int workers,int pendingLimit,List<? extends Callable<T>> tasks) {
        if(workers<1 || workers>32 || pendingLimit<workers || pendingLimit>4*workers || pendingLimit>128
            || tasks.isEmpty() || tasks.size()>pendingLimit) throw new IllegalArgumentException("Invalid case window");
        executor=new ThreadPoolExecutor(workers,workers,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(pendingLimit),
            r->new Thread(r,"cloudsim-case"));
        for(int i=0;i<tasks.size();i++) {
            final int index=i; var task=tasks.get(i);
            futures.add(executor.submit(()->{
                ready.await();
                try { return task.call(); }
                catch(Throwable error) {
                    if(failure.compareAndSet(null,new Failure(index,error))) {
                        // The window was fully registered before any task started.
                        for(var future:futures) future.cancel(true);
                        executor.shutdownNow();
                    }
                    throw error;
                }
            }));
        }
        ready.countDown();
    }
    T await(int index) throws Exception {
        return await(index,null);
    }
    @FunctionalInterface interface ProgressCallback { void publish() throws Exception; }
    T await(int index,ProgressCallback progress) throws Exception {
        try {
            if(progress==null) return futures.get(index).get();
            while(true) try { return futures.get(index).get(1,TimeUnit.SECONDS); }
            catch(TimeoutException waiting) { progress.publish(); }
        }
        catch(ExecutionException e) { return rethrow(e.getCause()); }
        catch(CancellationException e) {
            var failed=failure.get();
            if(failed!=null && failed.index()==index) return rethrow(failed.cause());
            throw new InterruptedException("Case cancelled after case "+(failed==null?"window":failed.index())+" failed"+
                (failed==null?"":": "+failed.cause()));
        }
    }
    private T rethrow(Throwable cause) throws Exception {
        if(cause instanceof Exception e) throw e;
        throw new IllegalStateException("Case worker failed: "+cause,cause);
    }
    @Override public void close() {
        for(var future:futures) future.cancel(true);
        executor.shutdownNow();
        boolean interrupted=false;
        // Do not close shared outputs or delete spools while owned workers are writing.
        while(!executor.isTerminated()) try { executor.awaitTermination(1,TimeUnit.SECONDS); }
        catch(InterruptedException e) { interrupted=true; }
        if(interrupted) Thread.currentThread().interrupt();
    }
}
