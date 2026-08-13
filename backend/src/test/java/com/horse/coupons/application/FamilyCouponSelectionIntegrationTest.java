package com.horse.coupons.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;
import com.horse.coupons.domain.CouponActorType;
import com.horse.members.domain.RidingClass;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class FamilyCouponSelectionIntegrationTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 20);
	private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 8, 14, 10, 0);

	@Autowired
	CouponSelectionService selectionService;

	@Autowired
	CouponHoldService holdService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	EntityManager entityManager;

	@Test
	void 활성_가족의_모든_유효_Coupon을_하나의_결정_순서로_선택한다() {
		final long reservationMemberId = insertMember("family-coupon-user");
		final long firstOwnerId = insertMember("family-coupon-owner-1");
		final long secondOwnerId = insertMember("family-coupon-owner-2");
		final long groupId = insertGroup("Coupon 공유 가족");
		insertMembership(groupId, reservationMemberId);
		insertMembership(groupId, firstOwnerId);
		insertMembership(groupId, secondOwnerId);
		insertCoupon(
			reservationMemberId,
			"general",
			10,
			0,
			"active",
			null,
			LocalDateTime.of(2026, 7, 1, 9, 0));
		final long laterExpiryId = insertCoupon(
			firstOwnerId,
			"general",
			10,
			0,
			"active",
			LocalDateTime.of(2026, 9, 10, 9, 0),
			LocalDateTime.of(2026, 7, 1, 9, 0));
		final long earliestExpiryId = insertCoupon(
			secondOwnerId,
			"general",
			10,
			0,
			"active",
			LocalDateTime.of(2026, 9, 1, 9, 0),
			LocalDateTime.of(2026, 7, 2, 9, 0));

		final CouponSelectionResult selected = selectionService.select(
			reservationMemberId,
			RidingClass.FIRST_RIDE,
			LESSON_DATE).orElseThrow();

		assertThat(earliestExpiryId).isNotEqualTo(laterExpiryId);
		assertThat(selected.couponId()).isEqualTo(earliestExpiryId);
		assertThat(selected.couponOwnerMemberId()).isEqualTo(secondOwnerId);
		assertThat(selected.familyGroupId()).isEqualTo(groupId);
	}

	@Test
	void 만료일_생성일_ID_NULL_순서와_후보_필터를_가족_전체에_적용한다() {
		final long reservationMemberId = insertMember("family-order-user");
		final long ownerId = insertMember("family-order-owner");
		final long groupId = insertGroup("정렬 가족");
		insertMembership(groupId, reservationMemberId);
		insertMembership(groupId, ownerId);
		final LocalDateTime sameCreatedAt = LocalDateTime.of(2026, 7, 1, 9, 0);
		insertCoupon(ownerId, "general", 10, 0, "active", null, sameCreatedAt);
		insertCoupon(ownerId, "general", 10, 0, "expired", null, sameCreatedAt);
		insertCoupon(ownerId, "general", 1, 1, "active", null, sameCreatedAt);
		insertCoupon(
			ownerId,
			"general",
			10,
			0,
			"active",
			LocalDateTime.of(2026, 8, 19, 9, 0),
			sameCreatedAt);
		insertCoupon(ownerId, "dressage", 10, 0, "active", null, sameCreatedAt);
		final long firstId = insertCoupon(
			ownerId,
			"general",
			10,
			0,
			"active",
			LocalDateTime.of(2026, 9, 1, 9, 0),
			sameCreatedAt);
		final long secondId = insertCoupon(
			ownerId,
			"general",
			10,
			0,
			"active",
			LocalDateTime.of(2026, 9, 1, 9, 0),
			sameCreatedAt);

		assertThat(firstId).isLessThan(secondId);
		assertThat(selectionService.select(
			reservationMemberId,
			RidingClass.FIRST_RIDE,
			LESSON_DATE).orElseThrow().couponId()).isEqualTo(firstId);
	}

	@Test
	void 종료되거나_해제된_가족은_새_Coupon_후보를_제공하지_않는다() {
		final long reservationMemberId = insertMember("family-ended-user");
		final long ownerId = insertMember("family-ended-owner");
		final long groupId = insertGroup("종료 가족");
		insertMembership(groupId, reservationMemberId);
		insertMembership(groupId, ownerId);
		insertCoupon(
			ownerId,
			"general",
			10,
			0,
			"active",
			null,
			LocalDateTime.of(2026, 7, 1, 9, 0));

		jdbcTemplate.update("""
			UPDATE family_memberships
			SET ended_at = '2026-08-14 10:00:00'
			WHERE family_group_id = ? AND member_id = ?
			""", groupId, reservationMemberId);

		assertThat(selectionService.select(
			reservationMemberId,
			RidingClass.FIRST_RIDE,
			LESSON_DATE)).isEmpty();

		jdbcTemplate.update("""
			UPDATE family_groups
			SET status = 'DISSOLVED', dissolved_at = '2026-08-14 10:00:00'
			WHERE id = ?
			""", groupId);
		assertThat(selectionService.select(
			ownerId,
			RidingClass.FIRST_RIDE,
			LESSON_DATE)).isPresent();
	}

	@Test
	void 가족_가입_전에_발급된_Coupon도_가입_후_후보가_된다() {
		final long reservationMemberId = insertMember("family-prior-user");
		final long ownerId = insertMember("family-prior-owner");
		final long couponId = insertCoupon(
			ownerId,
			"general",
			10,
			0,
			"active",
			null,
			LocalDateTime.of(2026, 6, 1, 9, 0));
		final long groupId = insertGroup("발급 선행 가족");
		insertMembership(groupId, reservationMemberId);
		insertMembership(groupId, ownerId);

		assertThat(selectionService.select(
			reservationMemberId,
			RidingClass.FIRST_RIDE,
			LESSON_DATE).orElseThrow().couponId()).isEqualTo(couponId);
	}

	@Test
	void 가족_Coupon_hold는_예약자_원소유자_가족을_snapshot하고_관계_종료_후에도_원_Coupon으로_반환한다() {
		final long reservationMemberId = insertMember("family-snapshot-user");
		final long ownerId = insertMember("family-snapshot-owner");
		final long groupId = insertGroup("Snapshot 가족");
		insertMembership(groupId, reservationMemberId);
		insertMembership(groupId, ownerId);
		final long couponId = insertCoupon(
			ownerId,
			"general",
			10,
			0,
			"active",
			null,
			LocalDateTime.of(2026, 7, 1, 9, 0));
		final CouponSelectionResult selection = selectionService.selectForUpdate(
			reservationMemberId,
			RidingClass.FIRST_RIDE,
			LESSON_DATE).orElseThrow();
		final long reservationId = insertReservation(reservationMemberId, couponId);

		holdService.hold(
			selection.couponId(),
			reservationId,
			reservationMemberId,
			selection.couponOwnerMemberId(),
			selection.familyGroupId(),
			LESSON_DATE,
			OCCURRED_AT,
			CouponActorType.MEMBER);
		entityManager.flush();
		jdbcTemplate.update("""
			UPDATE family_memberships
			SET ended_at = '2026-08-14 10:01:00'
			WHERE family_group_id = ? AND member_id = ?
			""", groupId, reservationMemberId);

		assertThat(holdService.release(
			reservationId,
			OCCURRED_AT.plusMinutes(2),
			CouponActorType.MEMBER)).isTrue();
		entityManager.flush();

		assertThat(jdbcTemplate.queryForList("""
			SELECT member_id, coupon_owner_member_id, family_group_id
			FROM coupon_usage_logs
			WHERE reservation_id = ?
			ORDER BY id
			""", reservationId)).allSatisfy(row -> {
				assertThat(((Number) row.get("member_id")).longValue())
					.isEqualTo(reservationMemberId);
				assertThat(((Number) row.get("coupon_owner_member_id")).longValue())
					.isEqualTo(ownerId);
				assertThat(((Number) row.get("family_group_id")).longValue())
					.isEqualTo(groupId);
			});
		assertThat(jdbcTemplate.queryForObject(
			"SELECT member_id FROM coupons WHERE id = ?",
			Long.class,
			couponId)).isEqualTo(ownerId);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT held_count FROM coupons WHERE id = ?",
			Integer.class,
			couponId)).isZero();
	}

	private long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES (?, '가족 Coupon 회원', '010-0000-0000')
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long insertGroup(String name) {
		jdbcTemplate.update("INSERT INTO family_groups (name) VALUES (?)", name);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertMembership(long groupId, long memberId) {
		jdbcTemplate.update("""
			INSERT INTO family_memberships (family_group_id, member_id)
			VALUES (?, ?)
			""", groupId, memberId);
	}

	private long insertCoupon(
		long ownerId,
		String type,
		int remainingCount,
		int heldCount,
		String status,
		LocalDateTime expiresAt,
		LocalDateTime createdAt
	) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count,
				first_used_at, expires_at, status, created_by, created_at, updated_at
			) VALUES (?, ?, 10, ?, ?, ?, ?, ?, 'family-selection-admin', ?, ?)
			""",
			ownerId,
			type,
			remainingCount,
			heldCount,
			expiresAt == null ? null : expiresAt.minusMonths(3),
			expiresAt,
			status,
			createdAt,
			createdAt);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long insertReservation(long reservationMemberId, long couponId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, '09:00:00', 'pending_admin_approval',
				'coupon', ?, ?)
			""", reservationMemberId, LESSON_DATE, couponId, OCCURRED_AT);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}
}
