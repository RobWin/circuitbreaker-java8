/*
 *
 *  Copyright 2019: Bohdan Storozhuk, Mahmoud Romeh, Tomasz Skowroński
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
package io.github.resilience4j.bulkhead.adaptive.internal;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.adaptive.AdaptiveBulkhead;
import io.github.resilience4j.bulkhead.adaptive.AdaptiveBulkheadConfig;
import io.github.resilience4j.bulkhead.event.*;
import io.github.resilience4j.bulkhead.internal.SemaphoreBulkhead;
import io.github.resilience4j.core.EventConsumer;
import io.github.resilience4j.core.EventProcessor;
import io.github.resilience4j.core.EventPublisher;
import io.github.resilience4j.core.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

public class AdaptiveBulkheadStateMachine implements AdaptiveBulkhead {

    private static final Logger LOG = LoggerFactory.getLogger(AdaptiveBulkheadStateMachine.class);
    // TODO remove
    public static final boolean RESET_METRICS_ON_TRANSITION = true;

    private final String name;
    private final AtomicReference<AdaptiveBulkheadState> stateReference;
    private final AdaptiveBulkheadConfig adaptiveBulkheadConfig;
    private final AdaptiveBulkheadEventProcessor eventProcessor;
    private final AdaptiveBulkheadMetrics metrics;
    private final Bulkhead innerBulkhead;
    private final AdaptationCalculator adaptationCalculator;

    public AdaptiveBulkheadStateMachine(@NonNull String name,
        @NonNull AdaptiveBulkheadConfig adaptiveBulkheadConfig) {
        this.name = name;
        this.adaptiveBulkheadConfig = Objects
            .requireNonNull(adaptiveBulkheadConfig, "Config must not be null");
        BulkheadConfig internalBulkheadConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(adaptiveBulkheadConfig.getInitialConcurrentCalls())
            .maxWaitDuration(adaptiveBulkheadConfig.getMaxWaitDuration())
            .build();
        this.innerBulkhead = new SemaphoreBulkhead(name + "-internal", internalBulkheadConfig);
        this.metrics = new AdaptiveBulkheadMetrics(
            adaptiveBulkheadConfig, innerBulkhead.getMetrics());
        this.stateReference = new AtomicReference<>(new SlowStartState(metrics));
        this.eventProcessor = new AdaptiveBulkheadEventProcessor();
        this.adaptationCalculator = new AdaptationCalculator(this);
    }

    @Override
    public boolean tryAcquirePermission() {
        return stateReference.get().tryAcquirePermission()
            && innerBulkhead.tryAcquirePermission();
    }

    @Override
    public void acquirePermission() {
        stateReference.get().acquirePermission();
        innerBulkhead.acquirePermission();
    }

    @Override
    public void releasePermission() {
        stateReference.get().releasePermission();
        innerBulkhead.releasePermission();
    }

    /**
     * @param duration     call time
     * @param durationUnit call time unit
     */
    @Override
    public void onSuccess(long duration, TimeUnit durationUnit) {
        releasePermission(); // ?
        stateReference.get().onSuccess(duration, durationUnit);
        publishBulkheadEvent(new BulkheadOnSuccessEvent(
            shortName(innerBulkhead), Collections.emptyMap()));
    }

    /**
     * @param startTime    call start time in millis or 0
     * @param durationUnit call time unit
     * @param throwable    an error
     */
    @Override
    public void onError(long startTime, TimeUnit durationUnit, Throwable throwable) {
        if (adaptiveBulkheadConfig.getIgnoreExceptionPredicate().test(throwable)) {
            releasePermission();
            publishBulkheadEvent(new BulkheadOnIgnoreEvent(
                shortName(innerBulkhead), errorData(throwable)));
        } else if (startTime != 0
            && adaptiveBulkheadConfig.getRecordExceptionPredicate().test(throwable)) {
            releasePermission(); // ?
            stateReference.get().onError(timeUntilNow(startTime), durationUnit, throwable);
            publishBulkheadEvent(new BulkheadOnErrorEvent(
                shortName(innerBulkhead), errorData(throwable)));
        } else if (startTime != 0) {
            onSuccess(timeUntilNow(startTime), durationUnit);
        }
    }

    private long timeUntilNow(long start) {
        return Duration.between(Instant.ofEpochMilli(start), Instant.now()).toMillis();
    }

    @Override
    public AdaptiveBulkheadConfig getBulkheadConfig() {
        return adaptiveBulkheadConfig;
    }

    @Override
    public Metrics getMetrics() {
        return metrics;
    }

    @Override
    public AdaptiveEventPublisher getEventPublisher() {
        return eventProcessor;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void transitionToCongestionAvoidance() {
        stateTransition(State.CONGESTION_AVOIDANCE,
            current -> new CongestionAvoidance(current.getMetrics()));
    }

    @Override
    public void transitionToSlowStart() {
        stateTransition(State.SLOW_START,
            current -> new SlowStartState(current.getMetrics()));
    }

    private void stateTransition(State newState,
        UnaryOperator<AdaptiveBulkheadState> newStateGenerator) {
        LOG.debug("stateTransition to {}", newState);
        AdaptiveBulkheadState previous = stateReference.getAndUpdate(newStateGenerator);
        publishBulkheadEvent(new BulkheadOnStateTransitionEvent(
            name, Collections.emptyMap(), previous.getState(), newState));
    }

    private void changeConcurrencyLimit(int newValue) {
        int oldValue = innerBulkhead.getBulkheadConfig().getMaxConcurrentCalls();
        if (newValue > oldValue) {
            changeInternals(oldValue, newValue);
            publishBulkheadOnLimitIncreasedEvent(newValue);
        } else if (newValue < oldValue) {
            changeInternals(oldValue, newValue);
            publishBulkheadOnLimitDecreasedEvent(newValue);
        }
    }

    private void changeInternals(int oldValue, int newValue) {
        LOG.debug("changeConcurrencyLimit from {} to {}", oldValue, newValue);
        innerBulkhead.changeConfig(
            BulkheadConfig.from(innerBulkhead.getBulkheadConfig())
                .maxConcurrentCalls(newValue)
                .build());
        if (RESET_METRICS_ON_TRANSITION) {
            metrics.resetRecords();
        }
    }

    private void publishBulkheadOnLimitIncreasedEvent(int maxConcurrentCalls) {
        publishBulkheadEvent(new BulkheadOnLimitIncreasedEvent(
            shortName(innerBulkhead),
            limitChangeEventData(
                innerBulkhead.getBulkheadConfig().getMaxWaitDuration().toMillis(),
                maxConcurrentCalls)));
    }

    private void publishBulkheadOnLimitDecreasedEvent(int maxConcurrentCalls) {
        publishBulkheadEvent(new BulkheadOnLimitDecreasedEvent(
            shortName(innerBulkhead),
            limitChangeEventData(
                innerBulkhead.getBulkheadConfig().getMaxWaitDuration().toMillis(),
                maxConcurrentCalls)));
    }

    /**
     * Although the strategy is referred to as slow start, its congestion window growth is quite
     * aggressive, more aggressive than the congestion avoidance phase.
     */
    private class SlowStartState implements AdaptiveBulkheadState {

        private final AdaptiveBulkheadMetrics adaptiveBulkheadMetrics;
        private final AtomicBoolean isSlowStart;

        SlowStartState(AdaptiveBulkheadMetrics adaptiveBulkheadMetrics) {
            this.adaptiveBulkheadMetrics = adaptiveBulkheadMetrics;
            this.isSlowStart = new AtomicBoolean(true);
        }

        /**
         * @return "always" true
         */
        @Override
        public boolean tryAcquirePermission() {
            return isSlowStart.get();
        }

        @Override
        public void acquirePermission() {
            // noOp
        }

        @Override
        public void releasePermission() {
            // noOp
        }

        @Override
        public void onError(long duration, TimeUnit durationUnit, Throwable throwable) {
            // AdaptiveBulkheadMetrics is thread-safe
            checkIfThresholdsExceeded(adaptiveBulkheadMetrics.onError(duration, durationUnit));
        }

        @Override
        public void onSuccess(long duration, TimeUnit durationUnit) {
            // AdaptiveBulkheadMetrics is thread-safe
            checkIfThresholdsExceeded(adaptiveBulkheadMetrics.onSuccess(duration, durationUnit));
        }

        /**
         * Transitions to CONGESTION_AVOIDANCE state when thresholds have been exceeded.
         *
         * @param result the Result
         */
        private void checkIfThresholdsExceeded(AdaptiveBulkheadMetrics.Result result) {
            logStateDetails(result);
            if (isSlowStart.get()) {
                switch (result) {
                    case BELOW_THRESHOLDS:
                        changeConcurrencyLimit(adaptationCalculator.increase());
                        break;
                    case ABOVE_THRESHOLDS:
                        if (isSlowStart.compareAndSet(true, false)) {
                            changeConcurrencyLimit(adaptationCalculator.decrease());
                            transitionToCongestionAvoidance();
                        }
                        break;
                }
            }
        }

        /**
         * Get the state of the AdaptiveBulkhead
         */
        @Override
        public AdaptiveBulkhead.State getState() {
            return State.SLOW_START;
        }

        /**
         * Get metrics of the AdaptiveBulkhead
         */
        @Override
        public AdaptiveBulkheadMetrics getMetrics() {
            return adaptiveBulkheadMetrics;
        }

    }

    private void logStateDetails(AdaptiveBulkheadMetrics.Result result) {
        LOG.debug("calls:{}/{}, rate:{} result:{}",
            metrics.getNumberOfBufferedCalls(),
            adaptiveBulkheadConfig.getMinimumNumberOfCalls(),
            Math.max(metrics.getSnapshot().getFailureRate(), metrics.getSnapshot().getSlowCallRate()),
            result);
    }


    private class CongestionAvoidance implements AdaptiveBulkheadState {

        private final AdaptiveBulkheadMetrics adaptiveBulkheadMetrics;
        private final AtomicBoolean congestionAvoidance;

        CongestionAvoidance(AdaptiveBulkheadMetrics adaptiveBulkheadMetrics) {
            this.adaptiveBulkheadMetrics = adaptiveBulkheadMetrics;
            this.congestionAvoidance = new AtomicBoolean(true);
        }

        /**
         * @return "always" true
         */
        @Override
        public boolean tryAcquirePermission() {
            return congestionAvoidance.get();
        }

        @Override
        public void acquirePermission() {
            // noOp
        }

        @Override
        public void releasePermission() {
            // noOp
        }

        @Override
        public void onError(long duration, TimeUnit durationUnit, Throwable throwable) {
            // AdaptiveBulkheadMetrics is thread-safe
            checkIfThresholdsExceeded(adaptiveBulkheadMetrics.onError(duration, durationUnit));
        }

        @Override
        public void onSuccess(long duration, TimeUnit durationUnit) {
            // AdaptiveBulkheadMetrics is thread-safe
            checkIfThresholdsExceeded(adaptiveBulkheadMetrics.onSuccess(duration, durationUnit));
        }

        /**
         * Transitions to SLOW_START state when Minimum Concurrency Limit have been reached.
         *
         * @param result the Result
         */
        private void checkIfThresholdsExceeded(AdaptiveBulkheadMetrics.Result result) {
            logStateDetails(result);
            if (congestionAvoidance.get()) {
                switch (result) {
                    case BELOW_THRESHOLDS:
                        if (isConcurrencyLimitTooLow()) {
                            if (congestionAvoidance.compareAndSet(true, false)) {
                                transitionToSlowStart();
                            }
                        } else {
                            changeConcurrencyLimit(adaptationCalculator.increment());
                        }
                        break;
                    case ABOVE_THRESHOLDS:
                        changeConcurrencyLimit(adaptationCalculator.decrease());
                        break;
                }
            }
        }

        private boolean isConcurrencyLimitTooLow() {
            return getMetrics().getMaxAllowedConcurrentCalls()
                == adaptiveBulkheadConfig.getMinConcurrentCalls();
        }

        /**
         * Get the state of the AdaptiveBulkhead
         */
        @Override
        public AdaptiveBulkhead.State getState() {
            return State.CONGESTION_AVOIDANCE;
        }

        /**
         * Get metrics of the AdaptiveBulkhead
         */
        @Override
        public AdaptiveBulkheadMetrics getMetrics() {
            return adaptiveBulkheadMetrics;
        }

    }

    private static class AdaptiveBulkheadEventProcessor extends
        EventProcessor<AdaptiveBulkheadEvent> implements AdaptiveEventPublisher,
        EventConsumer<AdaptiveBulkheadEvent> {

        @Override
        public EventPublisher<?> onLimitIncreased(
            EventConsumer<BulkheadOnLimitIncreasedEvent> eventConsumer) {
            registerConsumer(BulkheadOnLimitIncreasedEvent.class.getSimpleName(), eventConsumer);
            return this;
        }

        @Override
        public EventPublisher<?> onLimitDecreased(
            EventConsumer<BulkheadOnLimitDecreasedEvent> eventConsumer) {
            registerConsumer(BulkheadOnLimitDecreasedEvent.class.getSimpleName(), eventConsumer);
            return this;
        }

        @Override
        public EventPublisher<?> onSuccess(EventConsumer<BulkheadOnSuccessEvent> eventConsumer) {
            registerConsumer(BulkheadOnSuccessEvent.class.getSimpleName(), eventConsumer);
            return this;
        }

        @Override
        public EventPublisher<?> onError(EventConsumer<BulkheadOnErrorEvent> eventConsumer) {
            registerConsumer(BulkheadOnErrorEvent.class.getSimpleName(), eventConsumer);
            return this;
        }

        @Override
        public EventPublisher<?> onIgnoredError(
            EventConsumer<BulkheadOnIgnoreEvent> eventConsumer) {
            registerConsumer(BulkheadOnIgnoreEvent.class.getSimpleName(), eventConsumer);
            return this;
        }

        @Override
        public EventPublisher<?> onStateTransition(
            EventConsumer<BulkheadOnStateTransitionEvent> eventConsumer) {
            registerConsumer(BulkheadOnStateTransitionEvent.class.getSimpleName(), eventConsumer);
            return this;
        }

        @Override
        public void consumeEvent(AdaptiveBulkheadEvent event) {
            super.processEvent(event);
        }
    }

    @Override
    public String toString() {
        return String.format("AdaptiveBulkhead '%s'", this.name);
    }

    private interface AdaptiveBulkheadState {

        boolean tryAcquirePermission();

        void acquirePermission();

        void releasePermission();

        void onError(long duration, TimeUnit durationUnit, Throwable throwable);

        void onSuccess(long duration, TimeUnit durationUnit);

        AdaptiveBulkhead.State getState();

        AdaptiveBulkheadMetrics getMetrics();

        /**
         * Should the AdaptiveBulkhead in this state publish events
         *
         * @return a boolean signaling if the events should be published
         */
        default boolean shouldPublishEvents(AdaptiveBulkheadEvent event) {
            return event.getEventType().forcePublish || getState().allowPublish;
        }

    }

    /**
     * @param eventSupplier the event supplier to be pushed to consumers
     */
    private void publishBulkheadEvent(AdaptiveBulkheadEvent eventSupplier) {
        if (eventProcessor.hasConsumers()) {
            eventProcessor.consumeEvent(eventSupplier);
        }
    }

    // it's a workaround for "-internal" suffix
    @Deprecated
    private static String shortName(Bulkhead bulkhead) {
        int cut = bulkhead.getName().indexOf('-');
        return cut > 0 ? bulkhead.getName().substring(0, cut) : bulkhead.getName();
    }

    /**
     * @param waitTimeMillis        new wait time
     * @param newMaxConcurrentCalls new max concurrent data
     * @return map of kep value string of the event properties
     */
    private static Map<String, String> limitChangeEventData(long waitTimeMillis,
        int newMaxConcurrentCalls) {
        Map<String, String> eventData = new HashMap<>();
        eventData.put("newMaxConcurrentCalls", String.valueOf(newMaxConcurrentCalls));
        // TODO do we need newWaitTimeMillis here?
        eventData.put("newWaitTimeMillis", String.valueOf(waitTimeMillis));
        return eventData;
    }

    /**
     * @param throwable error exception to be wrapped into the event data
     * @return map of kep value string of the event properties
     */
    private Map<String, String> errorData(Throwable throwable) {
        Map<String, String> eventData = new HashMap<>();
        eventData.put("exceptionMsg", throwable.getMessage());
        return eventData;
    }

}
