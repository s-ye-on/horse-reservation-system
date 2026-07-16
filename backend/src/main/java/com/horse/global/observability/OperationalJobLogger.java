package com.horse.global.observability;

import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OperationalJobLogger {

	private static final Logger LOGGER = LoggerFactory.getLogger(OperationalJobLogger.class);

	public <T> T execute(
		OperationalJobContext context,
		Supplier<T> operation,
		Function<T, String> resultSummary
	) {
		try {
			final T result = operation.get();
			LOGGER.info(
				"Operational job completed. jobName={}, trigger={}, actorType={}, actorSubject={}, result={}",
				context.jobName(),
				context.trigger(),
				context.actorType(),
				context.actorSubject(),
				resultSummary.apply(result));
			return result;
		}
		catch (RuntimeException exception) {
			LOGGER.error(
				"Operational job failed. jobName={}, trigger={}, actorType={}, actorSubject={}, exceptionType={}",
				context.jobName(),
				context.trigger(),
				context.actorType(),
				context.actorSubject(),
				exception.getClass().getName(),
				exception);
			throw exception;
		}
	}
}
