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

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.mockito.Mockito;

import rx.Flowable;
import rx.Observer;
import rx.Subscriber;
import rx.internal.util.RxRingBuffer;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

public class OnSubscribeFromIterableTest {

    @Test(expected = NullPointerException.class)
    public void testNull() {
        Flowable.create(new OnSubscribeFromIterable<String>(null));
    }
    
    @Test
    public void testListIterable() {
        Flowable<String> observable = Flowable.create(new OnSubscribeFromIterable<String>(Arrays.<String> asList("one", "two", "three")));

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        observable.subscribe(observer);
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    /**
     * This tests the path that can not optimize based on size so must use setProducer.
     */
    @Test
    public void testRawIterable() {
        Iterable<String> it = new Iterable<String>() {

            @Override
            public Iterator<String> iterator() {
                return new Iterator<String>() {

                    int i = 0;

                    @Override
                    public boolean hasNext() {
                        return i++ < 3;
                    }

                    @Override
                    public String next() {
                        return String.valueOf(i);
                    }

                    @Override
                    public void remove() {
                    }

                };
            }

        };
        Flowable<String> observable = Flowable.create(new OnSubscribeFromIterable<String>(it));

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        observable.subscribe(observer);
        verify(observer, times(1)).onNext("1");
        verify(observer, times(1)).onNext("2");
        verify(observer, times(1)).onNext("3");
        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testFlowableFromIterable() {
        Flowable<String> observable = Flowable.from(Arrays.<String> asList("one", "two", "three"));

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        observable.subscribe(observer);
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testBackpressureViaRequest() {
        ArrayList<Integer> list = new ArrayList<Integer>(Flow.defaultBufferSize());
        for (int i = 1; i <= Flow.defaultBufferSize() + 1; i++) {
            list.add(i);
        }
        OnSubscribeFromIterable<Integer> o = new OnSubscribeFromIterable<Integer>(list);
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        ts.assertReceivedOnNext(Collections.<Integer> emptyList());
        ts.requestMore(1);
        o.call(ts);
        ts.assertValues((1));
        ts.requestMore(2);
        ts.assertValues((1, 2, 3));
        ts.requestMore(3);
        ts.assertValues((1, 2, 3, 4, 5, 6));
        ts.requestMore(list.size());
        ts.assertTerminalEvent();
    }

    @Test
    public void testNoBackpressure() {
        OnSubscribeFromIterable<Integer> o = new OnSubscribeFromIterable<Integer>(Arrays.asList(1, 2, 3, 4, 5));
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        ts.assertReceivedOnNext(Collections.<Integer> emptyList());
        ts.requestMore(Long.MAX_VALUE); // infinite
        o.call(ts);
        ts.assertValues((1, 2, 3, 4, 5));
        ts.assertTerminalEvent();
    }

    @Test
    public void testSubscribeMultipleTimes() {
        OnSubscribeFromIterable<Integer> o = new OnSubscribeFromIterable<Integer>(Arrays.asList(1, 2, 3));
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        o.call(ts);
        ts.assertValues((1, 2, 3));

        ts = new TestSubscriber<Integer>();
        o.call(ts);
        ts.assertValues((1, 2, 3));

        ts = new TestSubscriber<Integer>();
        o.call(ts);
        ts.assertValues((1, 2, 3));
    }
    
    @Test
    public void testFromIterableRequestOverflow() throws InterruptedException {
        Flowable<Integer> o = Flowable.from(Arrays.asList(1,2,3,4));
        final int expectedCount = 4;
        final CountDownLatch latch = new CountDownLatch(expectedCount);
        o.subscribeOn(Schedulers.computation()).subscribe(new Subscriber<Integer>() {
            
            @Override
            public void onStart() {
                request(2);
            }

            @Override
            public void onComplete() {
                //ignore
            }

            @Override
            public void onError(Throwable e) {
                throw new RuntimeException(e);
            }

            @Override
            public void onNext(Integer t) {
                latch.countDown();
                request(Long.MAX_VALUE-1);
            }});
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }


}
