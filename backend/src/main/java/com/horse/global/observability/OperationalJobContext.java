package com.horse.global.observability;

public record OperationalJobContext(
	OperationalJobName jobName,
	OperationalJobTrigger trigger,
	OperationalJobActorType actorType,
	String actorSubject
) {

	private static final String SYSTEM_SUBJECT = "system";

	public static OperationalJobContext scheduled(OperationalJobName jobName) {
		return new OperationalJobContext(
			jobName,
			OperationalJobTrigger.SCHEDULED,
			OperationalJobActorType.SYSTEM,
			SYSTEM_SUBJECT);
	}

	public static OperationalJobContext manual(OperationalJobName jobName, String adminSubject) {
		return new OperationalJobContext(
			jobName,
			OperationalJobTrigger.MANUAL,
			OperationalJobActorType.ADMIN,
			adminSubject);
	}
}
