package com.horse.coupons.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;
import com.horse.members.domain.RidingClass;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CouponSelectionPolicyTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 10);

	@Autowired
	CouponSelectionService service;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 예약_클래스와_같은_종류의_쿠폰만_선택한다() {
		final Long memberId = insertMember("selection-type-member");
		final LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 9, 0);
		final Long generalId = insertCoupon(memberId, "general", 10, 0, "active", null, null, createdAt);
		final Long dressageId = insertCoupon(memberId, "dressage", 10, 0, "active", null, null, createdAt);
		final Long jumpingId = insertCoupon(memberId, "jumping", 10, 0, "active", null, null, createdAt);

		assertThat(selectId(memberId, RidingClass.FIRST_RIDE)).isEqualTo(generalId);
		assertThat(selectId(memberId, RidingClass.ROUND_BEGINNER)).isEqualTo(generalId);
		assertThat(selectId(memberId, RidingClass.ROUND_TROT)).isEqualTo(generalId);
		assertThat(selectId(memberId, RidingClass.LARGE_ARENA_BEGINNER)).isEqualTo(generalId);
		assertThat(selectId(memberId, RidingClass.LARGE_ARENA_TROT)).isEqualTo(generalId);
		assertThat(selectId(memberId, RidingClass.DRESSAGE)).isEqualTo(dressageId);
		assertThat(selectId(memberId, RidingClass.JUMPING)).isEqualTo(jumpingId);
	}

	@Test
	void 사용_중인_쿠폰을_미사용_쿠폰보다_먼저_선택한다() {
		final Long memberId = insertMember("selection-used-member");
		final Long unusedId = insertCoupon(
			memberId, "general", 10, 0, "active", null, null, LocalDateTime.of(2026, 6, 1, 9, 0));
		final Long activeId = insertCoupon(
			memberId,
			"general",
			8,
			0,
			"active",
			LocalDateTime.of(2026, 5, 1, 9, 0),
			LocalDateTime.of(2026, 8, 20, 9, 0),
			LocalDateTime.of(2026, 7, 1, 9, 0));

		assertThat(unusedId).isNotEqualTo(activeId);
		assertThat(selectId(memberId, RidingClass.FIRST_RIDE)).isEqualTo(activeId);
	}

	@Test
	void 만료일이_빠른_쿠폰을_먼저_선택한다() {
		final Long memberId = insertMember("selection-expiry-member");
		final LocalDateTime firstUsedAt = LocalDateTime.of(2026, 5, 1, 9, 0);
		final Long laterId = insertCoupon(
			memberId,
			"general",
			10,
			0,
			"active",
			firstUsedAt,
			LocalDateTime.of(2026, 9, 1, 9, 0),
			LocalDateTime.of(2026, 6, 1, 9, 0));
		final Long earlierId = insertCoupon(
			memberId,
			"general",
			10,
			0,
			"active",
			firstUsedAt,
			LocalDateTime.of(2026, 8, 20, 9, 0),
			LocalDateTime.of(2026, 7, 1, 9, 0));

		assertThat(laterId).isNotEqualTo(earlierId);
		assertThat(selectId(memberId, RidingClass.FIRST_RIDE)).isEqualTo(earlierId);
	}

	@Test
	void 만료일이_같거나_없으면_FIFO와_ID_순으로_선택한다() {
		final Long memberId = insertMember("selection-tie-member");
		final LocalDateTime sameCreatedAt = LocalDateTime.of(2026, 7, 1, 9, 0);
		final Long firstId = insertCoupon(memberId, "general", 10, 0, "active", null, null, sameCreatedAt);
		final Long secondId = insertCoupon(memberId, "general", 10, 0, "active", null, null, sameCreatedAt);
		insertCoupon(
			memberId,
			"general",
			10,
			0,
			"active",
			null,
			null,
			LocalDateTime.of(2026, 7, 2, 9, 0));

		assertThat(firstId).isLessThan(secondId);
		assertThat(selectId(memberId, RidingClass.FIRST_RIDE)).isEqualTo(firstId);
	}

	@Test
	void 같은_만료일이면_먼저_등록한_쿠폰을_선택한다() {
		final Long memberId = insertMember("selection-same-expiry-member");
		final LocalDateTime firstUsedAt = LocalDateTime.of(2026, 5, 1, 9, 0);
		final LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 20, 9, 0);
		final Long laterId = insertCoupon(
			memberId,
			"general",
			10,
			0,
			"active",
			firstUsedAt,
			expiresAt,
			LocalDateTime.of(2026, 7, 2, 9, 0));
		final Long earlierId = insertCoupon(
			memberId,
			"general",
			10,
			0,
			"active",
			firstUsedAt,
			expiresAt,
			LocalDateTime.of(2026, 7, 1, 9, 0));

		assertThat(laterId).isNotEqualTo(earlierId);
		assertThat(selectId(memberId, RidingClass.FIRST_RIDE)).isEqualTo(earlierId);
	}

	@Test
	void 사용할_수_없는_쿠폰은_후보에서_제외한다() {
		final Long memberId = insertMember("selection-filter-member");
		final LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 9, 0);
		insertCoupon(memberId, "general", 10, 0, "expired", null, null, createdAt);
		insertCoupon(memberId, "general", 0, 0, "depleted", null, null, createdAt);
		insertCoupon(memberId, "general", 1, 1, "active", null, null, createdAt);
		insertCoupon(
			memberId,
			"general",
			10,
			0,
			"active",
			LocalDateTime.of(2026, 4, 1, 9, 0),
			LocalDateTime.of(2026, 8, 9, 9, 0),
			createdAt);
		final Long availableId = insertCoupon(
			memberId, "general", 2, 1, "active", null, null, createdAt.plusDays(1));

		assertThat(selectId(memberId, RidingClass.FIRST_RIDE)).isEqualTo(availableId);
	}

	@Test
	void 선택_조회는_쿠폰_상태를_변경하지_않는다() {
		final Long memberId = insertMember("selection-read-only-member");
		insertCoupon(
			memberId, "general", 7, 2, "active", null, null, LocalDateTime.of(2026, 7, 1, 9, 0));
		final List<CouponSnapshot> before = snapshots(memberId);

		service.select(memberId, RidingClass.FIRST_RIDE, LESSON_DATE);

		assertThat(snapshots(memberId)).isEqualTo(before);
	}

	private Long selectId(Long memberId, RidingClass ridingClass) {
		return service.select(memberId, ridingClass, LESSON_DATE)
			.orElseThrow()
			.couponId();
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '테스트 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM members WHERE auth_subject = ?", Long.class, authSubject);
	}

	private Long insertCoupon(
		Long memberId,
		String type,
		int remainingCount,
		int heldCount,
		String status,
		LocalDateTime firstUsedAt,
		LocalDateTime expiresAt,
		LocalDateTime createdAt
	) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count,
				first_used_at, expires_at, status, created_by, created_at, updated_at
			) VALUES (?, ?, 10, ?, ?, ?, ?, ?, 'selection-admin', ?, ?)
			""",
			memberId,
			type,
			remainingCount,
			heldCount,
			firstUsedAt,
			expiresAt,
			status,
			createdAt,
			createdAt);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private List<CouponSnapshot> snapshots(Long memberId) {
		return jdbcTemplate.query("""
			SELECT id, remaining_count, held_count, status, updated_at
			FROM coupons
			WHERE member_id = ?
			ORDER BY id
			""", (resultSet, rowNumber) -> new CouponSnapshot(
			resultSet.getLong("id"),
			resultSet.getInt("remaining_count"),
			resultSet.getInt("held_count"),
			resultSet.getString("status"),
			resultSet.getObject("updated_at", LocalDateTime.class)), memberId);
	}

	private record CouponSnapshot(
		Long id,
		int remainingCount,
		int heldCount,
		String status,
		LocalDateTime updatedAt
	) {
	}
}
