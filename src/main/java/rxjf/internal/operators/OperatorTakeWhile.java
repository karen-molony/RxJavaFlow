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

import rx.Flowable.Operator;
import rx.Subscriber;
import rx.functions.Function;
import rx.functions.BiFunction;

/**
 * Returns an Flowable that emits items emitted by the source Flowable as long as a specified
 * condition is true.
 * <p>
 * <img width="640" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/takeWhile.png" alt="">
 */
public final class OperatorTakeWhile<T> implements Operator<T, T> {

    private final BiFunction<? super T, ? super Integer, Boolean> predicate;

    public OperatorTakeWhile(final Function<? super T, Boolean> underlying) {
        this(new BiFunction<T, Integer, Boolean>() {
            @Override
            public Boolean call(T input, Integer index) {
                return underlying.call(input);
            }
        });
    }

    public OperatorTakeWhile(BiFunction<? super T, ? super Integer, Boolean> predicate) {
        this.predicate = predicate;
    }

    @Override
    public Subscriber<? super T> call(final Subscriber<? super T> subscriber) {
        Subscriber<T> s = new Subscriber<T>(subscriber, false) {

            private int counter = 0;

            private boolean done = false;

            @Override
            public void onNext(T args) {
                boolean isSelected;
                try {
                    isSelected = predicate.call(args, counter++);
                } catch (Throwable e) {
                    done = true;
                    subscriber.onError(e);
                    unsubscribe();
                    return;
                }
                if (isSelected) {
                    subscriber.onNext(args);
                } else {
                    done = true;
                    subscriber.onComplete();
                    unsubscribe();
                }
            }

            @Override
            public void onComplete() {
                if (!done) {
                    subscriber.onComplete();
                }
            }

            @Override
            public void onError(Throwable e) {
                if (!done) {
                    subscriber.onError(e);
                }
            }

        };
        subscriber.add(s);
        return s;
    }

}
