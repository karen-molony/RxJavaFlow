/**
 * Copyright 2015 David Karnok
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rxjf.internal.operators;

import rxjf.Flow.Subscriber;
import rxjf.Flowable.OnSubscribe;
import rxjf.internal.subscriptions.AbstractSubscription;

/**
 * 
 */
public final class OnSubscribeRangeLong implements OnSubscribe<Long> {
    final long start;
    final long count;
    public OnSubscribeRangeLong(long start, long count) {
        this.start = start;
        this.count = count;
    }
    @Override
    public void accept(Subscriber<? super Long> child) {
        long c = count;
        long s = start;
        child.onSubscribe(new AbstractSubscription<Long>(child) {
            long remaining = c;
            long value = s;
            @Override
            protected void onRequested(long n) {
                long val = value;
                long rem = remaining;
                if (n == Long.MAX_VALUE) {
                    while (rem > 0) {
                        if (isDisposed()) {
                            return;
                        }
                        child.onNext(val++);
                        rem--;
                    }
                    cancel();
                    child.onComplete();
                    return;
                }
                long req = n;
                for (;;) {
                    long c = 0;
                    while (req > 0 && rem > 0) {
                        if (isDisposed()) {
                            return;
                        }
                        child.onNext(val++);
                        req--;
                        rem--;
                        c++;
                    }
                    if (rem == 0) {
                        cancel();
                        child.onComplete();
                        break;
                    } else {
                        value = val;
                        remaining = rem;
                        req = produced(c);
                    }
                    if (req <= 0) {
                        break;
                    }
                }
            }
        });
    }
}
