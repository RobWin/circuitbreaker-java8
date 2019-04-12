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

import java.util.List;

/**
 * {@link RecoveryDecorator} resolver
 */
public class RecoveryDecorators {
    private final List<RecoveryDecorator> recoveryDecorator;
    private final RecoveryDecorator defaultRecoveryDecorator = new DefaultRecoveryDecorator();

    public RecoveryDecorators(List<RecoveryDecorator> recoveryDecorator) {
        this.recoveryDecorator = recoveryDecorator;
    }

    /**
     * find a {@link RecoveryDecorator} by return type of the {@link RecoveryMethod} and decorate supplier
     *
     * @param recoveryMethod recovery method that handles supplier's exception
     * @param supplier       original function
     * @return a function which is decorated by a {@link RecoveryMethod}
     */
    public CheckedFunction0<Object> decorate(RecoveryMethod recoveryMethod, CheckedFunction0<Object> supplier) {
        return get(recoveryMethod.getReturnType())
                .decorate(recoveryMethod, supplier);
    }

    private RecoveryDecorator get(Class<?> returnType) {
        return recoveryDecorator.stream().filter(it -> it.supports(returnType))
                .findFirst()
                .orElse(defaultRecoveryDecorator);
    }
}
