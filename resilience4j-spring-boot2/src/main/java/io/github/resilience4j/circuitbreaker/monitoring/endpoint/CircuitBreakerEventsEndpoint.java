/*
 * Copyright 2017 Robert Winkler
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
package io.github.resilience4j.circuitbreaker.monitoring.endpoint;


import java.util.Comparator;

import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import io.github.resilience4j.consumer.CircularEventConsumer;
import io.github.resilience4j.consumer.EventConsumerRegistry;


@WebEndpoint(id = "circuitbreaker-events")
public class CircuitBreakerEventsEndpoint {

    private static final String MEDIA_TYPE_TEXT_EVENT_STREAM = "text/event-stream";
    private final EventConsumerRegistry<CircuitBreakerEvent> eventConsumerRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public CircuitBreakerEventsEndpoint(EventConsumerRegistry<CircuitBreakerEvent> eventConsumerRegistry,
                                        CircuitBreakerRegistry circuitBreakerRegistry) {
        this.eventConsumerRegistry = eventConsumerRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @ReadOperation
    public CircuitBreakerEventsEndpointResponse getAllCircuitBreakerEvents() {
        return new CircuitBreakerEventsEndpointResponse(eventConsumerRegistry.getAllEventConsumer()
                .flatMap(CircularEventConsumer::getBufferedEvents)
                .sorted(Comparator.comparing(CircuitBreakerEvent::getCreationTime))
                .map(CircuitBreakerEventDTOFactory::createCircuitBreakerEventDTO).toJavaList());
    }

    @ReadOperation
    public CircuitBreakerEventsEndpointResponse getEventsFilteredByCircuitBreakerName(@Selector String circuitBreakerName) {
        return new CircuitBreakerEventsEndpointResponse(eventConsumerRegistry.getEventConsumer(circuitBreakerName).getBufferedEvents()
                .filter(event -> event.getCircuitBreakerName().equals(circuitBreakerName))
                .map(CircuitBreakerEventDTOFactory::createCircuitBreakerEventDTO).toJavaList());
    }

    @ReadOperation
    public CircuitBreakerEventsEndpointResponse getEventsFilteredByCircuitBreakerNameAndEventType(@Selector String circuitBreakerName,
                                                @Selector String eventType) {
        return new CircuitBreakerEventsEndpointResponse(eventConsumerRegistry.getEventConsumer(circuitBreakerName).getBufferedEvents()
                .filter(event -> event.getCircuitBreakerName().equals(circuitBreakerName))
                .filter(event -> event.getEventType() == CircuitBreakerEvent.Type.valueOf(eventType.toUpperCase()))
                .map(CircuitBreakerEventDTOFactory::createCircuitBreakerEventDTO).toJavaList());
    }

}
