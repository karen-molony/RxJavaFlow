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
package rxjf.schedulers;

import java.util.*;
import java.util.concurrent.TimeUnit;

import rxjf.disposables.*;
import rxjf.internal.disposables.BooleanDisposable;

/**
 * The {@code TestScheduler} is useful for debugging. It allows you to test schedules of events by manually
 * advancing the clock at whatever pace you choose.
 */
public final class TestScheduler implements Scheduler {
    private final Queue<TimedAction> queue = new PriorityQueue<>(11, new CompareActionsByTime());
    private static long counter = 0;

    private static final class TimedAction {

        private final long time;
        private final Runnable action;
        private final Worker scheduler;
        private final long count = counter++; // for differentiating tasks at same time

        private TimedAction(Worker scheduler, long time, Runnable action) {
            this.time = time;
            this.action = action;
            this.scheduler = scheduler;
        }

        @Override
        public String toString() {
            return String.format("TimedAction(time = %d, action = %s)", time, action.toString());
        }
    }

    private static class CompareActionsByTime implements Comparator<TimedAction> {
        @Override
        public int compare(TimedAction action1, TimedAction action2) {
            if (action1.time == action2.time) {
                return Long.valueOf(action1.count).compareTo(Long.valueOf(action2.count));
            } else {
                return Long.valueOf(action1.time).compareTo(Long.valueOf(action2.time));
            }
        }
    }

    // Storing time in nanoseconds internally.
    private long time;

    @Override
    public long now() {
        return TimeUnit.NANOSECONDS.toMillis(time);
    }

    /**
     * Moves the Scheduler's clock forward by a specified amount of time.
     *
     * @param delayTime
     *          the amount of time to move the Scheduler's clock forward
     * @param unit
     *          the units of time that {@code delayTime} is expressed in
     */
    public void advanceTimeBy(long delayTime, TimeUnit unit) {
        advanceTimeTo(time + unit.toNanos(delayTime), TimeUnit.NANOSECONDS);
    }

    /**
     * Moves the Scheduler's clock to a particular moment in time.
     *
     * @param delayTime
     *          the point in time to move the Scheduler's clock to
     * @param unit
     *          the units of time that {@code delayTime} is expressed in
     */
    public void advanceTimeTo(long delayTime, TimeUnit unit) {
        long targetTime = unit.toNanos(delayTime);
        triggerActions(targetTime);
    }

    /**
     * Triggers any actions that have not yet been triggered and that are scheduled to be triggered at or
     * before this Scheduler's present time.
     */
    public void triggerActions() {
        triggerActions(time);
    }

    private void triggerActions(long targetTimeInNanos) {
        while (!queue.isEmpty()) {
            TimedAction current = queue.peek();
            if (current.time > targetTimeInNanos) {
                break;
            }
            // if scheduled time is 0 (immediate) use current virtual time
            time = current.time == 0 ? time : current.time;
            queue.remove();

            // Only execute if not unsubscribed
            if (!current.scheduler.isDisposed()) {
                current.action.run();
            }
        }
        time = targetTimeInNanos;
    }

    @Override
    public Worker createWorker() {
        return new InnerTestScheduler();
    }

    private final class InnerTestScheduler implements Worker {

        private final BooleanDisposable s = new BooleanDisposable();

        @Override
        public void dispose() {
            s.dispose();
        }

        @Override
        public boolean isDisposed() {
            return s.isDisposed();
        }

        @Override
        public Disposable schedule(Runnable action, long delayTime, TimeUnit unit) {
            final TimedAction timedAction = new TimedAction(this, time + unit.toNanos(delayTime), action);
            queue.add(timedAction);
            return new BooleanDisposable(() -> queue.remove(timedAction));
        }

        @Override
        public Disposable schedule(Runnable action) {
            final TimedAction timedAction = new TimedAction(this, 0, action);
            queue.add(timedAction);
            return new BooleanDisposable(() -> queue.remove(timedAction));
        }

        @Override
        public long now() {
            return TestScheduler.this.now();
        }

    }

}
