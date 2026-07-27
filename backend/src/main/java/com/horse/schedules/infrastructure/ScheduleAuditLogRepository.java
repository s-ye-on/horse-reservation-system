package com.horse.schedules.infrastructure;

import java.util.List;
import java.util.Optional;

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

	public Optional<ScheduleAuditLog> findLatestByTargetAndAction(
		ScheduleAuditTargetType targetType,
		String targetKey,
		String action
	) {
		return entityManager.createQuery("""
			SELECT auditLog
			FROM ScheduleAuditLog auditLog
			WHERE auditLog.targetType = :targetType
			  AND auditLog.targetKey = :targetKey
			  AND auditLog.action = :action
			ORDER BY auditLog.id DESC
			""", ScheduleAuditLog.class)
			.setParameter("targetType", targetType)
			.setParameter("targetKey", targetKey)
			.setParameter("action", action)
			.setMaxResults(1)
			.getResultStream()
			.findFirst();
	}
}
