package io.github.resilience4j.common.bulkhead.configuration;

import java.time.Duration;

import static io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig.DEFAULT_KEEP_ALIVE_TIME;

/**
 * common spring configuration properties fir {@link io.github.resilience4j.bulkhead.ThreadPoolBulkhead}
 */
public class ThreadPoolProperties {

	private int maxThreadPoolSize;
	private int coreThreadPoolSize;
	private int queueCapacity;
	private Duration keepAliveDuration;

	public int getMaxThreadPoolSize() {
		return maxThreadPoolSize;
	}

	public ThreadPoolProperties setMaxThreadPoolSize(int maxThreadPoolSize) {
		this.maxThreadPoolSize = maxThreadPoolSize;
		return this;
	}

	public int getCoreThreadPoolSize() {
		return coreThreadPoolSize;
	}

	public ThreadPoolProperties setCoreThreadPoolSize(int coreThreadPoolSize) {
		this.coreThreadPoolSize = coreThreadPoolSize;
		return this;
	}

	public int getQueueCapacity() {
		return queueCapacity;
	}

	public ThreadPoolProperties setQueueCapacity(int queueCapacity) {
		this.queueCapacity = queueCapacity;
		return this;
	}

	public long getKeepAliveTime() {
		if (keepAliveDuration != null) {
			return keepAliveDuration.toMillis();
		} else {
			return DEFAULT_KEEP_ALIVE_TIME;
		}
	}

	public ThreadPoolProperties setKeepAliveTime(long keepAliveTime) {
		this.keepAliveDuration = Duration.ofMillis(keepAliveTime);
		return this;
	}

	public Duration getKeepAliveDuration() {
		return keepAliveDuration;
	}

	public ThreadPoolProperties setKeepAliveDuration(Duration keepAliveDuration) {
		this.keepAliveDuration = keepAliveDuration;
		return this;
	}

}
