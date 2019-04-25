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
package io.github.resilience4j.retry;


import java.util.function.Supplier;

import io.github.resilience4j.core.Registry;
import io.github.resilience4j.retry.internal.InMemoryRetryRegistry;
import io.vavr.collection.Seq;

/**
 * The {@link RetryRegistry} is a factory to create Retry instances which stores all Retry instances in a registry.
 */
public interface RetryRegistry extends Registry<Retry, RetryConfig> {

	/**
	 * Returns all managed {@link Retry} instances.
	 *
	 * @return all managed {@link Retry} instances.
	 */
	Seq<Retry> getAllRetries();

	/**
	 * Returns a managed {@link Retry} or creates a new one with the default Retry configuration.
	 *
	 * @param name the name of the Retry
	 * @return The {@link Retry}
	 */
	Retry retry(String name);

	/**
	 * Returns a managed {@link Retry} or creates a new one with a custom Retry configuration.
	 *
	 * @param name        the name of the Retry
	 * @param retryConfig a custom Retry configuration
	 * @return The {@link Retry}
	 */
	Retry retry(String name, RetryConfig retryConfig);

	/**
	 * Returns a managed {@link Retry} or creates a new one with a custom Retry configuration.
	 *
	 * @param name                the name of the Retry
	 * @param retryConfigSupplier a supplier of a custom Retry configuration
	 * @return The {@link Retry}
	 */
	Retry retry(String name, Supplier<RetryConfig> retryConfigSupplier);


	/**
	 * Creates a RetryRegistry with a custom Retry configuration.
	 *
	 * @param retryConfig a custom Retry configuration
	 * @return a RetryRegistry with a custom Retry configuration.
	 */
	static RetryRegistry of(RetryConfig retryConfig) {
		return new InMemoryRetryRegistry(retryConfig);
	}

	/**
	 * Creates a RetryRegistry with a default Retry configuration.
	 *
	 * @return a RetryRegistry with a default Retry configuration.
	 */
	static RetryRegistry ofDefaults() {
		return new InMemoryRetryRegistry();
	}
}
