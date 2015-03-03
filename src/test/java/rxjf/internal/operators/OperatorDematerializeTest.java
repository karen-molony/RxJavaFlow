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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Test;

import rx.Notification;
import rx.Flowable;
import rx.Observer;
import rx.exceptions.TestException;
import rx.observers.Subscribers;
import rx.observers.TestSubscriber;

public class OperatorDematerializeTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testDematerialize1() {
        Flowable<Notification<Integer>> notifications = Flowable.just(1, 2).materialize();
        Flowable<Integer> dematerialize = notifications.dematerialize();

        Observer<Integer> observer = mock(Observer.class);
        dematerialize.subscribe(observer);

        verify(observer, times(1)).onNext(1);
        verify(observer, times(1)).onNext(2);
        verify(observer, times(1)).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDematerialize2() {
        Throwable exception = new Throwable("test");
        Flowable<Integer> observable = Flowable.error(exception);
        Flowable<Integer> dematerialize = observable.materialize().dematerialize();

        Observer<Integer> observer = mock(Observer.class);
        dematerialize.subscribe(observer);

        verify(observer, times(1)).onError(exception);
        verify(observer, times(0)).onComplete();
        verify(observer, times(0)).onNext(any(Integer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDematerialize3() {
        Exception exception = new Exception("test");
        Flowable<Integer> observable = Flowable.error(exception);
        Flowable<Integer> dematerialize = observable.materialize().dematerialize();

        Observer<Integer> observer = mock(Observer.class);
        dematerialize.subscribe(observer);

        verify(observer, times(1)).onError(exception);
        verify(observer, times(0)).onComplete();
        verify(observer, times(0)).onNext(any(Integer.class));
    }

    @Test
    public void testErrorPassThru() {
        Exception exception = new Exception("test");
        Flowable<Integer> observable = Flowable.error(exception);
        Flowable<Integer> dematerialize = observable.dematerialize();

        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);
        dematerialize.subscribe(observer);

        verify(observer, times(1)).onError(exception);
        verify(observer, times(0)).onComplete();
        verify(observer, times(0)).onNext(any(Integer.class));
    }

    @Test
    public void testCompletePassThru() {
        Flowable<Integer> observable = Flowable.empty();
        Flowable<Integer> dematerialize = observable.dematerialize();

        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>(observer);
        dematerialize.subscribe(ts);

        System.out.println(ts.getOnErrorEvents());

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
        verify(observer, times(0)).onNext(any(Integer.class));
    }

    @Test
    public void testHonorsContractWhenCompleted() {
        Flowable<Integer> source = Flowable.just(1);
        
        Flowable<Integer> result = source.materialize().dematerialize();
        
        @SuppressWarnings("unchecked")
        Observer<Integer> o = mock(Observer.class);
        
        result.unsafeSubscribe(Subscribers.from(o));
        
        verify(o).onNext(1);
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }
    
    @Test
    public void testHonorsContractWhenThrows() {
        Flowable<Integer> source = Flowable.error(new TestException());
        
        Flowable<Integer> result = source.materialize().dematerialize();
        
        @SuppressWarnings("unchecked")
        Observer<Integer> o = mock(Observer.class);
        
        result.unsafeSubscribe(Subscribers.from(o));
        
        verify(o, never()).onNext(any(Integer.class));
        verify(o, never()).onComplete();
        verify(o).onError(any(TestException.class));
    }
}
