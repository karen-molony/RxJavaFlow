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
package rx.internal.operators;

import java.util.concurrent.*;

import rx.Flow.Subscriber;
import rx.*;
import rx.Observable.Operator;
import rx.disposables.SerialDisposable;
import rx.internal.subscriptions.*;
import rx.schedulers.Scheduler;
import rx.subscribers.*;

/**
 * Applies a timeout policy for each element in the observable sequence, using
 * the specified scheduler to run timeout timers. If the next element isn't
 * received within the specified timeout duration starting from its predecessor,
 * the other observable sequence is used to produce future messages from that
 * point on.
 */
public final class OperatorTimeout<T> implements Operator<T, T> {

    final long timeout;
    final TimeUnit timeUnit;
    final Observable<? extends T> other;
    final Scheduler scheduler;

    public OperatorTimeout(long timeout, TimeUnit timeUnit, 
            Observable<? extends T> other, Scheduler scheduler) {
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.other = other;
        this.scheduler = scheduler;
    }
    
    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> child) {

        SubscriptionArbiter<T> arbiter = new SubscriptionArbiter<>(child);
        CompositeDisposableSubscription disposable = new CompositeDisposableSubscription(arbiter);

        Scheduler.Worker worker = scheduler.createWorker();
        SerialDisposable timeouts = new SerialDisposable();

        disposable.add(worker);
        disposable.add(timeouts);
        
        
        return new AbstractSubscriber<T>() {
            @Override
            protected void onSubscribe() {
                child.onSubscribe(disposable);
                arbiter.set(subscription);
                scheduleTimeout();
            }
            @Override
            public void onNext(T item) {
                arbiter.onNext(item);
                scheduleTimeout();
            }
            @Override
            public void onError(Throwable throwable) {
                arbiter.onError(throwable);
            }
            @Override
            public void onComplete() {
                arbiter.onComplete();
            }
            
            void scheduleTimeout() {
                timeouts.set(worker.schedule(() -> {
                    if (other == null) {
                        arbiter.onError(new TimeoutException());
                    } else {
                        subscribeOther();
                    }
                }, timeout, timeUnit));
            }
            void subscribeOther() {
                other.unsafeSubscribe(new AbstractSubscriber<T>() {
                    @Override
                    protected void onSubscribe() {
                        arbiter.set(subscription);
                    }
                    @Override
                    public void onNext(T item) {
                        arbiter.onNext(item);
                    }
                    @Override
                    public void onError(Throwable throwable) {
                        arbiter.onError(throwable);
                    }
                    @Override
                    public void onComplete() {
                        arbiter.onComplete();
                    }
                });
            }
        };
    }
}
