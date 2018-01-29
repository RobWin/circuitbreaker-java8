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

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.BDDAssertions.assertThat;
public class CircuitBreakerStateMachineTest {

    private CircuitBreaker circuitBreaker;

    @Before
    public void setUp(){
        circuitBreaker = new CircuitBreakerStateMachine("testName", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .ringBufferSizeInClosedState(5)
                .ringBufferSizeInHalfOpenState(3)
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .recordFailure(error -> !(error instanceof NumberFormatException))
                .build());
    }

    @Test
    public void shouldReturnTheCorrectName() {
        assertThat(circuitBreaker.getName()).isEqualTo("testName");
    }

    @Test
    public void testCircuitBreakerStateMachine() throws InterruptedException {
        // A ring buffer with size 5 is used in closed state
        // Initially the CircuitBreaker is closed
        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(true);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThatMetricsAreReset();

        // Call 1 is a failure
        circuitBreaker.onError(0, new RuntimeException()); // Should create a CircuitBreakerOnErrorEvent (1)
        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(true);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1f);

        // Call 2 is a failure
        circuitBreaker.onError(0, new RuntimeException()); // Should create a CircuitBreakerOnErrorEvent (2)
        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(true);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(2);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1f);

        // Call 3 is a failure
        circuitBreaker.onError(0, new RuntimeException()); // Should create a CircuitBreakerOnErrorEvent (3)
        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(true);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(3);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(3);
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1f);

        // Call 4 is a success
        circuitBreaker.onSuccess(0); // Should create a CircuitBreakerOnSuccessEvent (4)
        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(true);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(4);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(3);
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1f);

        // Call 5 is a success
        circuitBreaker.onSuccess(0); // Should create a CircuitBreakerOnSuccessEvent (5)
        // The ring buffer is filled and the failure rate is above 50%
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN); // Should create a CircuitBreakerOnStateTransitionEvent (6)
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(5);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(3);
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(60.0f);

        sleep(500);

        // The CircuitBreaker is still open, because the wait duration of 1 second is not elapsed
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(false); // Should create a CircuitBreakerOnCallNotPermittedEvent (7)
        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(false); // Should create a CircuitBreakerOnCallNotPermittedEvent (8)
        // Two calls are tried, but not permitted, because the CircuitBreaker is open
        assertThat(circuitBreaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(2);

        sleep(800);

        // The CircuitBreaker switches to half open, because the wait duration of 1 second is elapsed
        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(true);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN); // Should create a CircuitBreakerOnStateTransitionEvent (9)
        // Metrics are reseted
        assertThat(circuitBreaker.getMetrics().getMaxNumberOfBufferedCalls()).isEqualTo(3);
        assertThat(circuitBreaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(0);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(0);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);

        // A ring buffer with size 2 is used in half open state
        // Call 1 is a failure
        circuitBreaker.onError(0, new RuntimeException()); // Should create a CircuitBreakerOnErrorEvent (10)
        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(true);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(0);
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1f);

        // Call 2 is a failure
        circuitBreaker.onError(0, new RuntimeException()); // Should create a CircuitBreakerOnErrorEvent (11)
        // Call 3 is a success
        circuitBreaker.onSuccess(0); // Should create a CircuitBreakerOnSuccessEvent (12)

        // The ring buffer is filled and the failure rate is above 50%
        // The state machine transitions back to OPEN state
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN); // Should create a CircuitBreakerOnStateTransitionEvent (13)
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(3);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isGreaterThan(50f);

        sleep(1300);

        // The CircuitBreaker switches to half open, because the wait duration of 1 second is elapsed
        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(true);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // Call 1 is a failure
        circuitBreaker.onError(0, new RuntimeException()); // Should create a CircuitBreakerOnErrorEvent (15)
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1f);

        // Call 2 should be ignored, because it's a NumberFormatException
        circuitBreaker.onError(0, new NumberFormatException()); // Should create a CircuitBreakerOnIgnoredErrorEvent (16)
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1f);

        // Call 3 is a success
        circuitBreaker.onSuccess(0); // Should create a CircuitBreakerOnSuccessEvent (17)
        // Call 4 is a success
        circuitBreaker.onSuccess(0); // Should create a CircuitBreakerOnSuccessEvent (18)

        // The ring buffer is filled and the failure rate is below 50%
        // The state machine transitions back to CLOSED state
        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(true);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // Should create a CircuitBreakerOnStateTransitionEvent (19)
        assertThat(circuitBreaker.getMetrics().getMaxNumberOfBufferedCalls()).isEqualTo(5);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(3);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1f);

        circuitBreaker.reset(); // Should create a CircuitBreakerOnResetEvent (20)

        // The ring buffer back to initial state
        // The state machine transitions back to CLOSED state
        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(true);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // Should create a CircuitBreakerOnStateTransitionEvent (21)
        assertThatMetricsAreReset();

        circuitBreaker.transitionToDisabledState(); // Should create a CircuitBreakerOnStateTransitionEvent (20)
        // The ring buffer back to initial state
        // The state machine transitions back to CLOSED state
        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(true);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.DISABLED); // Should create a CircuitBreakerOnStateTransitionEvent (21)
        assertThatMetricsAreReset();

        // Call 5 is a success
        circuitBreaker.onSuccess(0); // Should not create a CircuitBreakerOnSuccessEvent
        // Call 6 is a failure
        circuitBreaker.onError(0, new RuntimeException()); // Should not create a CircuitBreakerOnErrorEvent
        // Call 7 is a failure
        circuitBreaker.onError(0, new RuntimeException()); // Should not create a CircuitBreakerOnErrorEvent
        // Call 8 is a failure
        circuitBreaker.onError(0, new RuntimeException()); // Should not create a CircuitBreakerOnErrorEvent
        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(true);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.DISABLED); // Should create a CircuitBreakerOnStateTransitionEvent (21)
        assertThatMetricsAreReset();

        circuitBreaker.transitionToClosedState(); // Should create a CircuitBreakerOnStateTransitionEvent (22)
        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(true);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThatMetricsAreReset();
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(0);

        circuitBreaker.onSuccess(0); // Should create a CircuitBreakerOnSuccessEvent (23)
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1f);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
        assertThat(circuitBreaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(0);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);


        circuitBreaker.transitionToForcedOpenState(); // Should create a CircuitBreakerOnStateTransitionEvent (20)
        // The ring buffer back to initial state
        // The state machine transitions back to CLOSED state
        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(false);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.FORCED_OPEN); // Should create a CircuitBreakerOnStateTransitionEvent (21)
        assertThatMetricsAreReset();
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(0);

        circuitBreaker.onSuccess(0); // Should not create a CircuitBreakerOnSuccessEvent
        circuitBreaker.onSuccess(0); // Should not create a CircuitBreakerOnSuccessEvent
        circuitBreaker.onSuccess(0); // Should not create a CircuitBreakerOnSuccessEvent
        circuitBreaker.onSuccess(0); // Should not create a CircuitBreakerOnSuccessEvent

        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(false);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.FORCED_OPEN); // Should create a CircuitBreakerOnStateTransitionEvent (21)
        assertThatMetricsAreReset();

        circuitBreaker.transitionToOpenState(); // Should create a CircuitBreakerOnStateTransitionEvent (20)
        assertThat(circuitBreaker.isCallPermitted()).isEqualTo(false);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1f);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(0);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
        assertThat(circuitBreaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);

    }

    private void assertThatMetricsAreReset() {
        final CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        assertThat(metrics.getFailureRate()).isEqualTo(-1f);
        assertThat(metrics.getNumberOfBufferedCalls()).isEqualTo(0);
        assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(0);
        assertThat(metrics.getNumberOfNotPermittedCalls()).isEqualTo(0);
    }
}
