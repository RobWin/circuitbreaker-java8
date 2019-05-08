/*
 * Copyright 2019 Kyuhyen Hwang
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
package io.github.resilience4j.recovery;

import io.vavr.CheckedFunction0;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * recovery decorator for {@link CompletionStage}
 */
public class CompletionStageRecoveryDecorator implements RecoveryDecorator {

    @Override
    public boolean supports(Class<?> target) {
        return CompletionStage.class.isAssignableFrom(target);
    }

    @SuppressWarnings("unchecked")
    @Override
    public CheckedFunction0<Object> decorate(RecoveryMethod recoveryMethod, CheckedFunction0<Object> supplier) {
        return supplier.andThen(request -> {
            CompletionStage completionStage = (CompletionStage) request;

            CompletableFuture promise = new CompletableFuture();

            completionStage.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    try {
                        ((CompletionStage) recoveryMethod.recover((Throwable) throwable))
                                .whenComplete((recoveryResult, recoveryThrowable) -> {
                                    if (recoveryThrowable != null) {
                                        promise.completeExceptionally((Throwable) recoveryThrowable);
                                    } else {
                                        promise.complete(recoveryResult);
                                    }
                                });
                    } catch (Throwable recoveryThrowable) {
                        promise.completeExceptionally(recoveryThrowable);
                    }
                } else {
                    promise.complete(result);
                }
            });

            return promise;
        });
    }
}
