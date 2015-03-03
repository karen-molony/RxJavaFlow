/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package rx.internal.operators;

import rx.Flowable;
import rx.Flowable.OnSubscribe;
import rx.Subscriber;
import rx.functions.Supplier;

/**
 * Delays the subscription until the Flowable<U> emits an event.
 * 
 * @param <T>
 *            the value type
 */
public final class OnSubscribeDelaySubscriptionWithSelector<T, U> implements OnSubscribe<T> {
    final Flowable<? extends T> source;
    final Supplier<? extends Flowable<U>> subscriptionDelay;

    public OnSubscribeDelaySubscriptionWithSelector(Flowable<? extends T> source, Supplier<? extends Flowable<U>> subscriptionDelay) {
        this.source = source;
        this.subscriptionDelay = subscriptionDelay;
    }

    @Override
    public void call(final Subscriber<? super T> child) {
        try {
            subscriptionDelay.call().take(1).unsafeSubscribe(new Subscriber<U>() {

                @Override
                public void onComplete() {
                    // subscribe to actual source
                    source.unsafeSubscribe(child);
                }

                @Override
                public void onError(Throwable e) {
                    child.onError(e);
                }

                @Override
                public void onNext(U t) {
                    // ignore as we'll complete immediately because of take(1)
                }

            });
        } catch (Throwable e) {
            child.onError(e);
        }
    }

}
