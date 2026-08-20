package com.horse.members.infrastructure;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

	public Page<MemberClassProgressionAuditLog> findPageByMemberId(long memberId, Pageable pageable) {
		final List<MemberClassProgressionAuditLog> content = entityManager.createQuery("""
			SELECT auditLog
			FROM MemberClassProgressionAuditLog auditLog
			WHERE auditLog.member.id = :memberId
			ORDER BY auditLog.createdAt DESC, auditLog.id DESC
			""", MemberClassProgressionAuditLog.class)
			.setParameter("memberId", memberId)
			.setFirstResult((int)pageable.getOffset())
			.setMaxResults(pageable.getPageSize())
			.getResultList();
		final long total = entityManager.createQuery("""
			SELECT COUNT(auditLog)
			FROM MemberClassProgressionAuditLog auditLog
			WHERE auditLog.member.id = :memberId
			""", Long.class)
			.setParameter("memberId", memberId)
			.getSingleResult();
		return new PageImpl<>(content, pageable, total);
	}
}
