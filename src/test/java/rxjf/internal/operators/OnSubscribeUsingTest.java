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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.mockito.InOrder;

import rx.Flowable;
import rx.Flowable.OnSubscribe;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.exceptions.TestException;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Supplier;
import rx.functions.Function;
import rx.subscriptions.Subscriptions;

public class OnSubscribeUsingTest {

    private static interface Resource {
        public String getTextFromWeb();

        public void dispose();
    }

    private static class DisposeAction implements Action1<Resource> {

        @Override
        public void call(Resource r) {
            r.dispose();
        }

    }

    private final Action1<Subscription> disposeSubscription = new Action1<Subscription>() {

        @Override
        public void call(Subscription s) {
            s.unsubscribe();
        }

    };

    @Test
    public void testUsing() {
        performTestUsing(false);
    }

    @Test
    public void testUsingEagerly() {
        performTestUsing(true);
    }

    private void performTestUsing(boolean disposeEagerly) {
        final Resource resource = mock(Resource.class);
        when(resource.getTextFromWeb()).thenReturn("Hello world!");

        Supplier<Resource> resourceFactory = new Supplier<Resource>() {
            @Override
            public Resource call() {
                return resource;
            }
        };

        Function<Resource, Flowable<String>> observableFactory = new Function<Resource, Flowable<String>>() {
            @Override
            public Flowable<String> call(Resource resource) {
                return Flowable.from(resource.getTextFromWeb().split(" "));
            }
        };

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Flowable<String> observable = Flowable.using(resourceFactory, observableFactory,
                new DisposeAction(), disposeEagerly);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("Hello");
        inOrder.verify(observer, times(1)).onNext("world!");
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();

        // The resouce should be closed
        verify(resource, times(1)).dispose();
    }

    @Test
    public void testUsingWithSubscribingTwice() {
        performTestUsingWithSubscribingTwice(false);
    }

    @Test
    public void testUsingWithSubscribingTwiceDisposeEagerly() {
        performTestUsingWithSubscribingTwice(true);
    }

    private void performTestUsingWithSubscribingTwice(boolean disposeEagerly) {
        // When subscribe is called, a new resource should be created.
        Supplier<Resource> resourceFactory = new Supplier<Resource>() {
            @Override
            public Resource call() {
                return new Resource() {

                    boolean first = true;

                    @Override
                    public String getTextFromWeb() {
                        if (first) {
                            first = false;
                            return "Hello world!";
                        }
                        return "Nothing";
                    }

                    @Override
                    public void dispose() {
                        // do nothing
                    }

                };
            }
        };

        Function<Resource, Flowable<String>> observableFactory = new Function<Resource, Flowable<String>>() {
            @Override
            public Flowable<String> call(Resource resource) {
                return Flowable.from(resource.getTextFromWeb().split(" "));
            }
        };

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Flowable<String> observable = Flowable.using(resourceFactory, observableFactory,
                new DisposeAction(), disposeEagerly);
        observable.subscribe(observer);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);

        inOrder.verify(observer, times(1)).onNext("Hello");
        inOrder.verify(observer, times(1)).onNext("world!");
        inOrder.verify(observer, times(1)).onComplete();

        inOrder.verify(observer, times(1)).onNext("Hello");
        inOrder.verify(observer, times(1)).onNext("world!");
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test(expected = TestException.class)
    public void testUsingWithResourceFactoryError() {
        performTestUsingWithResourceFactoryError(false);
    }

    @Test(expected = TestException.class)
    public void testUsingWithResourceFactoryErrorDisposeEagerly() {
        performTestUsingWithResourceFactoryError(true);
    }

    private void performTestUsingWithResourceFactoryError(boolean disposeEagerly) {
        Supplier<Subscription> resourceFactory = new Supplier<Subscription>() {
            @Override
            public Subscription call() {
                throw new TestException();
            }
        };

        Function<Subscription, Flowable<Integer>> observableFactory = new Function<Subscription, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> call(Subscription subscription) {
                return Flowable.empty();
            }
        };

        Flowable.using(resourceFactory, observableFactory, disposeSubscription).toBlocking()
                .last();
    }

    @Test
    public void testUsingWithFlowableFactoryError() {
        performTestUsingWithFlowableFactoryError(false);
    }

    @Test
    public void testUsingWithFlowableFactoryErrorDisposeEagerly() {
        performTestUsingWithFlowableFactoryError(true);
    }

    private void performTestUsingWithFlowableFactoryError(boolean disposeEagerly) {
        final Action0 unsubscribe = mock(Action0.class);
        Supplier<Subscription> resourceFactory = new Supplier<Subscription>() {
            @Override
            public Subscription call() {
                return Subscriptions.create(unsubscribe);
            }
        };

        Function<Subscription, Flowable<Integer>> observableFactory = new Function<Subscription, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> call(Subscription subscription) {
                throw new TestException();
            }
        };

        try {
            Flowable.using(resourceFactory, observableFactory, disposeSubscription).toBlocking()
                    .last();
            fail("Should throw a TestException when the observableFactory throws it");
        } catch (TestException e) {
            // Make sure that unsubscribe is called so that users can close
            // the resource if some error happens.
            verify(unsubscribe, times(1)).call();
        }
    }

    @Test
    public void testUsingWithFlowableFactoryErrorInOnSubscribe() {
        performTestUsingWithFlowableFactoryErrorInOnSubscribe(false);
    }

    @Test
    public void testUsingWithFlowableFactoryErrorInOnSubscribeDisposeEagerly() {
        performTestUsingWithFlowableFactoryErrorInOnSubscribe(true);
    }

    private void performTestUsingWithFlowableFactoryErrorInOnSubscribe(boolean disposeEagerly) {
        final Action0 unsubscribe = mock(Action0.class);
        Supplier<Subscription> resourceFactory = new Supplier<Subscription>() {
            @Override
            public Subscription call() {
                return Subscriptions.create(unsubscribe);
            }
        };

        Function<Subscription, Flowable<Integer>> observableFactory = new Function<Subscription, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> call(Subscription subscription) {
                return Flowable.create(new OnSubscribe<Integer>() {
                    @Override
                    public void call(Subscriber<? super Integer> t1) {
                        throw new TestException();
                    }
                });
            }
        };

        try {
            Flowable
                    .using(resourceFactory, observableFactory, disposeSubscription, disposeEagerly)
                    .toBlocking().last();
            fail("Should throw a TestException when the observableFactory throws it");
        } catch (TestException e) {
            // Make sure that unsubscribe is called so that users can close
            // the resource if some error happens.
            verify(unsubscribe, times(1)).call();
        }
    }

    @Test
    public void testUsingDisposesEagerlyBeforeCompletion() {
        final List<String> events = new ArrayList<String>();
        Supplier<Resource> resourceFactory = createResourceFactory(events);
        final Action0 completion = createonComplete()Action(events);
        final Action0 unsub =createUnsubAction(events);

        Function<Resource, Flowable<String>> observableFactory = new Function<Resource, Flowable<String>>() {
            @Override
            public Flowable<String> call(Resource resource) {
                return Flowable.from(resource.getTextFromWeb().split(" "));
            }
        };

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Flowable<String> observable = Flowable.using(resourceFactory, observableFactory,
                new DisposeAction(), true).doOnUnsubscribe(unsub)
                .doOnComplete(completion);
        observable.subscribe(observer);

        assertEquals(Arrays.asList("disposed", "completed", "unsub"), events);

    }

    @Test
    public void testUsingDoesNotDisposesEagerlyBeforeCompletion() {
        final List<String> events = new ArrayList<String>();
        Supplier<Resource> resourceFactory = createResourceFactory(events);
        final Action0 completion = createonComplete()Action(events);
        final Action0 unsub =createUnsubAction(events);

        Function<Resource, Flowable<String>> observableFactory = new Function<Resource, Flowable<String>>() {
            @Override
            public Flowable<String> call(Resource resource) {
                return Flowable.from(resource.getTextFromWeb().split(" "));
            }
        };

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Flowable<String> observable = Flowable.using(resourceFactory, observableFactory,
                new DisposeAction(), false).doOnUnsubscribe(unsub)
                .doOnComplete(completion);
        observable.subscribe(observer);

        assertEquals(Arrays.asList("completed", "unsub", "disposed"), events);

    }

    
    
    @Test
    public void testUsingDisposesEagerlyBeforeError() {
        final List<String> events = new ArrayList<String>();
        Supplier<Resource> resourceFactory = createResourceFactory(events);
        final Action1<Throwable> onError = createOnErrorAction(events);
        final Action0 unsub = createUnsubAction(events);
        
        Function<Resource, Flowable<String>> observableFactory = new Function<Resource, Flowable<String>>() {
            @Override
            public Flowable<String> call(Resource resource) {
                return Flowable.from(resource.getTextFromWeb().split(" ")).concatWith(Flowable.<String>error(new RuntimeException()));
            }
        };

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Flowable<String> observable = Flowable.using(resourceFactory, observableFactory,
                new DisposeAction(), true).doOnUnsubscribe(unsub)
                .doOnError(onError);
        observable.subscribe(observer);

        assertEquals(Arrays.asList("disposed", "error", "unsub"), events);

    }
    
    @Test
    public void testUsingDoesNotDisposesEagerlyBeforeError() {
        final List<String> events = new ArrayList<String>();
        Supplier<Resource> resourceFactory = createResourceFactory(events);
        final Action1<Throwable> onError = createOnErrorAction(events);
        final Action0 unsub = createUnsubAction(events);
        
        Function<Resource, Flowable<String>> observableFactory = new Function<Resource, Flowable<String>>() {
            @Override
            public Flowable<String> call(Resource resource) {
                return Flowable.from(resource.getTextFromWeb().split(" ")).concatWith(Flowable.<String>error(new RuntimeException()));
            }
        };

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Flowable<String> observable = Flowable.using(resourceFactory, observableFactory,
                new DisposeAction(), false).doOnUnsubscribe(unsub)
                .doOnError(onError);
        observable.subscribe(observer);

        assertEquals(Arrays.asList("error", "unsub", "disposed"), events);
    }

    private static Action0 createUnsubAction(final List<String> events) {
        return new Action0() {
            @Override
            public void call() {
                events.add("unsub");
            }
        };
    }

    private static Action1<Throwable> createOnErrorAction(final List<String> events) {
        return new Action1<Throwable>() {
            @Override
            public void call(Throwable t) {
                events.add("error");
            }
        };
    }

    private static Supplier<Resource> createResourceFactory(final List<String> events) {
        return new Supplier<Resource>() {
            @Override
            public Resource call() {
                return new Resource() {

                    @Override
                    public String getTextFromWeb() {
                        return "hello world";
                    }

                    @Override
                    public void dispose() {
                        events.add("disposed");
                    }
                };
            }
        };
    }
    
    private static Action0 createonComplete()Action(final List<String> events) {
        return new Action0() {
            @Override
            public void call() {
                events.add("completed");
            }
        };
    }
    
}
