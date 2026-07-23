package com.horse.schedules.infrastructure;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.schedules.domain.ScheduleAuditLog;
import com.horse.schedules.domain.ScheduleAuditTargetType;

import jakarta.persistence.EntityManager;

@Repository
public class ScheduleAuditLogRepository {

	private final EntityManager entityManager;

	public ScheduleAuditLogRepository(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public ScheduleAuditLog append(ScheduleAuditLog auditLog) {
		entityManager.persist(auditLog);
		return auditLog;
	}

	public List<ScheduleAuditLog> findAllByTarget(
		ScheduleAuditTargetType targetType,
		String targetKey
	) {
		return entityManager.createQuery("""
			SELECT auditLog
			FROM ScheduleAuditLog auditLog
			WHERE auditLog.targetType = :targetType
			  AND auditLog.targetKey = :targetKey
			ORDER BY auditLog.createdAt, auditLog.id
			""", ScheduleAuditLog.class)
			.setParameter("targetType", targetType)
			.setParameter("targetKey", targetKey)
			.getResultList();
	}
}
