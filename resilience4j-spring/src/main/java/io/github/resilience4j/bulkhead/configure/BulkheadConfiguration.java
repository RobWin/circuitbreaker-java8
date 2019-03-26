/*
 * Copyright 2019 lespinsideg
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
package io.github.resilience4j.bulkhead.configure;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.event.BulkheadEvent;
import io.github.resilience4j.consumer.DefaultEventConsumerRegistry;
import io.github.resilience4j.consumer.EventConsumerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link Configuration
 * Configuration} for resilience4j-bulkhead.
 */
@Configuration
public class BulkheadConfiguration {

    @Bean
    public BulkheadRegistry bulkheadRegistry(BulkheadConfigurationProperties bulkheadConfigurationProperties,
                                             EventConsumerRegistry<BulkheadEvent> bulkheadEventConsumerRegistry) {
        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.ofDefaults();
        bulkheadConfigurationProperties.getBackends().forEach(
                (name, properties) -> {
                    BulkheadConfig bulkheadConfig = bulkheadConfigurationProperties.createBulkheadConfig(name);
                    Bulkhead bulkhead = bulkheadRegistry.bulkhead(name, bulkheadConfig);
                    bulkhead.getEventPublisher().onEvent(bulkheadEventConsumerRegistry.createEventConsumer(name, properties.getEventConsumerBufferSize()));
                }
        );
        return bulkheadRegistry;
    }

    @Bean
    public BulkheadAspect bulkheadAspect(BulkheadConfigurationProperties bulkheadConfigurationProperties,
                                               BulkheadRegistry bulkheadRegistry) {
        return new BulkheadAspect(bulkheadConfigurationProperties, bulkheadRegistry);
    }

    /**
     * The EventConsumerRegistry is used to manage EventConsumer instances.
     * The EventConsumerRegistry is used by the BulkheadHealthIndicator to show the latest Bulkhead events
     * for each Bulkhead instance.
     * @return a default EventConsumerRegistry {@link DefaultEventConsumerRegistry}
     */
    @Bean
    public EventConsumerRegistry<BulkheadEvent> bulkheadEventConsumerRegistry() {
        return new DefaultEventConsumerRegistry<>();
    }
}
