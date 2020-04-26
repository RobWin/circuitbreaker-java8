package io.github.resilience4j.retry;

import io.github.resilience4j.retry.transformer.RetryTransformer;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.ReturnType;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.retry.exception.FallbackException;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Singleton
@Internal
@Requires(classes = RetryRegistry.class)
public class RetrySpecificationInterceptor implements MethodInterceptor<Object,Object> {

    private static final Logger LOG = LoggerFactory.getLogger(RetrySpecificationInterceptor.class);

    private final RetryRegistry retryRegistry;
    private final BeanContext beanContext;
    private static final ScheduledExecutorService retryExecutorService = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    public RetrySpecificationInterceptor(BeanContext beanContext, RetryRegistry retryRegistry) {
        this.retryRegistry = retryRegistry;
        this.beanContext = beanContext;
    }

    public Optional<? extends MethodExecutionHandle<?, Object>> findFallbackMethod(MethodInvocationContext<Object, Object> context) {
        ExecutableMethod executableMethod = context.getExecutableMethod();
        final String fallbackMethod = executableMethod.stringValue(io.github.resilience4j.retry.annotation.Retry.class, "fallbackMethod").orElse("");
        Class<?> declaringType = context.getDeclaringType();
        return beanContext.findExecutionHandle(declaringType, fallbackMethod, context.getArgumentTypes());
    }

    @Override
    public Object intercept(MethodInvocationContext context) {
        Optional<AnnotationValue<io.github.resilience4j.retry.annotation.Retry>> opt = context.findAnnotation(io.github.resilience4j.retry.annotation.Retry.class);
        if (!opt.isPresent()) {
            return context.proceed();
        }

        ExecutableMethod executableMethod = context.getExecutableMethod();
        final String name = executableMethod.stringValue(io.github.resilience4j.retry.annotation.Retry.class).orElse("default");
        Retry retry = retryRegistry.retry(name);

        ReturnType<Object> rt = context.getReturnType();
        Class<Object> returnType = rt.getType();
        if (CompletionStage.class.isAssignableFrom(returnType)) {
            return handleFuture(context, retry);
        } else if (Publishers.isConvertibleToPublisher(returnType)) {
            return handlerForReactiveType(context, retry);
        }

        try {
            return retry.executeCheckedSupplier(context::proceed);
        } catch (RuntimeException exception) {
            return resolveFallback(context, exception);
        } catch (Throwable throwable) {
            throw new FallbackException("Error invoking fallback for type [" + context.getTarget().getClass().getName() + "]: " + throwable.getMessage(), throwable);
        }
    }

    Object resolveFallback(MethodInvocationContext<Object, Object> context, RuntimeException exception) {
        if (exception instanceof NoAvailableServiceException) {
            NoAvailableServiceException ex = (NoAvailableServiceException) exception;
            if (LOG.isErrorEnabled()) {
                LOG.debug(ex.getMessage(), ex);
                LOG.error("Type [{}] attempting to resolve fallback for unavailable service [{}]", context.getTarget().getClass().getName(), ex.getServiceID());
            }
        } else {
            if (LOG.isErrorEnabled()) {
                LOG.error("Type [" + context.getTarget().getClass().getName() + "] executed with error: " + exception.getMessage(), exception);
            }
        }
        Optional<? extends MethodExecutionHandle<?, Object>> fallback = findFallbackMethod(context);
        if (fallback.isPresent()) {
            MethodExecutionHandle<?, Object> fallbackMethod = fallback.get();
            try {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Type [{}] resolved fallback: {}", context.getTarget().getClass().getName(), fallbackMethod);
                }
                return fallbackMethod.invoke(context.getParameterValues());
            } catch (Exception e) {
                throw new FallbackException("Error invoking fallback for type [" + context.getTarget().getClass().getName() + "]: " + e.getMessage(), e);
            }
        } else {
            throw exception;
        }
    }

    private Object handlerForReactiveType(MethodInvocationContext<Object, Object> context, Retry retry) {
        Object result = context.proceed();
        if (result == null) {
            return result;
        }
        Flowable<Object> flowable = ConversionService.SHARED
            .convert(result, Flowable.class)
            .orElseThrow(() -> new FallbackException("Unsupported Reactive type: " + result));
        flowable = flowable.compose(RetryTransformer.of(retry)).onErrorResumeNext(throwable -> {
            Optional<? extends MethodExecutionHandle<?, Object>> fallbackMethod = findFallbackMethod(context);
            if (fallbackMethod.isPresent()) {
                MethodExecutionHandle<?, Object> fallbackHandle = fallbackMethod.get();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Type [{}] resolved fallback: {}", context.getTarget().getClass(), fallbackHandle);
                }
                Object fallbackResult;
                try {
                    fallbackResult = fallbackHandle.invoke(context.getParameterValues());
                } catch (Exception e) {
                    return Flowable.error(throwable);
                }
                if (fallbackResult == null) {
                    return Flowable.error(new FallbackException("Fallback handler [" + fallbackHandle + "] returned null value"));
                } else {
                    return ConversionService.SHARED.convert(fallbackResult, Publisher.class)
                        .orElseThrow(() -> new FallbackException("Unsupported Reactive type: " + fallbackResult));
                }
            }
            return Flowable.error(throwable);
        });
        return ConversionService.SHARED
            .convert(flowable, context.getReturnType().asArgument())
            .orElseThrow(() -> new FallbackException("Unsupported Reactive type: " + result));
    }

    private Object handleFuture(MethodInvocationContext<Object, Object> context, Retry retry) {
        Object result = context.proceed();
        if (result == null) {
            return result;
        }
        CompletableFuture<Object> newFuture = new CompletableFuture<>();
        retry.executeCompletionStage(retryExecutorService, () -> ((CompletableFuture<?>) result)).whenComplete((o, throwable) -> {
            if (throwable == null) {
                newFuture.complete(o);
            } else {
                Optional<? extends MethodExecutionHandle<?, Object>> fallbackMethod = findFallbackMethod(context);
                if (fallbackMethod.isPresent()) {
                    MethodExecutionHandle<?, Object> fallbackHandle = fallbackMethod.get();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Type [{}] resolved fallback: {}", context.getTarget().getClass(), fallbackHandle);
                    }
                    try {
                        CompletableFuture<Object> resultingFuture = (CompletableFuture<Object>) fallbackHandle.invoke(context.getParameterValues());
                        if (resultingFuture == null) {
                            newFuture.completeExceptionally(new FallbackException("Fallback handler [" + fallbackHandle + "] returned null value"));
                        } else {
                            resultingFuture.whenComplete((o1, throwable1) -> {
                                if (throwable1 == null) {
                                    newFuture.complete(o1);
                                } else {
                                    newFuture.completeExceptionally(throwable1);
                                }
                            });
                        }
                    } catch (Exception e) {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error invoking Fallback [" + fallbackHandle + "]: " + e.getMessage(), e);
                        }
                        newFuture.completeExceptionally(throwable);
                    }
                } else {
                    newFuture.completeExceptionally(throwable);
                }
            }

        });
        return newFuture;
    }
}
