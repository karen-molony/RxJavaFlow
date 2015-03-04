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
package rxjf.internal.operators;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import rx.Flowable;
import rx.Flowable.OnSubscribe;
import rx.Subscriber;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

/**
 * This method has similar behavior to {@link Flowable#replay()} except that this auto-subscribes
 * to the source Flowable rather than returning a connectable Flowable.
 * <p>
 * <img width="640" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/cache.png" alt="">
 * <p>
 * This is useful with an Flowable that you want to cache responses when you can't control the
 * subscribe/unsubscribe behavior of all the Observers.
 * <p>
 * <em>Note:</em> You sacrifice the ability to unsubscribe from the origin when you use this operator, so be
 * careful not to use this operator on Flowables that emit infinite or very large numbers of
 * items, as this will use up memory.
 * 
 * @param <T>
 *            the cached value type
 */
public final class OnSubscribeCache<T> implements OnSubscribe<T> {
    protected final Flowable<? extends T> source;
    protected final Subject<? super T, ? extends T> cache;
    volatile int sourceSubscribed;
    @SuppressWarnings("rawtypes")
    static final AtomicIntegerFieldUpdater<OnSubscribeCache> SRC_SUBSCRIBED_UPDATER
            = AtomicIntegerFieldUpdater.newUpdater(OnSubscribeCache.class, "sourceSubscribed");

    public OnSubscribeCache(Flowable<? extends T> source) {
        this(source, ReplaySubject.<T> create());
    }

    public OnSubscribeCache(Flowable<? extends T> source, int capacity) {
        this(source, ReplaySubject.<T> create(capacity));
    }

    /* accessible to tests */OnSubscribeCache(Flowable<? extends T> source, Subject<? super T, ? extends T> cache) {
        this.source = source;
        this.cache = cache;
    }

    @Override
    public void call(Subscriber<? super T> s) {
        if (SRC_SUBSCRIBED_UPDATER.compareAndSet(this, 0, 1)) {
            source.subscribe(cache);
            /*
             * Note that we will never unsubscribe from 'source' unless we receive `onComplete()` or `onError`,
             * as we want to receive and cache all of its values.
             * 
             * This means this should never be used on an infinite or very large sequence, similar to toList().
             */
        }
        cache.unsafeSubscribe(s);
    }
}
