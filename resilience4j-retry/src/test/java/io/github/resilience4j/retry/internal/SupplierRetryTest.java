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
package io.github.resilience4j.retry.internal;

import io.github.resilience4j.retry.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.test.HelloWorldService;
import io.vavr.API;
import io.vavr.CheckedFunction0;
import io.vavr.Predicates;
import io.vavr.control.Try;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;

import javax.xml.ws.WebServiceException;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static io.vavr.API.$;

public class SupplierRetryTest {

    private HelloWorldService helloWorldService;
    private long sleptTime = 0L;

    @Before
    public void setUp(){
        helloWorldService = Mockito.mock(HelloWorldService.class);
        RetryContext.sleepFunction = sleep -> sleptTime += sleep;
    }

    @Test
    public void shouldNotRetry() {
        // Given the HelloWorldService returns Hello world
        BDDMockito.given(helloWorldService.returnHelloWorld()).willReturn("Hello world");
        // Create a Retry with default configuration
        Retry retryContext = Retry.ofDefaults("id");
        // Decorate the invocation of the HelloWorldService
        Supplier<String> supplier = Retry.decorateSupplier(retryContext, helloWorldService::returnHelloWorld);

        // When
        String result = supplier.get();
        // Then the helloWorldService should be invoked 1 time
        BDDMockito.then(helloWorldService).should(Mockito.times(1)).returnHelloWorld();
        Assertions.assertThat(sleptTime).isEqualTo(0);
    }

    @Test
    public void testDecorateSupplier() {
        // Given the HelloWorldService throws an exception
        BDDMockito.given(helloWorldService.returnHelloWorld()).willThrow(new WebServiceException("BAM!")).willReturn("Hello world");

        // Create a Retry with default configuration
        Retry retryContext = Retry.ofDefaults("id");
        // Decorate the invocation of the HelloWorldService
        Supplier<String> supplier = Retry.decorateSupplier(retryContext, helloWorldService::returnHelloWorld);

        // When
        String result = supplier.get();

        // Then the helloWorldService should be invoked 2 times
        BDDMockito.then(helloWorldService).should(Mockito.times(2)).returnHelloWorld();
        Assertions.assertThat(result).isEqualTo("Hello world");
        Assertions.assertThat(sleptTime).isEqualTo(RetryConfig.DEFAULT_WAIT_DURATION);
    }

    @Test
    public void testDecorateCallable() throws Exception {
        // Given the HelloWorldService throws an exception
        BDDMockito.given(helloWorldService.returnHelloWorldWithException()).willThrow(new WebServiceException("BAM!")).willReturn("Hello world");

        // Create a Retry with default configuration
        Retry retryContext = Retry.ofDefaults("id");
        // Decorate the invocation of the HelloWorldService
        Callable<String> callable = Retry.decorateCallable(retryContext, helloWorldService::returnHelloWorldWithException);

        // When
        String result = callable.call();

        // Then the helloWorldService should be invoked 2 times
        BDDMockito.then(helloWorldService).should(Mockito.times(2)).returnHelloWorldWithException();
        Assertions.assertThat(result).isEqualTo("Hello world");
        Assertions.assertThat(sleptTime).isEqualTo(RetryConfig.DEFAULT_WAIT_DURATION);
    }

    @Test
    public void testExecuteCallable() throws Exception {
        // Given the HelloWorldService throws an exception
        BDDMockito.given(helloWorldService.returnHelloWorldWithException()).willThrow(new WebServiceException("BAM!")).willReturn("Hello world");

        // Create a Retry with default configuration
        Retry retryContext = Retry.ofDefaults("id");
        // Decorate the invocation of the HelloWorldService

        String result = retryContext.executeCallable(helloWorldService::returnHelloWorldWithException);

        // Then the helloWorldService should be invoked 2 times
        BDDMockito.then(helloWorldService).should(Mockito.times(2)).returnHelloWorldWithException();
        Assertions.assertThat(result).isEqualTo("Hello world");
        Assertions.assertThat(sleptTime).isEqualTo(RetryConfig.DEFAULT_WAIT_DURATION);
    }


    @Test
    public void testExecuteSupplier() {
        // Given the HelloWorldService throws an exception
        BDDMockito.given(helloWorldService.returnHelloWorld()).willThrow(new WebServiceException("BAM!")).willReturn("Hello world");

        // Create a Retry with default configuration
        Retry retryContext = Retry.ofDefaults("id");
        // Decorate the invocation of the HelloWorldService

        String result = retryContext.executeSupplier(helloWorldService::returnHelloWorld);

        // Then the helloWorldService should be invoked 2 times
        BDDMockito.then(helloWorldService).should(Mockito.times(2)).returnHelloWorld();
        Assertions.assertThat(result).isEqualTo("Hello world");
        Assertions.assertThat(sleptTime).isEqualTo(RetryConfig.DEFAULT_WAIT_DURATION);
    }

    @Test
    public void shouldReturnSuccessfullyAfterSecondAttempt() {
        // Given the HelloWorldService throws an exception
        BDDMockito.given(helloWorldService.returnHelloWorld()).willThrow(new WebServiceException("BAM!")).willReturn("Hello world");

        // Create a Retry with default configuration
        Retry retryContext = Retry.ofDefaults("id");
        // Decorate the invocation of the HelloWorldService
        CheckedFunction0<String> retryableSupplier = Retry.decorateCheckedSupplier(retryContext, helloWorldService::returnHelloWorld);

        // When
        Try<String> result = Try.of(retryableSupplier);

        // Then the helloWorldService should be invoked 2 times
        BDDMockito.then(helloWorldService).should(Mockito.times(2)).returnHelloWorld();
        Assertions.assertThat(result.get()).isEqualTo("Hello world");
        Assertions.assertThat(sleptTime).isEqualTo(RetryConfig.DEFAULT_WAIT_DURATION);
    }

    @Test
    public void shouldReturnAfterThreeAttempts() {
        // Given the HelloWorldService throws an exception
        BDDMockito.given(helloWorldService.returnHelloWorld()).willThrow(new WebServiceException("BAM!"));

        // Create a Retry with default configuration
        Retry retryContext = Retry.ofDefaults("id");
        // Decorate the invocation of the HelloWorldService
        CheckedFunction0<String> retryableSupplier = Retry.decorateCheckedSupplier(retryContext, helloWorldService::returnHelloWorld);

        // When
        Try<String> result = Try.of(retryableSupplier);

        // Then the helloWorldService should be invoked 3 times
        BDDMockito.then(helloWorldService).should(Mockito.times(3)).returnHelloWorld();
        // and the result should be a failure
        Assertions.assertThat(result.isFailure()).isTrue();
        // and the returned exception should be of type RuntimeException
        Assertions.assertThat(result.failed().get()).isInstanceOf(WebServiceException.class);
        Assertions.assertThat(sleptTime).isEqualTo(RetryConfig.DEFAULT_WAIT_DURATION*2);
    }

    @Test
    public void shouldReturnAfterOneAttempt() {
        // Given the HelloWorldService throws an exception
        BDDMockito.given(helloWorldService.returnHelloWorld()).willThrow(new WebServiceException("BAM!"));

        // Create a Retry with custom configuration
        RetryConfig config = RetryConfig.custom().maxAttempts(1).build();
        Retry retryContext = Retry.of("id", config);
        // Decorate the invocation of the HelloWorldService
        CheckedFunction0<String> retryableSupplier = Retry.decorateCheckedSupplier(retryContext, helloWorldService::returnHelloWorld);

        // When
        Try<String> result = Try.of(retryableSupplier);

        // Then the helloWorldService should be invoked 1 time
        BDDMockito.then(helloWorldService).should(Mockito.times(1)).returnHelloWorld();
        // and the result should be a failure
        Assertions.assertThat(result.isFailure()).isTrue();
        // and the returned exception should be of type RuntimeException
        Assertions.assertThat(result.failed().get()).isInstanceOf(WebServiceException.class);
        Assertions.assertThat(sleptTime).isEqualTo(0);
    }

    @Test
    public void shouldReturnAfterOneAttemptAndIgnoreException() {
        // Given the HelloWorldService throws an exception
        BDDMockito.given(helloWorldService.returnHelloWorld()).willThrow(new WebServiceException("BAM!"));

        // Create a Retry with default configuration
        RetryConfig config = RetryConfig.custom()
                .retryOnException(throwable -> API.Match(throwable).of(
                        API.Case($(Predicates.instanceOf(WebServiceException.class)), false),
                        API.Case($(), true)))
                .build();
        Retry retryContext = Retry.of("id", config);
        // Decorate the invocation of the HelloWorldService
        CheckedFunction0<String> retryableSupplier = Retry.decorateCheckedSupplier(retryContext, helloWorldService::returnHelloWorld);

        // When
        Try<String> result = Try.of(retryableSupplier);

        // Then the helloWorldService should be invoked only once, because the exception should be rethrown immediately.
        BDDMockito.then(helloWorldService).should(Mockito.times(1)).returnHelloWorld();
        // and the result should be a failure
        Assertions.assertThat(result.isFailure()).isTrue();
        // and the returned exception should be of type RuntimeException
        Assertions.assertThat(result.failed().get()).isInstanceOf(WebServiceException.class);
        Assertions.assertThat(sleptTime).isEqualTo(0);
    }

    @Test
    public void shouldReturnAfterThreeAttemptsAndRecover() {
        // Given the HelloWorldService throws an exception
        BDDMockito.given(helloWorldService.returnHelloWorld()).willThrow(new WebServiceException("BAM!"));

        // Create a Retry with default configuration
        Retry retryContext = Retry.ofDefaults("id");
        // Decorate the invocation of the HelloWorldService
        CheckedFunction0<String> retryableSupplier = Retry.decorateCheckedSupplier(retryContext, helloWorldService::returnHelloWorld);

        // When
        Try<String> result = Try.of(retryableSupplier).recover((throwable) -> "Hello world from recovery function");

        // Then the helloWorldService should be invoked 3 times
        BDDMockito.then(helloWorldService).should(Mockito.times(3)).returnHelloWorld();

        // and the returned exception should be of type RuntimeException
        Assertions.assertThat(result.get()).isEqualTo("Hello world from recovery function");
        Assertions.assertThat(sleptTime).isEqualTo(RetryConfig.DEFAULT_WAIT_DURATION*2);
    }

    @Test
    public void shouldTakeIntoAccountBackoffFunction() {
        // Given the HelloWorldService throws an exception
        BDDMockito.given(helloWorldService.returnHelloWorld()).willThrow(new WebServiceException("BAM!"));

        // Create a Retry with a backoff function doubling the interval
        RetryConfig config = RetryConfig.custom().intervalFunction(IntervalFunction.ofExponentialBackoff(500, 2.0)).build();
        Retry retryContext = Retry.of("id", config);
        // Decorate the invocation of the HelloWorldService
        CheckedFunction0<String> retryableSupplier = Retry.decorateCheckedSupplier(retryContext, helloWorldService::returnHelloWorld);

        // When
        Try<String> result = Try.of(retryableSupplier);

        // Then the slept time should be according to the backoff function
        BDDMockito.then(helloWorldService).should(Mockito.times(3)).returnHelloWorld();
        Assertions.assertThat(sleptTime).isEqualTo(
                RetryConfig.DEFAULT_WAIT_DURATION +
                    RetryConfig.DEFAULT_WAIT_DURATION*2);
    }
}
