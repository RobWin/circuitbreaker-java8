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
package io.github.resilience4j.retry;

import io.github.resilience4j.retry.event.RetryEvent;
import io.github.resilience4j.retry.internal.RetryContext;
import io.reactivex.Flowable;
import io.vavr.CheckedFunction0;
import io.vavr.CheckedFunction1;
import io.vavr.CheckedRunnable;

import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

public interface Retry {

    /**
     * Returns the ID of this Retry.
     *
     * @return the ID of this Retry
     */
    String getName();

    /**
     *  Records a successful call.
     */
    void onSuccess();

    /**
     * Handles a checked exception
     *
     * @param exception the exception to handle
     * @throws Throwable the exception
     */
    void onError(Exception exception) throws Throwable;

    /**
     * Handles a runtime exception
     *
     * @param runtimeException the exception to handle
     */
    void onRuntimeError(RuntimeException runtimeException);

    /**
     * Returns a reactive stream of RetryEvents.
     *
     * @return a reactive stream of RetryEvents
     */
    Flowable<RetryEvent> getEventStream();

    /**
     * Creates a Retry with a custom Retry configuration.
     *
     * @param name the ID of the Retry
     * @param retryConfig a custom Retry configuration
     *
     * @return a Retry with a custom Retry configuration.
     */
    static RetryContext of(String name, RetryConfig retryConfig){
        return new RetryContext(name, retryConfig);
    }

    /**
     * Creates a Retry with a custom Retry configuration.
     *
     * @param name the ID of the Retry
     * @param retryConfigSupplier a supplier of a custom Retry configuration
     *
     * @return a Retry with a custom Retry configuration.
     */
    static RetryContext of(String name, Supplier<RetryConfig> retryConfigSupplier){
        return new RetryContext(name, retryConfigSupplier.get());
    }

    /**
     * Creates a Retry with default configuration.
     *
     * @param name the ID of the Retry
     * @return a Retry with default configuration
     */
    static Retry ofDefaults(String name){
        return new RetryContext(name, RetryConfig.ofDefaults());
    }

    /**
     * Decorates and executes the decorated Supplier.
     *
     * @param supplier the original Supplier
     * @param <T> the type of results supplied by this supplier
     * @return the result of the decorated Supplier.
     */
    default <T> T executeSupplier(Supplier<T> supplier){
        return decorateSupplier(this, supplier).get();
    }

    /**
     * Decorates and executes the decorated Callable.
     *
     * @param callable the original Callable
     *
     * @return the result of the decorated Callable.
     * @param <T> the result type of callable
     * @throws Exception if unable to compute a result
     */
    default <T> T executeCallable(Callable<T> callable) throws Exception{
        return decorateCallable(this, callable).call();
    }

    /**
     * Decorates and executes the decorated Runnable.
     *
     * @param runnable the original Runnable
     */
    default void executeRunnable(Runnable runnable){
        decorateRunnable(this, runnable).run();
    }

    /**
     * Creates a retryable supplier.
     *
     * @param retryContext the retry context
     * @param supplier the original function
     * @param <T> the type of results supplied by this supplier
     *
     * @return a retryable function
     */
    static <T> CheckedFunction0<T> decorateCheckedSupplier(Retry retryContext, CheckedFunction0<T> supplier){
        return () -> {
            do try {
                T result = supplier.apply();
                retryContext.onSuccess();
                return result;
            } catch (Exception exception) {
                retryContext.onError(exception);
            } while (true);
        };
    }

    /**
     * Creates a retryable runnable.
     *
     * @param retryContext the retry context
     * @param runnable the original runnable
     *
     * @return a retryable runnable
     */
    static CheckedRunnable decorateCheckedRunnable(Retry retryContext, CheckedRunnable runnable){
        return () -> {
            do try {
                runnable.run();
                retryContext.onSuccess();
                break;
            } catch (Exception exception) {
                retryContext.onError(exception);
            } while (true);
        };
    }

    /**
     * Creates a retryable function.
     *
     * @param retryContext the retry context
     * @param function the original function
     * @param <T> the type of the input to the function
     * @param <R> the result type of the function
     *
     * @return a retryable function
     */
    static <T, R> CheckedFunction1<T, R> decorateCheckedFunction(Retry retryContext, CheckedFunction1<T, R> function){
        return (T t) -> {
            do try {
                R result = function.apply(t);
                retryContext.onSuccess();
                return result;
            } catch (Exception exception) {
                retryContext.onError(exception);
            } while (true);
        };
    }

    /**
     * Creates a retryable supplier.
     *
     * @param retryContext the retry context
     * @param supplier the original function
     * @param <T> the type of results supplied by this supplier
     *
     * @return a retryable function
     */
    static <T> Supplier<T> decorateSupplier(Retry retryContext, Supplier<T> supplier){
        return () -> {
            do try {
                T result = supplier.get();
                retryContext.onSuccess();
                return result;
            } catch (RuntimeException runtimeException) {
                retryContext.onRuntimeError(runtimeException);
            } while (true);
        };
    }

    /**
     * Creates a retryable callable.
     *
     * @param retryContext the retry context
     * @param supplier the original function
     * @param <T> the type of results supplied by this supplier
     *
     * @return a retryable function
     */
    static <T> Callable<T> decorateCallable(Retry retryContext, Callable<T> supplier){
        return () -> {
            do try {
                T result = supplier.call();
                retryContext.onSuccess();
                return result;
            } catch (RuntimeException runtimeException) {
                retryContext.onRuntimeError(runtimeException);
            } while (true);
        };
    }

    /**
     * Creates a retryable runnable.
     *
     * @param retryContext the retry context
     * @param runnable the original runnable
     *
     * @return a retryable runnable
     */
    static Runnable decorateRunnable(Retry retryContext, Runnable runnable){
        return () -> {
            do try {
                runnable.run();
                retryContext.onSuccess();
                break;
            } catch (RuntimeException runtimeException) {
                retryContext.onRuntimeError(runtimeException);
            } while (true);
        };
    }

    /**
     * Creates a retryable function.
     *
     * @param retryContext the retry context
     * @param function the original function
     * @param <T> the type of the input to the function
     * @param <R> the result type of the function
     *
     * @return a retryable function
     */
    static <T, R> Function<T, R> decorateFunction(Retry retryContext, Function<T, R> function){
        return (T t) -> {
            do try {
                R result = function.apply(t);
                retryContext.onSuccess();
                return result;
            } catch (RuntimeException runtimeException) {
                retryContext.onRuntimeError(runtimeException);
            } while (true);
        };
    }

    /**
     * Get the Metrics of this RateLimiter.
     *
     * @return the Metrics of this RateLimiter
     */
    Metrics getMetrics();

    interface Metrics {
        /**
         * Returns how many attempts this have been made by this retry.
         *
         * @return how many retries have been attempted, but failed.
         */
        int getNumAttempts();

        /**
         * Returns how many retry attempts are allowed before failure.
         *
         * @return how many retries are allowed before failure.
         */
        int getMaxAttempts();
    }
}
