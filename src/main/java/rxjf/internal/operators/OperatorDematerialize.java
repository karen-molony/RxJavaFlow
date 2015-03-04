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

import rx.Notification;
import rx.Flowable.Operator;
import rx.Subscriber;

/**
 * Reverses the effect of {@link OperatorMaterialize} by transforming the Notification objects
 * emitted by a source Flowable into the items or notifications they represent.
 * <p>
 * <img width="640" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/dematerialize.png" alt="">
 * <p>
 * See <a href="http://msdn.microsoft.com/en-us/library/hh229047.aspx">here</a> for the Microsoft Rx equivalent.
 * 
 * @param <T> the wrapped value type
 */
public final class OperatorDematerialize<T> implements Operator<T, Notification<T>> {
    /** Lazy initialization via inner-class holder. */
    private static final class Holder {
        /** A singleton instance. */
        static final OperatorDematerialize<Object> INSTANCE = new OperatorDematerialize<Object>();
    }
    /**
     * @return a singleton instance of this stateless operator.
     */
    @SuppressWarnings({ "rawtypes" })
    public static OperatorDematerialize instance() {
        return Holder.INSTANCE; // using raw types because the type inference is not good enough
    }
    private OperatorDematerialize() { }
    @Override
    public Subscriber<? super Notification<T>> call(final Subscriber<? super T> child) {
        return new Subscriber<Notification<T>>(child) {
            /** Do not send two onComplete() events. */
            boolean terminated;
            @Override
            public void onNext(Notification<T> t) {
                switch (t.getKind()) {
                case OnNext:
                    if (!terminated) {
                        child.onNext(t.getValue());
                    }
                    break;
                case OnError:
                    onError(t.getThrowable());
                    break;
                case onComplete():
                    onComplete();
                    break;
                }
            }

            @Override
            public void onError(Throwable e) {
                if (!terminated) {
                    terminated = true;
                    child.onError(e);
                }
            }

            @Override
            public void onComplete() {
                if (!terminated) {
                    terminated = true;
                    child.onComplete();
                }
            }
            
        };
    }
    
}
