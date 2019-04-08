/*
 * Copyright 2018 David Rusek
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
package io.github.resilience4j.retry;


import java.util.function.Supplier;

import io.github.resilience4j.retry.internal.InMemoryAsyncRetryRegistry;
import io.vavr.collection.Seq;

/**
 * The {@link AsyncRetryRegistry} is a factory to create AsyncRetry instances which stores all AsyncRetry instances in a registry.
 * * @deprecated replaced by {@link RetryRegistry}
 */
@Deprecated
public interface AsyncRetryRegistry {

	/**
	 * Creates an AsyncRetryRegistry with a custom Retry configuration.
	 *
	 * @param retryConfig a custom Retry configuration
	 * @return an AsyncRetryRegistry with a custom Retry configuration.
	 */
	static AsyncRetryRegistry of(RetryConfig retryConfig) {
		return new InMemoryAsyncRetryRegistry(retryConfig);
	}

	/**
	 * Creates an AsyncRetryRegistry with a default Retry configuration.
	 *
	 * @return an AsyncRetryRegistry with a default Retry configuration.
	 */
	static AsyncRetryRegistry ofDefaults() {
		return new InMemoryAsyncRetryRegistry();
	}

	/**
	 * Returns all managed {@link AsyncRetry} instances.
	 *
	 * @return all managed {@link AsyncRetry} instances.
	 */
	Seq<AsyncRetry> getAllRetries();

	/**
	 * Returns a managed {@link AsyncRetry} or creates a new one with the default Retry configuration.
	 *
	 * @param name the name of the AsyncRetry
	 * @return The {@link AsyncRetry}
	 */
	AsyncRetry retry(String name);

	/**
	 * Returns a managed {@link AsyncRetry} or creates a new one with a custom Retry configuration.
	 *
	 * @param name        the name of the AsyncRetry
	 * @param retryConfig a custom Retry configuration
	 * @return The {@link AsyncRetry}
	 */
	AsyncRetry retry(String name, RetryConfig retryConfig);

	/**
	 * Returns a managed {@link AsyncRetry} or creates a new one with a custom Retry configuration.
	 *
	 * @param name                the name of the AsyncRetry
	 * @param retryConfigSupplier a supplier of a custom Retry configuration
	 * @return The {@link AsyncRetry}
	 */
	AsyncRetry retry(String name, Supplier<RetryConfig> retryConfigSupplier);
}
