/*
 *
 *  Copyright 2019: Mahmoud Romeh
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
package io.github.resilience4j.bulkhead.adaptive;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.core.lang.NonNull;
import io.github.resilience4j.core.metrics.Metrics;


/**
 * Limit adapter interface for adoptive bulkhead {@link AdaptiveBulkhead}
 */
public interface LimitPolicy {

	/**
	 * adapt the concurrency limit of the bulkhead based into the implemented logic
	 * by updating the bulkhead concurrent limit through {@link Bulkhead#changeConfig(BulkheadConfig)} ()} if the limiter algorithm trigger a need for an update
	 *
	 * @param callTime the protected service by bulkhead call total execution time
	 */
	LimitResult adaptLimitIfAny(@NonNull long callTime, boolean isSuccess, int inFlight);


	/**
	 * @return the current associated metrics implementation
	 */
	Metrics getMetrics();


}
