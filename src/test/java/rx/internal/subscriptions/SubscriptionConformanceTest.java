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

package rx.internal.subscriptions;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.function.Function;

import rx.Flow.Subscriber;
import rx.Flow.Subscription;

/**
 * Tests conformance of a Subscription implementation.
 */
public final class SubscriptionConformanceTest {
    private SubscriptionConformanceTest() { }

    public static void conformancePositiveTry0(
            Function<? super Subscriber<?>, ? extends Subscription> supplier) {
        @SuppressWarnings("unchecked")
        Subscriber<Object> subscriber = mock(Subscriber.class);

        Subscription ds = supplier.apply(subscriber);

        ds.request(0);
        
        verify(subscriber, never()).onNext(any());
        verify(subscriber).onError(any(IllegalArgumentException.class));
        verify(subscriber, never()).onComplete();
    }
    public static void conformancePositiveTryMinus1(
            Function<? super Subscriber<?>, ? extends Subscription> supplier) {
        @SuppressWarnings("unchecked")
        Subscriber<Object> subscriber = mock(Subscriber.class);

        Subscription ds = supplier.apply(subscriber);

        ds.request(-1);
        
        verify(subscriber, never()).onNext(any());
        verify(subscriber).onError(any(IllegalArgumentException.class));
        verify(subscriber, never()).onComplete();
    }
    public static void conformanceRequestAfterCancelNoError(
            Function<? super Subscriber<?>, ? extends Subscription> supplier) {
        @SuppressWarnings("unchecked")
        Subscriber<Object> subscriber = mock(Subscriber.class);

        Subscription ds = supplier.apply(subscriber);

        ds.request(1);
        
        ds.cancel();
        
        ds.request(1);
        
        verify(subscriber, never()).onNext(any());
        verify(subscriber, never()).onError(any(Throwable.class));
        verify(subscriber, never()).onComplete();
    }
    
    public static <T> void conformanceSubscriberNonNull(Function<? super Subscriber<?>, ? extends Subscription> supplier) {
        supplier.apply(null);
    }
            
}
