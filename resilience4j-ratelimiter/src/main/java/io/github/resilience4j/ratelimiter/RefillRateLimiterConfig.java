/*
 *
 *  Copyright 2020 Emmanouil Gkatziouras
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
package io.github.resilience4j.ratelimiter;

import io.github.resilience4j.ratelimiter.internal.RefillRateLimiter;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * {@link RefillRateLimiter} is a permission rate based Rate Limiter.
 * Instead of resetting permits based on a permission period the permission release is based on a rate.
 * Therefore {@link RefillRateLimiterConfig#nanosPerPermission} is used which is a product of the division
 * of {@link RateLimiterConfig#limitRefreshPeriod} to {@link RateLimiterConfig#limitForPeriod}.
 */
public class RefillRateLimiterConfig extends RateLimiterConfig {

    private static final String TIMEOUT_DURATION_MUST_NOT_BE_NULL = "TimeoutDuration must not be null";
    private static final String LIMIT_REFRESH_PERIOD_MUST_NOT_BE_NULL = "LimitRefreshPeriod must not be null";
    private static final Duration ACCEPTABLE_REFRESH_PERIOD = Duration.ofNanos(1L);
    private static final boolean DEFAULT_WRITABLE_STACK_TRACE_ENABLED = true;

    private final int permitCapacity;
    private final int initialPermits;
    private final long nanosPerPermission;
    private final boolean writableStackTraceEnabled;

    private RefillRateLimiterConfig(Duration timeoutDuration, int permitCapacity, long nanosPerPermission,
                                    int initialPermits, boolean writableStackTraceEnabled) {
        super(timeoutDuration, Duration.ofNanos(nanosPerPermission*permitCapacity), permitCapacity, writableStackTraceEnabled);
        this.permitCapacity = permitCapacity;
        this.initialPermits = initialPermits;
        this.nanosPerPermission = nanosPerPermission;
        this.writableStackTraceEnabled = writableStackTraceEnabled;
    }

    /**
     * Returns a builder to create a custom RefillRateLimiterConfig.
     *
     * @return a {@link RefillRateLimiterConfig.Builder}
     */
    public static RefillRateLimiterConfig.Builder custom() {
        return new RefillRateLimiterConfig.Builder();
    }

    /**
     * Returns a builder to create a custom RefillRateLimiterConfig using specified config as prototype
     *
     * @param prototype A {@link RefillRateLimiterConfig} prototype.
     * @return a {@link RefillRateLimiterConfig.Builder}
     */
    public static RefillRateLimiterConfig.Builder from(RefillRateLimiterConfig prototype) {
        return new RefillRateLimiterConfig.Builder(prototype);
    }

    /**
     * Creates a default RateLimiter configuration.
     *
     * @return a default RateLimiter configuration.
     */
    public static RefillRateLimiterConfig ofDefaults() {
        return new RefillRateLimiterConfig.Builder().build();
    }

    private static Duration checkTimeoutDuration(final Duration timeoutDuration) {
        return requireNonNull(timeoutDuration, TIMEOUT_DURATION_MUST_NOT_BE_NULL);
    }

    private static Duration checkLimitRefreshPeriod(Duration limitRefreshPeriod) {
        requireNonNull(limitRefreshPeriod, LIMIT_REFRESH_PERIOD_MUST_NOT_BE_NULL);
        boolean refreshPeriodIsTooShort =
            limitRefreshPeriod.compareTo(ACCEPTABLE_REFRESH_PERIOD) < 0;
        if (refreshPeriodIsTooShort) {
            throw new IllegalArgumentException("LimitRefreshPeriod is too short");
        }
        return limitRefreshPeriod;
    }

    private static int checkLimitForPeriod(final int limitForPeriod) {
        if (limitForPeriod < 1) {
            throw new IllegalArgumentException("LimitForPeriod should be greater than 0");
        }
        return limitForPeriod;
    }

    public int getPermitCapacity() {
        return permitCapacity;
    }

    public int getInitialPermits() {
        return initialPermits;
    }

    public boolean isWritableStackTraceEnabled() {
        return writableStackTraceEnabled;
    }

    @Override
    public String toString() {
        return "RefillRateLimiterConfig{" +
            "timeoutDuration=" + getTimeoutDuration() +
            ", permitCapacity=" + permitCapacity +
            ", nanosPerPermission="+ nanosPerPermission +
            ", writableStackTraceEnabled=" + writableStackTraceEnabled +
            '}';
    }

    public static class Builder extends RateLimiterConfig.Builder {

        private Duration timeoutDuration = Duration.ofSeconds(5);
        private Duration limitRefreshPeriod = Duration.ofNanos(500);
        private int limitForPeriod = 50;
        private int permitCapacity = 0;
        private int initialPermits = 0;
        private boolean initialPermitsSet;
        private boolean writableStackTraceEnabled = DEFAULT_WRITABLE_STACK_TRACE_ENABLED;

        public Builder() {
        }

        public Builder(RefillRateLimiterConfig prototype) {
            this.timeoutDuration = prototype.getTimeoutDuration();
            this.limitRefreshPeriod = Duration.ofNanos(prototype.nanosPerPermission);
            this.limitForPeriod = 1;
            this.permitCapacity = prototype.permitCapacity;
            this.writableStackTraceEnabled = prototype.writableStackTraceEnabled;
        }

        /**
         * Builds a RefillRateLimiterConfig
         *
         * @return the RefillRateLimiterConfig
         */
        @Override
        public RefillRateLimiterConfig build() {
            if(permitCapacity < limitForPeriod) {
                permitCapacity = limitForPeriod;
            }

            if(!initialPermitsSet) {
                initialPermits = limitForPeriod;
            }

            final long nanosPerPermission = calculateNanosPerPermission(limitRefreshPeriod, limitForPeriod);

            return new RefillRateLimiterConfig(timeoutDuration, permitCapacity, nanosPerPermission,
                initialPermits ,writableStackTraceEnabled);
        }

        /**
         * Enables writable stack traces. When set to false, {@link Exception#getStackTrace()}
         * returns a zero length array. This may be used to reduce log spam when the circuit breaker
         * is open as the cause of the exceptions is already known (the circuit breaker is
         * short-circuiting calls).
         *
         * @param writableStackTraceEnabled flag to control if stack trace is writable
         * @return the BulkheadConfig.Builder
         */
        @Override
        public RefillRateLimiterConfig.Builder writableStackTraceEnabled(boolean writableStackTraceEnabled) {
            this.writableStackTraceEnabled = writableStackTraceEnabled;
            return this;
        }

        /**
         * Configures the default wait for permission duration. Default value is 5 seconds.
         *
         * @param timeoutDuration the default wait for permission duration
         * @return the RateLimiterConfig.Builder
         */
        @Override
        public RefillRateLimiterConfig.Builder timeoutDuration(final Duration timeoutDuration) {
            this.timeoutDuration = checkTimeoutDuration(timeoutDuration);
            return this;
        }

        /**
         * Configures the period needed for the permissions specified. After each period
         * permissions up to {@link RefillRateLimiterConfig.Builder#limitForPeriod} should be released.
         * Default value is 500 nanoseconds.
         *
         * @param limitRefreshPeriod the period of limit refresh
         * @return the RefillRateLimiterConfig.Builder
         */
        @Override
        public RefillRateLimiterConfig.Builder limitRefreshPeriod(final Duration limitRefreshPeriod) {
            this.limitRefreshPeriod = checkLimitRefreshPeriod(limitRefreshPeriod);
            return this;
        }

        /**
         * Configures the permissions limit for refresh period. Count of permissions released
         * during one rate limiter period specified by {@link RefillRateLimiterConfig.Builder#limitRefreshPeriod}
         * value. Default value is 50.
         *
         * @param limitForPeriod the permissions limit for refresh period
         * @return the RefillRateLimiterConfig.Builder
         */
        @Override
        public RefillRateLimiterConfig.Builder limitForPeriod(final int limitForPeriod) {
            this.limitForPeriod = checkLimitForPeriod(limitForPeriod);
            return this;
        }

        /**
         * Configures the permissions capacity. Count of max permissions available
         * If no value specified the default value is the one
         * specified for {@link RefillRateLimiterConfig.Builder#limitForPeriod}.
         *
         * @param permitCapacity the capacity of permissions
         * @return the RateLimiterConfig.Builder
         */
        public RefillRateLimiterConfig.Builder permitCapacity(final int permitCapacity) {
            this.permitCapacity = permitCapacity;
            return this;
        }

        /**
         * Configures the initial permissions available.
         * If no value specified the default value is the one
         * specified for {@link RefillRateLimiterConfig.Builder#limitForPeriod}.
         *
         * @param initialPermits the initial permits
         * @return the RateLimiterConfig.Builder
         */
        public RefillRateLimiterConfig.Builder initialPermits(final int initialPermits) {
            this.initialPermits = initialPermits;
            this.initialPermitsSet = true;
            return this;
        }

        /**
         * Calculate the nanos needed for one permission
         * @param limitRefreshPeriod
         * @param limitForPeriod
         * @return
         */
        private long calculateNanosPerPermission(Duration limitRefreshPeriod, int limitForPeriod) {
            long permissionsPeriodInNanos = limitRefreshPeriod.toNanos();
            return permissionsPeriodInNanos/limitForPeriod;
        }

    }
}
