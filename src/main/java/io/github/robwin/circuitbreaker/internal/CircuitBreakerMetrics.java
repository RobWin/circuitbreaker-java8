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
package io.github.robwin.circuitbreaker.internal;


import io.github.robwin.circuitbreaker.CircuitBreaker;

class CircuitBreakerMetrics implements CircuitBreaker.Metrics {

    private final int ringBufferSize;
    private final RingBitSet ringBitSet;

    CircuitBreakerMetrics(int ringBufferSize) {
        this.ringBufferSize = ringBufferSize;
        this.ringBitSet = new RingBitSet(this.ringBufferSize);
    }

    /**
     * Records a failed call and returns the current failure rate in percentage.
     *
     * @return the current failure rate  in percentage.
     */
    float onError() {
        int currentNumberOfFailedCalls = ringBitSet.setNextBit(true);
        return getFailureRate(currentNumberOfFailedCalls);
    }

    /**
     * Records a successful call and returns the current failure rate in percentage.
     *
     * @return the current failure rate in percentage.
     */
    float onSuccess() {
        int currentNumberOfFailedCalls = ringBitSet.setNextBit(false);
        return getFailureRate(currentNumberOfFailedCalls);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public float getFailureRate() {
        return getFailureRate(getNumberOfFailedCalls());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxNumberOfBufferedCalls() {
        return ringBufferSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumberOfSuccessfulCalls() {
        return getNumberOfBufferedCalls() - getNumberOfFailedCalls();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumberOfBufferedCalls() {
        return this.ringBitSet.length();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumberOfFailedCalls() {
        return this.ringBitSet.cardinality();
    }

    private float getFailureRate(int numberOfFailedCalls) {
        if (getNumberOfBufferedCalls() < ringBufferSize) {
            return -1.0f;
        }
        return numberOfFailedCalls * 100.0f / ringBufferSize;
    }
}
