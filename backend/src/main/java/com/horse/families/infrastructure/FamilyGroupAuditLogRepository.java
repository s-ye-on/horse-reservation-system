package com.horse.families.infrastructure;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.families.domain.FamilyGroupAuditLog;

@Repository
public class FamilyGroupAuditLogRepository {

	private final EntityManager entityManager;

	public FamilyGroupAuditLogRepository(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public FamilyGroupAuditLog append(FamilyGroupAuditLog auditLog) {
		entityManager.persist(auditLog);
		return auditLog;
	}

	public List<FamilyGroupAuditLog> findAllByGroupId(long groupId) {
		return entityManager.createQuery("""
			SELECT auditLog
			FROM FamilyGroupAuditLog auditLog
			WHERE auditLog.familyGroup.id = :groupId
			ORDER BY auditLog.createdAt, auditLog.id
			""", FamilyGroupAuditLog.class)
			.setParameter("groupId", groupId)
			.getResultList();
	}

	public Page<FamilyGroupAuditLog> findPageByGroupId(long groupId, Pageable pageable) {
		final List<FamilyGroupAuditLog> content = entityManager.createQuery("""
			SELECT auditLog
			FROM FamilyGroupAuditLog auditLog
			LEFT JOIN FETCH auditLog.member
			WHERE auditLog.familyGroup.id = :groupId
			ORDER BY auditLog.createdAt DESC, auditLog.id DESC
			""", FamilyGroupAuditLog.class)
			.setParameter("groupId", groupId)
			.setFirstResult((int)pageable.getOffset())
			.setMaxResults(pageable.getPageSize())
			.getResultList();
		final long total = entityManager.createQuery("""
			SELECT COUNT(auditLog)
			FROM FamilyGroupAuditLog auditLog
			WHERE auditLog.familyGroup.id = :groupId
			""", Long.class)
			.setParameter("groupId", groupId)
			.getSingleResult();
		return new PageImpl<>(content, pageable, total);
	}
}
