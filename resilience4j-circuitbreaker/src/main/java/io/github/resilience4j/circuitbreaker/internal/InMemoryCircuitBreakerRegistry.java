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
package io.github.resilience4j.circuitbreaker.internal;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.AbstractRegistry;
import io.vavr.collection.Array;
import io.vavr.collection.Seq;

/**
 * Backend circuitBreaker manager.
 * Constructs backend circuitBreakers according to configuration values.
 */
public final class InMemoryCircuitBreakerRegistry extends AbstractRegistry<CircuitBreaker, CircuitBreakerConfig> implements CircuitBreakerRegistry {

	private static final String NAME_MUST_NOT_BE_NULL = "Name must not be null";
	private final CircuitBreakerConfig defaultCircuitBreakerConfig;
	/**
	 * The circuitBreakers, indexed by name of the backend.
	 */
	private final ConcurrentMap<String, CircuitBreaker> circuitBreakers;


	/**
	 * The constructor with default circuitBreaker properties.
	 */
	public InMemoryCircuitBreakerRegistry() {
		this(CircuitBreakerConfig.ofDefaults());
	}

	public InMemoryCircuitBreakerRegistry(Map<String, CircuitBreakerConfig> configs) {
		this(configs.getOrDefault(DEFAULT_CONFIG, CircuitBreakerConfig.ofDefaults()));
		this.configurations.putAll(configs);
	}

	/**
	 * The constructor with custom default circuitBreaker properties.
	 *
	 * @param defaultCircuitBreakerConfig The BackendMonitor service properties.
	 */
	public InMemoryCircuitBreakerRegistry(CircuitBreakerConfig defaultCircuitBreakerConfig) {
		super();
		this.defaultCircuitBreakerConfig = Objects.requireNonNull(defaultCircuitBreakerConfig, "CircuitBreakerConfig must not be null");
		this.circuitBreakers = new ConcurrentHashMap<>();
		this.configurations.put(DEFAULT_CONFIG, defaultCircuitBreakerConfig);
	}

	@Override
	public Seq<CircuitBreaker> getAllCircuitBreakers() {
		return Array.ofAll(circuitBreakers.values());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CircuitBreaker circuitBreaker(String name) {
		return circuitBreakers.computeIfAbsent(Objects.requireNonNull(name, NAME_MUST_NOT_BE_NULL),
				k -> notifyPostCreationConsumers(CircuitBreaker.of(name, defaultCircuitBreakerConfig)));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CircuitBreaker circuitBreaker(String name, CircuitBreakerConfig customCircuitBreakerConfig) {
		return circuitBreakers.computeIfAbsent(Objects.requireNonNull(name, NAME_MUST_NOT_BE_NULL),
				k -> notifyPostCreationConsumers(CircuitBreaker.of(name, customCircuitBreakerConfig)));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CircuitBreaker circuitBreaker(String name, Supplier<CircuitBreakerConfig> circuitBreakerConfigSupplier) {
		return circuitBreakers.computeIfAbsent(Objects.requireNonNull(name, NAME_MUST_NOT_BE_NULL),
				k -> {
					CircuitBreakerConfig config = circuitBreakerConfigSupplier.get();
					return notifyPostCreationConsumers(CircuitBreaker.of(name, config));
				});
	}



}
