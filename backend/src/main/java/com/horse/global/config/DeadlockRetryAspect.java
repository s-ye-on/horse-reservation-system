package com.horse.global.config;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class DeadlockRetryAspect {

	private static final Logger LOGGER = LoggerFactory.getLogger(DeadlockRetryAspect.class);
	private static final int MYSQL_DEADLOCK_ERROR_CODE = 1213;
	private static final String MYSQL_DEADLOCK_SQL_STATE = "40001";
	private static final int MAX_ATTEMPTS = 3;
	private static final long BASE_BACKOFF_MILLIS = 10;
	private static final long MAX_BACKOFF_MILLIS = 40;

	@Around(
		"execution(* com.horse..application..*(..))"
			+ " && @annotation(org.springframework.transaction.annotation.Transactional)")
	public Object retryWriteTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
		final Transactional transactional = findTransactional(joinPoint);
		if (transactional == null
			|| transactional.readOnly()
			|| TransactionSynchronizationManager.isActualTransactionActive()) {
			return joinPoint.proceed();
		}

		final String operation = operationName(joinPoint);
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				return joinPoint.proceed();
			}
			catch (Throwable exception) {
				if (!isMySqlDeadlock(exception)) {
					throw exception;
				}
				if (attempt == MAX_ATTEMPTS) {
					LOGGER.error(
						"mysql_deadlock_retry_exhausted operation={} attempts={}",
						operation,
						MAX_ATTEMPTS);
					throw exception;
				}
				LOGGER.warn(
					"mysql_deadlock_retry operation={} failedAttempt={} maxAttempts={}",
					operation,
					attempt,
					MAX_ATTEMPTS);
				pauseWithBackoffAndJitter(attempt);
			}
		}
		throw new AssertionError("deadlock retry loop exhausted without terminal result");
	}

	private Transactional findTransactional(ProceedingJoinPoint joinPoint) {
		final MethodSignature signature = (MethodSignature)joinPoint.getSignature();
		final Method targetMethod = AopUtils.getMostSpecificMethod(
			signature.getMethod(),
			joinPoint.getTarget().getClass());
		return AnnotatedElementUtils.findMergedAnnotation(targetMethod, Transactional.class);
	}

	private boolean isMySqlDeadlock(Throwable exception) {
		final Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		Throwable current = exception;
		while (current != null && visited.add(current)) {
			if (current instanceof SQLException sqlException
				&& containsDeadlock(sqlException, visited)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private boolean containsDeadlock(SQLException exception, Set<Throwable> visited) {
		SQLException current = exception;
		while (current != null) {
			if (current.getErrorCode() == MYSQL_DEADLOCK_ERROR_CODE
				&& MYSQL_DEADLOCK_SQL_STATE.equals(current.getSQLState())) {
				return true;
			}
			final SQLException next = current.getNextException();
			if (next == null || !visited.add(next)) {
				return false;
			}
			current = next;
		}
		return false;
	}

	private void pauseWithBackoffAndJitter(int failedAttempt) {
		final long exponentialBackoff = BASE_BACKOFF_MILLIS << (failedAttempt - 1);
		final long upperBound = Math.min(exponentialBackoff, MAX_BACKOFF_MILLIS);
		final long delayMillis = ThreadLocalRandom.current().nextLong(1, upperBound + 1);
		LockSupport.parkNanos(Duration.ofMillis(delayMillis).toNanos());
	}

	private String operationName(ProceedingJoinPoint joinPoint) {
		final MethodSignature signature = (MethodSignature)joinPoint.getSignature();
		return signature.getDeclaringType().getSimpleName() + "." + signature.getMethod().getName();
	}
}
