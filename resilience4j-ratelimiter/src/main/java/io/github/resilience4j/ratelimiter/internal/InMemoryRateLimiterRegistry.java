/*
 *
 *  Copyright 2016 Robert Winkler and Bohdan Storozhuk
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
package io.github.resilience4j.ratelimiter.internal;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.github.resilience4j.core.AbstractRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.vavr.collection.Array;
import io.vavr.collection.Seq;

/**
 * Backend RateLimiter manager.
 * Constructs backend RateLimiters according to configuration values.
 */
public class InMemoryRateLimiterRegistry extends AbstractRegistry<RateLimiter, RateLimiterConfig> implements RateLimiterRegistry {

	private static final String NAME_MUST_NOT_BE_NULL = "Name must not be null";
	private static final String CONFIG_MUST_NOT_BE_NULL = "Config must not be null";
	private static final String SUPPLIER_MUST_NOT_BE_NULL = "Supplier must not be null";

	private final RateLimiterConfig defaultRateLimiterConfig;
	/**
	 * The RateLimiters, indexed by name of the backend.
	 */
	private final Map<String, RateLimiter> rateLimiters;

	public InMemoryRateLimiterRegistry(final RateLimiterConfig defaultRateLimiterConfig) {
		super();
		this.defaultRateLimiterConfig = requireNonNull(defaultRateLimiterConfig, CONFIG_MUST_NOT_BE_NULL);
		this.rateLimiters = new ConcurrentHashMap<>();
		this.configurations.put(DEFAULT_CONFIG, defaultRateLimiterConfig);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Seq<RateLimiter> getAllRateLimiters() {
		return Array.ofAll(rateLimiters.values());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RateLimiter rateLimiter(final String name) {
		return rateLimiter(name, defaultRateLimiterConfig);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RateLimiter rateLimiter(final String name, final RateLimiterConfig rateLimiterConfig) {
		requireNonNull(name, NAME_MUST_NOT_BE_NULL);
		requireNonNull(rateLimiterConfig, CONFIG_MUST_NOT_BE_NULL);
		return rateLimiters.computeIfAbsent(
				name,
				limitName -> notifyPostCreationConsumers(new AtomicRateLimiter(name, rateLimiterConfig))
		);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RateLimiter rateLimiter(final String name, final Supplier<RateLimiterConfig> rateLimiterConfigSupplier) {
		requireNonNull(name, NAME_MUST_NOT_BE_NULL);
		requireNonNull(rateLimiterConfigSupplier, SUPPLIER_MUST_NOT_BE_NULL);
		return rateLimiters.computeIfAbsent(
				name,
				limitName -> {
					RateLimiterConfig rateLimiterConfig = rateLimiterConfigSupplier.get();
					requireNonNull(rateLimiterConfig, CONFIG_MUST_NOT_BE_NULL);
					AtomicRateLimiter atomicRateLimiter = new AtomicRateLimiter(limitName, rateLimiterConfig);
					notifyPostCreationConsumers(atomicRateLimiter);
					return atomicRateLimiter;
				}
		);
	}
}
