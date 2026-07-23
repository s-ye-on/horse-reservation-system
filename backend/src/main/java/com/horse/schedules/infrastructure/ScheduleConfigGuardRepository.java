package com.horse.schedules.infrastructure;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.schedules.domain.ScheduleConfigGuard;

import jakarta.persistence.EntityManager;

@Repository
public class ScheduleConfigGuardRepository {

	private static final String FIND_SINGLETON_FOR_SHARE_SQL = """
		SELECT *
		FROM schedule_config_guard
		WHERE id = 1
		FOR SHARE
		""";
	private static final String FIND_SINGLETON_FOR_UPDATE_SQL = """
		SELECT *
		FROM schedule_config_guard
		WHERE id = 1
		FOR UPDATE
		""";

	private final EntityManager entityManager;

	public ScheduleConfigGuardRepository(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
	public ScheduleConfigGuard findSingletonForShare() {
		return (ScheduleConfigGuard) entityManager.createNativeQuery(
			FIND_SINGLETON_FOR_SHARE_SQL,
			ScheduleConfigGuard.class)
			.getSingleResult();
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public ScheduleConfigGuard findSingletonForUpdate() {
		return (ScheduleConfigGuard) entityManager.createNativeQuery(
			FIND_SINGLETON_FOR_UPDATE_SQL,
			ScheduleConfigGuard.class)
			.getSingleResult();
	}
}
