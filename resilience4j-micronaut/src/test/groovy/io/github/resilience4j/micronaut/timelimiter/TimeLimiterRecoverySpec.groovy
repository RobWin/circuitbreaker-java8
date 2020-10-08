/*
 * Copyright 2020 Michael Pollind
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
package io.github.resilience4j.micronaut.timelimiter

import io.github.resilience4j.micronaut.TestDummyService
import io.github.resilience4j.micronaut.annotation.TimeLimiter
import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MicronautTest
import io.reactivex.Flowable
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.CompletableFuture

@MicronautTest
@Property(name = "resilience4j.timelimiter.enabled", value = "true")
class TimeLimiterRecoverySpec extends Specification {

    @Inject
    TimeLimiterService service;


    void "test sync recovery timeLimiter"() {
        when:
        String result = service.sync();

        then:
        result == "recovered"
    }

    void "test flowable recovery timeLimiter"() {
        when:
        Flowable<String> result = service.flowable();

        then:
        result.blockingFirst() == "recovered"
    }

    void "test completable recovery timeLimiter"() {
        when:
        CompletableFuture<String> result = service.completable();


        then:
        result.get() == "recovered"
    }

    @Singleton
    static class TimeLimiterService extends TestDummyService {
        @TimeLimiter(name = "backend-a", fallbackMethod = 'syncRecovery')
        @Override
        String sync() {
            return syncError()
        }

        @TimeLimiter(name = "backend-a", fallbackMethod = 'syncRecoveryParam')
        @Override
        String syncWithParam(String param) {
            return syncError()
        }

        @TimeLimiter(name = "backend-a", fallbackMethod = 'completableRecovery')
        @Override
        CompletableFuture<String> completable() {
            return completableFutureError()
        }

        @TimeLimiter(name = "backend-a", fallbackMethod = 'completableRecoveryParam')
        @Override
        CompletableFuture<String> completableWithParam(String param) {
            return completableFutureError()
        }

        @TimeLimiter(name = "backend-a", fallbackMethod = 'flowableRecovery')
        @Override
        Flowable<String> flowable() {
            return flowableError()
        }
    }
}
