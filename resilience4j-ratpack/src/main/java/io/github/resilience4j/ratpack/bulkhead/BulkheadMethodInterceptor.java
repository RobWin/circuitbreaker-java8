/*
 * Copyright 2017 Jan Sykora
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
package io.github.resilience4j.ratpack.bulkhead;

import com.google.inject.Inject;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.core.lang.Nullable;
import io.github.resilience4j.ratpack.internal.AbstractMethodInterceptor;
import io.github.resilience4j.ratpack.recovery.RecoveryFunction;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import ratpack.exec.Promise;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A {@link MethodInterceptor} to handle all methods annotated with {@link Bulkhead}. It will
 * handle methods that return a Promise only. It will add a transform to the promise with the bulkhead and
 * fallback found in the annotation.
 */
public class BulkheadMethodInterceptor extends AbstractMethodInterceptor {

    @Inject(optional = true)
    @Nullable
    private BulkheadRegistry registry;

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Bulkhead annotation = invocation.getMethod().getAnnotation(Bulkhead.class);
        final RecoveryFunction<?> fallbackMethod = Optional
                .ofNullable(createRecoveryFunction(invocation, annotation.fallbackMethod()))
                .orElse(annotation.recovery().getDeclaredConstructor().newInstance());
        if (registry == null) {
            registry = BulkheadRegistry.ofDefaults();
        }
        io.github.resilience4j.bulkhead.Bulkhead bulkhead = registry.bulkhead(annotation.name());
        Class<?> returnType = invocation.getMethod().getReturnType();
        if (Promise.class.isAssignableFrom(returnType)) {
            Promise<?> result = (Promise<?>) invocation.proceed();
            if (result != null) {
                BulkheadTransformer transformer = BulkheadTransformer.of(bulkhead).recover(fallbackMethod);
                result = result.transform(transformer);
            }
            return result;
        } else if (Flux.class.isAssignableFrom(returnType)) {
            Flux<?> result = (Flux<?>) invocation.proceed();
            if (result != null) {
                BulkheadOperator operator = BulkheadOperator.of(bulkhead);
                result = fallbackMethod.onErrorResume(result.transform(operator));
            }
            return result;
        } else if (Mono.class.isAssignableFrom(returnType)) {
            Mono<?> result = (Mono<?>) invocation.proceed();
            if (result != null) {
                BulkheadOperator operator = BulkheadOperator.of(bulkhead);
                result = fallbackMethod.onErrorResume(result.transform(operator));
            }
            return result;
        } else if (CompletionStage.class.isAssignableFrom(returnType)) {
            final CompletableFuture promise = new CompletableFuture<>();
            if (bulkhead.tryAcquirePermission()) {
                CompletionStage<?> result = (CompletionStage<?>) invocation.proceed();
                if (result != null) {
                    result.whenComplete((value, throwable) -> {
                        bulkhead.onComplete();
                        if (throwable != null) {
                            try {
                                Object maybeFuture = fallbackMethod.apply(throwable);
                                if (maybeFuture instanceof CompletionStage) {
                                    ((CompletionStage) maybeFuture).whenComplete((v1, t1) -> promise.complete(v1));
                                } else {
                                    promise.complete(maybeFuture);
                                }
                            } catch (Exception e) {
                                promise.completeExceptionally(e);
                            }
                        } else {
                            promise.complete(value);
                        }
                    });
                }
            } else {
                Throwable t = new BulkheadFullException(String.format("Bulkhead '%s' is full", bulkhead.getName()));
                try {
                    Object maybeFuture = fallbackMethod.apply(t);
                    if (maybeFuture instanceof CompletionStage) {
                        ((CompletionStage) maybeFuture).whenComplete((v1, t1) -> promise.complete(v1));
                    } else {
                        promise.complete(maybeFuture);
                    }
                } catch (Exception exception) {
                    promise.completeExceptionally(exception);
                }
            }
            return promise;
        } else {
            boolean permission = bulkhead.tryAcquirePermission();
            if (!permission) {
                Throwable t = new BulkheadFullException(String.format("Bulkhead '%s' is full", bulkhead.getName()));
                return fallbackMethod.apply(t);
            }
            try {
                if (Thread.interrupted()) {
                    throw new IllegalStateException("Thread was interrupted during permission wait");
                }
                return invocation.proceed();
            } catch (Exception e) {
                return fallbackMethod.apply(e);
            } finally {
                bulkhead.onComplete();
            }
        }
    }

}
