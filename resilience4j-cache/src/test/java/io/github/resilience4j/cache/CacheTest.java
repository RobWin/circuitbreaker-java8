/*
 *
 *  Copyright 2016 Robert Winkler
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */
package io.github.resilience4j.cache;

import io.github.resilience4j.cache.event.CacheEvent;
import io.reactivex.subscribers.TestSubscriber;
import io.vavr.CheckedFunction1;
import org.junit.Before;
import org.junit.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

public class CacheTest {

    private javax.cache.Cache<String, String> cache;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp(){
        cache = mock(javax.cache.Cache.class);
    }

    @Test
    public void shouldReturnValueFromDecoratedCheckedSupplier() throws Throwable {
        // Given the cache does not contain the key
        given(cache.get("testKey")).willReturn(null);

        Cache<String, String> cacheContext = Cache.of(cache);
        TestSubscriber<CacheEvent.Type> testSubscriber = cacheContext.getEventStream()
                .map(CacheEvent::getEventType)
                .test();

        CheckedFunction1<String, String> cachedFunction = Cache.decorateCheckedSupplier(cacheContext, () -> "Hello world");
        String value = cachedFunction.apply("testKey");
        assertThat(value).isEqualTo("Hello world");

        then(cache).should().put("testKey", "Hello world");
        testSubscriber
                .assertValueCount(1)
                .assertValues(CacheEvent.Type.CACHE_MISS);
    }

    @Test
    public void shouldReturnValueFromDecoratedSupplier() throws Throwable {
        // Given the cache does not contain the key
        given(cache.get("testKey")).willReturn(null);

        Cache<String, String> cacheContext = Cache.of(cache);
        TestSubscriber<CacheEvent.Type> testSubscriber = cacheContext.getEventStream()
                .map(CacheEvent::getEventType)
                .test();

        Function<String, String> cachedFunction = Cache.decorateSupplier(cacheContext, () -> "Hello world");
        String value = cachedFunction.apply("testKey");
        assertThat(value).isEqualTo("Hello world");

        then(cache).should().put("testKey", "Hello world");
        testSubscriber
                .assertValueCount(1)
                .assertValues(CacheEvent.Type.CACHE_MISS);
    }

    @Test
    public void shouldReturnValueFromDecoratedCallable() throws Throwable {
        // Given the cache does not contain the key
        given(cache.get("testKey")).willReturn(null);

        Cache<String, String> cacheContext = Cache.of(cache);
        TestSubscriber<CacheEvent.Type> testSubscriber = cacheContext.getEventStream()
                .map(CacheEvent::getEventType)
                .test();

        CheckedFunction1<String, String> cachedFunction = Cache.decorateCallable(cacheContext, () -> "Hello world");
        String value = cachedFunction.apply("testKey");
        assertThat(value).isEqualTo("Hello world");

        then(cache).should().put("testKey", "Hello world");
        testSubscriber
                .assertValueCount(1)
                .assertValues(CacheEvent.Type.CACHE_MISS);
    }

    /*
    @Test(expected = IOException.class)
    public void shouldRethrowExceptionOfSupplier() throws Throwable {
        // Given the cache does not contain the key
        given(cache.get("testKey")).willReturn(null);

        Cache<String, String> cacheContext = Cache.of(cache);
        TestSubscriber<CacheEvent.Type> testSubscriber = cacheContext.getEventStream()
                .map(CacheEvent::getEventType)
                .test();

        CheckedFunction1<String, String> cachedFunction = Cache.decorateCheckedSupplier(cacheContext, () -> {throw new IOException();});
        cachedFunction.apply("testKey");

        testSubscriber
                .assertValueCount(0);
    }
    */

    @Test
    public void shouldReturnValueOfSupplier() throws Throwable {
        // Given the cache does not contain the key
        given(cache.get("testKey")).willReturn(null);
        willThrow(new RuntimeException("Cache is not available")).given(cache).put("testKey", "Hello world");

        Cache<String, String> cacheContext = Cache.of(cache);
        TestSubscriber<CacheEvent.Type> testSubscriber = cacheContext.getEventStream()
                .map(CacheEvent::getEventType)
                .test();

        CheckedFunction1<String, String> cachedFunction = Cache.decorateCheckedSupplier(cacheContext, () -> "Hello world");
        String value = cachedFunction.apply("testKey");
        assertThat(value).isEqualTo("Hello world");

        testSubscriber
                .assertValueCount(2)
                .assertValues(CacheEvent.Type.CACHE_MISS, CacheEvent.Type.ERROR);
    }

    @Test
    public void shouldReturnCachedValue() throws Throwable {
        // Return the value from cache
        given(cache.get("testKey")).willReturn("Hello from cache");

        Cache<String, String> cacheContext = Cache.of(cache);
        TestSubscriber<CacheEvent.Type> testSubscriber = cacheContext.getEventStream()
                .map(CacheEvent::getEventType)
                .test();

        CheckedFunction1<String, String> cachedFunction = Cache.decorateCheckedSupplier(cacheContext, () -> "Hello world");
        String value = cachedFunction.apply("testKey");
        assertThat(value).isEqualTo("Hello from cache");

        testSubscriber
                .assertValueCount(1)
                .assertValues(CacheEvent.Type.CACHE_HIT);
    }

    @Test
    public void shouldReturnValueFromDecoratedCallableBecauseOfException() throws Throwable {
        // Given the cache contains the key
        given(cache.get("testKey")).willThrow(new RuntimeException("Cache is not available"));

        Cache<String, String> cacheContext = Cache.of(cache);
        TestSubscriber<CacheEvent.Type> testSubscriber = cacheContext.getEventStream()
                .map(CacheEvent::getEventType)
                .test();

        CheckedFunction1<String, String> cachedFunction = Cache.decorateCheckedSupplier(cacheContext, () -> "Hello world");
        String value = cachedFunction.apply("testKey");
        assertThat(value).isEqualTo("Hello world");

        testSubscriber
                .assertValueCount(1)
                .assertValues(CacheEvent.Type.ERROR);
    }
}
