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

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.core.lang.Nullable;
import io.github.resilience4j.utils.AnnotationExtractor;
import io.github.resilience4j.recovery.RecoveryDecorators;
import io.github.resilience4j.recovery.RecoveryMethod;
import io.github.resilience4j.utils.AnnotationExtractor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * This Spring AOP aspect intercepts all methods which are annotated with a {@link Bulkhead} annotation.
 * The aspect protects an annotated method with a Bulkhead. The BulkheadRegistry is used to retrieve an instance of a Bulkhead for
 * a specific name.
 */
@Aspect
public class BulkheadAspect implements Ordered {

	private static final Logger logger = LoggerFactory.getLogger(BulkheadAspect.class);

	private final BulkheadConfigurationProperties bulkheadConfigurationProperties;
	private final BulkheadRegistry bulkheadRegistry;
	private final @Nullable List<BulkheadAspectExt> bulkheadAspectExts;
	private final RecoveryDecorators recoveryDecorators;

	public BulkheadAspect(BulkheadConfigurationProperties backendMonitorPropertiesRegistry, BulkheadRegistry bulkheadRegistry, @Autowired(required = false) List<BulkheadAspectExt> bulkheadAspectExts, RecoveryDecorators recoveryDecorators) {
		this.bulkheadConfigurationProperties = backendMonitorPropertiesRegistry;
		this.bulkheadRegistry = bulkheadRegistry;
		this.bulkheadAspectExts = bulkheadAspectExts;
		this.recoveryDecorators = recoveryDecorators;
	}

	@Pointcut(value = "@within(Bulkhead) || @annotation(Bulkhead)", argNames = "Bulkhead")
	public void matchAnnotatedClassOrMethod(Bulkhead Bulkhead) {
	}

	@Around(value = "matchAnnotatedClassOrMethod(backendMonitored)", argNames = "proceedingJoinPoint, backendMonitored")
	public Object bulkheadAroundAdvice(ProceedingJoinPoint proceedingJoinPoint, @Nullable Bulkhead backendMonitored) throws Throwable {
		Method method = ((MethodSignature) proceedingJoinPoint.getSignature()).getMethod();
		String methodName = method.getDeclaringClass().getName() + "#" + method.getName();
		if (backendMonitored == null) {
			backendMonitored = getBackendMonitoredAnnotation(proceedingJoinPoint);
		}
		if(backendMonitored == null) { //because annotations wasn't found
			return proceedingJoinPoint.proceed();
		}
		String backend = backendMonitored.name();
		io.github.resilience4j.bulkhead.Bulkhead bulkhead = getOrCreateBulkhead(methodName, backend);
		Class<?> returnType = method.getReturnType();

		if (StringUtils.isEmpty(backendMonitored.recovery())) {
			return proceed(proceedingJoinPoint, methodName, bulkhead, returnType);
		}

		RecoveryMethod recoveryMethod = new RecoveryMethod(backendMonitored.recovery(), method, proceedingJoinPoint.getArgs(), proceedingJoinPoint.getTarget());
		return recoveryDecorators.decorate(recoveryMethod, () -> proceed(proceedingJoinPoint, methodName, bulkhead, returnType)).apply();
	}

	private Object proceed(ProceedingJoinPoint proceedingJoinPoint, String methodName, io.github.resilience4j.bulkhead.Bulkhead bulkhead, Class<?> returnType) throws Throwable {
		if (bulkheadAspectExts != null && !bulkheadAspectExts.isEmpty()) {
			for (BulkheadAspectExt bulkHeadAspectExt : bulkheadAspectExts) {
				if (bulkHeadAspectExt.canHandleReturnType(returnType)) {
					return bulkHeadAspectExt.handle(proceedingJoinPoint, bulkhead, methodName);
				}
			}
		}
		if (CompletionStage.class.isAssignableFrom(returnType)) {
			return handleJoinPointCompletableFuture(proceedingJoinPoint, bulkhead, methodName);
		}
		return handleJoinPoint(proceedingJoinPoint, bulkhead, methodName);
	}

	private io.github.resilience4j.bulkhead.Bulkhead getOrCreateBulkhead(String methodName, String backend) {
		io.github.resilience4j.bulkhead.Bulkhead bulkhead = bulkheadRegistry.bulkhead(backend,
				() -> bulkheadConfigurationProperties.createBulkheadConfig(backend));

		if (logger.isDebugEnabled()) {
			logger.debug("Created or retrieved bulkhead '{}' with max concurrent call '{}' and max wait time '{}' for method: '{}'",
					backend, bulkhead.getBulkheadConfig().getMaxConcurrentCalls(),
					bulkhead.getBulkheadConfig().getMaxWaitTime(), methodName);
		}

		return bulkhead;
	}

	@Nullable
	private Bulkhead getBackendMonitoredAnnotation(ProceedingJoinPoint proceedingJoinPoint) {
		if (logger.isDebugEnabled()) {
			logger.debug("bulkhead parameter is null");
		}

		return AnnotationExtractor.extract(proceedingJoinPoint.getTarget().getClass(), Bulkhead.class);
	}

	private Object handleJoinPoint(ProceedingJoinPoint proceedingJoinPoint, io.github.resilience4j.bulkhead.Bulkhead bulkhead, String methodName) throws Throwable {
		if (logger.isDebugEnabled()) {
			logger.debug("bulkhead method invocation for method {}", methodName);
		}
        return bulkhead.executeCheckedSupplier(proceedingJoinPoint::proceed);
	}

	/**
	 * handle the asynchronous completable future flow
	 *
	 * @param proceedingJoinPoint AOPJoinPoint
	 * @param bulkhead            configured bulkhead
	 * @param methodName          bulkhead method name
	 * @return CompletionStage
	 * @throws Throwable
	 */
	private Object handleJoinPointCompletableFuture(ProceedingJoinPoint proceedingJoinPoint, io.github.resilience4j.bulkhead.Bulkhead bulkhead, String methodName) throws Throwable {

		return bulkhead.executeCompletionStage(() -> {
			try {
				return (CompletionStage<?>) proceedingJoinPoint.proceed();
			} catch (Throwable throwable) {
				logger.error("Exception being thrown during bulkhead invocation {} ", methodName, throwable.getCause());
				throw new CompletionException(throwable);
			}
		});
	}

	@Override
	public int getOrder() {
		return bulkheadConfigurationProperties.getBulkheadAspectOrder();
	}
}
