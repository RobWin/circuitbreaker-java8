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

/**
 *  default recovery decorator. it catches throwable and invoke the recovery method.
 */
public class DefaultRecoveryDecorator implements RecoveryDecorator {

    @Override
    public boolean supports(Class<?> target) {
        return true;
    }

    @Override
    public CheckedFunction0<Object> decorate(RecoveryMethod recoveryMethod, CheckedFunction0<Object> supplier) {
        return () -> {
            try {
                return supplier.apply();
            } catch (Throwable throwable) {
                return recoveryMethod.recover(throwable);
            }
        };
    }
}
