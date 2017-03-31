/*
 * Copyright 2017 Dan Maas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.resilience4j.ratpack.annotation

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.ratpack.RecoveryFunction
import io.github.resilience4j.ratpack.ResilienceModule
import ratpack.exec.Promise
import ratpack.test.embed.EmbeddedApp
import ratpack.test.http.TestHttpClient
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

import static ratpack.groovy.test.embed.GroovyEmbeddedApp.ratpack

@Unroll
class RateLimiterSpec extends Specification {

    @AutoCleanup
    EmbeddedApp app

    @Delegate
    TestHttpClient client

    def "test rate limit a method via annotation"() {
        given:
        RateLimiterRegistry registry = RateLimiterRegistry.of(buildConfig())
        app = ratpack {
            bindings {
                bindInstance(CircuitBreakerRegistry, CircuitBreakerRegistry.ofDefaults())
                bindInstance(RateLimiterRegistry, registry)
                bind(Something)
                module(ResilienceModule)
            }
            handlers {
                get('promise') { Something something ->
                    something.rateLimiterPromise().then {
                        render it
                    }
                }
                get('stage') { Something something ->
                    render something.rateLimiterStage().toCompletableFuture().get()
                }
                get('normal') { Something something ->
                    render something.rateLimiterNormal()
                }
            }
        }
        client = testHttpClient(app)

        when:
        def actual = null
        for (int i = 0; i <= 10; i++) {
            actual = get(path)
            println actual.body.text
        }

        then:
        actual.body.text.contains('io.github.resilience4j.ratelimiter.RequestNotPermitted: Request not permitted for limiter: test')
        actual.statusCode == 500

        where:
        path      | rateLimiterName
        'promise' | 'test'
        'stage'   | 'test'
        'normal'  | 'test'
    }

    def "test rate limit a method via annotation with exception"() {
        given:
        RateLimiterRegistry registry = RateLimiterRegistry.of(buildConfig())
        app = ratpack {
            bindings {
                bindInstance(CircuitBreakerRegistry, CircuitBreakerRegistry.ofDefaults())
                bindInstance(RateLimiterRegistry, registry)
                bind(Something)
                module(ResilienceModule)
            }
            handlers {
                get('promise') { Something something ->
                    something.rateLimiterPromiseException().then {
                        render it
                    }
                }
                get('stage') { Something something ->
                    render something.rateLimiterStageException().toCompletableFuture().get()
                }
                get('normal') { Something something ->
                    render something.rateLimiterNormalException()
                }
            }
        }
        client = testHttpClient(app)

        when:
        def actual = null
        for (int i = 0; i <= 10; i++) {
            actual = get(path)
            println actual.body.text
        }

        then:
        actual.body.text.contains('io.github.resilience4j.ratelimiter.RequestNotPermitted: Request not permitted for limiter: test')
        actual.statusCode == 500

        where:
        path      | rateLimiterName
        'promise' | 'test'
        'stage'   | 'test'
        'normal'  | 'test'
    }

    def "test rate limit a method via annotation with fallback"() {
        given:
        RateLimiterRegistry registry = RateLimiterRegistry.of(buildConfig())
        app = ratpack {
            bindings {
                bindInstance(CircuitBreakerRegistry, CircuitBreakerRegistry.ofDefaults())
                bindInstance(RateLimiterRegistry, registry)
                bind(Something)
                module(ResilienceModule)
            }
            handlers {
                get('promise') { Something something ->
                    something.rateLimiterPromiseFallback().then {
                        render it
                    }
                }
                get('stage') { Something something ->
                    render something.rateLimiterStageFallback().toCompletableFuture().get()
                }
                get('normal') { Something something ->
                    render something.rateLimiterNormalFallback()
                }
            }
        }
        client = testHttpClient(app)

        when:
        def actual = null
        for (int i = 0; i <= 10; i++) {
            actual = get(path)
            println actual.body.text
        }

        then:
        actual.body.text.contains('recovered')
        actual.statusCode == 200

        where:
        path      | rateLimiterName
        'promise' | 'test'
        'stage'   | 'test'
        'normal'  | 'test'
    }

    // 10 events / 10 s
    def buildConfig() {
        RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(10))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofMillis(100))
                .build()
    }

    static class Something {

        @RateLimiter(name = "test")
        Promise<String> rateLimiterPromise() {
            Promise.async {
                it.success("rateLimiter promise")
            }
        }

        @RateLimiter(name = "test")
        CompletionStage<String> rateLimiterStage() {
            CompletableFuture.supplyAsync { 'rateLimiter stage' }
        }

        @RateLimiter(name = "test")
        String rateLimiterNormal() {
            "rateLimiter normal"
        }

        @RateLimiter(name = "test")
        Promise<String> rateLimiterPromiseException() {
            Promise.async {
                it.error(new Exception("rateLimiter promise exception"))
            }
        }

        @RateLimiter(name = "test")
        CompletionStage<Void> rateLimiterStageException() {
            CompletableFuture.supplyAsync { throw new Exception('rateLimiter stage exception') }
        }

        @RateLimiter(name = "test")
        String rateLimiterNormalException() {
            throw new Exception("rateLimiter normal exception")
        }

        @RateLimiter(name = "test", recovery = MyRecoveryFunction)
        Promise<String> rateLimiterPromiseFallback() {
            Promise.async {
                it.error(new Exception("rateLimiter promise exception"))
            }
        }

        @RateLimiter(name = "test", recovery = MyRecoveryFunction)
        CompletionStage<Void> rateLimiterStageFallback() {
            CompletableFuture.supplyAsync { throw new Exception('rateLimiter stage exception') }
        }

        @RateLimiter(name = "test", recovery = MyRecoveryFunction)
        String rateLimiterNormalFallback() {
            throw new Exception("rateLimiter normal exception")
        }
    }

    static class MyRecoveryFunction implements RecoveryFunction<String> {
        @Override
        String apply(Throwable t) throws Exception {
            "recovered"
        }
    }

}