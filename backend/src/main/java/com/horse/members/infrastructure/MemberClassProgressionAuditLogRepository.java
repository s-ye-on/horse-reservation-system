package com.horse.members.infrastructure;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.members.domain.MemberClassProgressionAuditLog;

@Repository
public class MemberClassProgressionAuditLogRepository {

	private final EntityManager entityManager;

	public MemberClassProgressionAuditLogRepository(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public MemberClassProgressionAuditLog append(MemberClassProgressionAuditLog auditLog) {
		entityManager.persist(auditLog);
		return auditLog;
	}

	public List<MemberClassProgressionAuditLog> findAllByMemberId(long memberId) {
		return entityManager.createQuery("""
			SELECT auditLog
			FROM MemberClassProgressionAuditLog auditLog
			WHERE auditLog.member.id = :memberId
			ORDER BY auditLog.createdAt, auditLog.id
			""", MemberClassProgressionAuditLog.class)
			.setParameter("memberId", memberId)
			.getResultList();
	}
}
