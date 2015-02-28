/**
 * Copyright 2014 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rxjf.internal.schedulers;

import static rxjf.internal.UnsafeAccess.UNSAFE;

import java.util.concurrent.*;

import rxjf.cancellables.*;
import rxjf.internal.UnsafeAccess;
import rxjf.schedulers.Scheduler;

public class EventLoopsScheduler implements Scheduler {
    /** Manages a fixed number of workers. */
    private static final String THREAD_NAME_PREFIX = "RxComputationThreadPool-";
    private static final RxThreadFactory THREAD_FACTORY = new RxThreadFactory(THREAD_NAME_PREFIX);
    /** 
     * Key to setting the maximum number of computation scheduler threads.
     * Zero or less is interpreted as use available. Capped by available.
     */
    static final String KEY_MAX_THREADS = "rx.scheduler.max-computation-threads";
    /** The maximum number of computation scheduler threads. */
    static final int MAX_THREADS;
    static {
        int maxThreads = Integer.getInteger(KEY_MAX_THREADS, 0);
        int ncpu = Runtime.getRuntime().availableProcessors();
        int max;
        if (maxThreads <= 0 || maxThreads > ncpu) {
            max = ncpu;
        } else {
            max = maxThreads;
        }
        MAX_THREADS = max;
    }
    static final class FixedSchedulerPool {
        final int cores;

        final PoolWorker[] eventLoops;
        volatile long n;
        static final long N = UnsafeAccess.addressOf(FixedSchedulerPool.class, "n");

        FixedSchedulerPool() {
            // initialize event loops
            this.cores = MAX_THREADS;
            this.eventLoops = new PoolWorker[cores];
            for (int i = 0; i < cores; i++) {
                this.eventLoops[i] = new PoolWorker(THREAD_FACTORY);
            }
        }

        public PoolWorker getEventLoop() {
            // simple round robin, improvements to come
            int idx = (int)UNSAFE.getAndAddLong(this, N, 1);
            return eventLoops[idx % cores];
        }
    }

    final FixedSchedulerPool pool;
    
    /**
     * Create a scheduler with pool size equal to the available processor
     * count and using least-recent worker selection policy.
     */
    public EventLoopsScheduler() {
        pool = new FixedSchedulerPool();
    }
    
    @Override
    public Worker createWorker() {
        return new EventLoopWorker(pool.getEventLoop());
    }
    
    /**
     * Schedules the action directly on one of the event loop workers
     * without the additional infrastructure and checking.
     * @param action the action to schedule
     * @return the subscription
     */
    public Cancellable scheduleDirect(Runnable action) {
       PoolWorker pw = pool.getEventLoop();
       return pw.scheduleActual(action, -1, TimeUnit.NANOSECONDS);
    }

    private static class EventLoopWorker implements Scheduler.Worker {
        private final CompositeCancellable composite = new CompositeCancellable();
        private final PoolWorker poolWorker;

        EventLoopWorker(PoolWorker poolWorker) {
            this.poolWorker = poolWorker;
        }

        @Override
        public void cancel() {
            composite.cancel();
        }

        @Override
        public boolean isCancelled() {
            return composite.isCancelled();
        }

        @Override
        public Cancellable schedule(Runnable action) {
            if (isCancelled()) {
                return Cancellable.CANCELLED;
            }
            ScheduledRunnable s = poolWorker.scheduleActual(action, 0, null);
            
            composite.add(s);
            s.addParent(composite);
            
            return s;
        }
        @Override
        public Cancellable schedule(Runnable action, long delayTime, TimeUnit unit) {
            if (isCancelled()) {
                return Cancellable.CANCELLED;
            }
            ScheduledRunnable s = poolWorker.scheduleActual(action, delayTime, unit, composite);
            
            return s;
        }
    }
    
    private static final class PoolWorker extends NewThreadWorker {
        PoolWorker(ThreadFactory threadFactory) {
            super(threadFactory);
        }
    }
}
