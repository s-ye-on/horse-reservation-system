package com.horse.coupons.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.horse.TestcontainersConfiguration;
import com.horse.coupons.domain.CouponActorType;
import com.horse.families.application.FamilyGroupCommandService;
import com.horse.families.infrastructure.FamilyMembershipRepository;
import com.horse.members.domain.RidingClass;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FamilyCouponCrossConcurrencyIntegrationTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 12);
	private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 8, 10, 10, 0);

	@Autowired
	CouponSelectionService selectionService;

	@Autowired
	CouponHoldService holdService;

	@Autowired
	CouponExpiryService expiryService;

	@Autowired
	FamilyGroupCommandService familyGroupCommandService;

	@Autowired
	FamilyMembershipRepository membershipRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	PlatformTransactionManager transactionManager;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 데이터베이스를_초기화한다() {
		given(clock.instant()).willReturn(Instant.parse("2026-08-14T00:00:00Z"));
		given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
		deleteTestData();
	}

	@AfterEach
	void 데이터베이스를_정리한다() {
		deleteTestData();
	}

	@Test
	void 가족의_마지막_Coupon을_동시에_예약하면_한_요청만_hold한다() throws Exception {
		final long ownerId = insertMember("m32-cross-owner");
		final long firstMemberId = insertMember("m32-cross-requester-1");
		final long secondMemberId = insertMember("m32-cross-requester-2");
		final long groupId = insertGroup();
		insertMembership(groupId, ownerId);
		insertMembership(groupId, firstMemberId);
		insertMembership(groupId, secondMemberId);
		final long couponId = insertCoupon(ownerId, 1, 0, null);
		final long firstReservationId = insertReservation(firstMemberId, couponId, "09:00:00");
		final long secondReservationId = insertReservation(secondMemberId, couponId, "10:00:00");
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		final ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			final List<Future<Boolean>> futures = List.of(
				executor.submit(() -> selectAndHold(ready, start, firstMemberId, firstReservationId)),
				executor.submit(() -> selectAndHold(ready, start, secondMemberId, secondReservationId)));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(futures.stream().map(this::getResult).filter(Boolean::booleanValue))
				.hasSize(1);
			assertThat(couponState(couponId)).isEqualTo("active:1:1");
			assertThat(queryCount("""
				SELECT COUNT(*) FROM coupon_usage_logs
				WHERE coupon_id = ? AND action = 'held'
				""", couponId)).isOne();
			assertThat(jdbcTemplate.queryForObject("""
				SELECT CONCAT(coupon_owner_member_id, ':', family_group_id)
				FROM coupon_usage_logs
				WHERE coupon_id = ? AND action = 'held'
				""", String.class, couponId)).isEqualTo(ownerId + ":" + groupId);
		}
		finally {
			shutdown(executor);
		}
	}

	@Test
	void 가족_Coupon_선택이_먼저면_구성원_제거는_hold_커밋까지_대기한다() throws Exception {
		final FamilyData data = createFamilyData("reservation-first", 1, null);
		final CountDownLatch selected = new CountDownLatch(1);
		final CountDownLatch releaseSelection = new CountDownLatch(1);
		final CountDownLatch removalStarted = new CountDownLatch(1);
		final ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			final Future<Boolean> reservation = executor.submit(() -> transaction().execute(status -> {
				final CouponSelectionResult selection = selectionService.selectForUpdate(
					data.reservationMemberId(),
					RidingClass.FIRST_RIDE,
					LESSON_DATE).orElseThrow();
				selected.countDown();
				await(releaseSelection);
				hold(selection, data.reservationId(), data.reservationMemberId());
				return true;
			}));
			assertThat(selected.await(5, TimeUnit.SECONDS)).isTrue();

			final Future<?> removal = executor.submit(() -> {
				removalStarted.countDown();
				return familyGroupCommandService.removeMember(
					data.groupId(),
					data.reservationMemberId(),
					"m32-cross-admin",
					"예약 경쟁 구성원 제거");
			});
			assertThat(removalStarted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> removal.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);

			releaseSelection.countDown();
			assertThat(reservation.get(5, TimeUnit.SECONDS)).isTrue();
			removal.get(5, TimeUnit.SECONDS);

			assertThat(activeMembershipCount(data.reservationMemberId())).isZero();
			assertThat(couponState(data.couponId())).isEqualTo("active:1:1");
			assertFamilySnapshot(data, "held");
		}
		finally {
			releaseSelection.countDown();
			shutdown(executor);
		}
	}

	@Test
	void 구성원_제거가_먼저면_대기한_신규_예약은_가족_Coupon을_선택하지_않는다() throws Exception {
		final FamilyData data = createFamilyData("removal-first", 1, null);
		final CountDownLatch removed = new CountDownLatch(1);
		final CountDownLatch commitRemoval = new CountDownLatch(1);
		final CountDownLatch reservationStarted = new CountDownLatch(1);
		final ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			final Future<?> removal = executor.submit(() -> transaction().executeWithoutResult(status -> {
				familyGroupCommandService.removeMember(
					data.groupId(),
					data.reservationMemberId(),
					"m32-cross-admin",
					"선행 구성원 제거");
				removed.countDown();
				await(commitRemoval);
			}));
			assertThat(removed.await(5, TimeUnit.SECONDS)).isTrue();

			final Future<Boolean> reservation = executor.submit(() -> {
				reservationStarted.countDown();
				return selectAndHold(
					new CountDownLatch(0),
					data.reservationMemberId(),
					data.reservationId());
			});
			assertThat(reservationStarted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> reservation.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);

			commitRemoval.countDown();
			removal.get(5, TimeUnit.SECONDS);
			assertThat(reservation.get(5, TimeUnit.SECONDS)).isFalse();

			assertThat(activeMembershipCount(data.reservationMemberId())).isZero();
			assertThat(couponState(data.couponId())).isEqualTo("active:1:0");
			assertThat(queryCount("SELECT COUNT(*) FROM coupon_usage_logs")).isZero();
		}
		finally {
			commitRemoval.countDown();
			shutdown(executor);
		}
	}

	@Test
	void 가족_없음을_먼저_관찰한_예약에는_동시_가입을_소급_적용하지_않는다() throws Exception {
		final long ownerId = insertMember("m32-cross-owner-add");
		final long reservationMemberId = insertMember("m32-cross-requester-add");
		final long groupId = insertGroup();
		insertMembership(groupId, ownerId);
		final long couponId = insertCoupon(ownerId, 1, 0, null);
		final long reservationId = insertReservation(
			reservationMemberId,
			couponId,
			"09:00:00");
		final CountDownLatch noFamilyObserved = new CountDownLatch(1);
		final CountDownLatch memberAdded = new CountDownLatch(1);
		final ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			final Future<Boolean> reservation = executor.submit(() -> transaction().execute(status -> {
				assertThat(membershipRepository.findActiveGroupIdByMemberId(reservationMemberId))
					.isEmpty();
				noFamilyObserved.countDown();
				await(memberAdded);
				return selectionService.selectForUpdate(
					reservationMemberId,
					RidingClass.FIRST_RIDE,
					LESSON_DATE).isPresent();
			}));
			final Future<?> addition = executor.submit(() -> {
				assertThat(noFamilyObserved.await(5, TimeUnit.SECONDS)).isTrue();
				final Object result = familyGroupCommandService.addMember(
					groupId,
					reservationMemberId,
					"m32-cross-admin",
					"예약 경쟁 구성원 추가");
				memberAdded.countDown();
				return result;
			});

			addition.get(5, TimeUnit.SECONDS);
			assertThat(reservation.get(5, TimeUnit.SECONDS)).isFalse();
			assertThat(selectionService.selectForUpdate(
				reservationMemberId,
				RidingClass.FIRST_RIDE,
				LESSON_DATE)).isPresent();
			assertThat(couponState(couponId)).isEqualTo("active:1:0");
			assertThat(queryCount("""
				SELECT COUNT(*) FROM coupon_usage_logs
				WHERE reservation_id = ?
				""", reservationId)).isZero();
		}
		finally {
			memberAdded.countDown();
			shutdown(executor);
		}
	}

	@Test
	void 그룹_해제가_먼저면_대기한_신규_예약은_가족_Coupon을_선택하지_않는다() throws Exception {
		final FamilyData data = createFamilyData("dissolution-first", 1, null);
		final CountDownLatch dissolved = new CountDownLatch(1);
		final CountDownLatch commitDissolution = new CountDownLatch(1);
		final CountDownLatch reservationStarted = new CountDownLatch(1);
		final ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			final Future<?> dissolution = executor.submit(() -> transaction().executeWithoutResult(status -> {
				familyGroupCommandService.dissolve(
					data.groupId(),
					"m32-cross-admin",
					"선행 가족 해제");
				dissolved.countDown();
				await(commitDissolution);
			}));
			assertThat(dissolved.await(5, TimeUnit.SECONDS)).isTrue();

			final Future<Boolean> reservation = executor.submit(() -> {
				reservationStarted.countDown();
				return selectAndHold(
					new CountDownLatch(0),
					data.reservationMemberId(),
					data.reservationId());
			});
			assertThat(reservationStarted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> reservation.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);

			commitDissolution.countDown();
			dissolution.get(5, TimeUnit.SECONDS);
			assertThat(reservation.get(5, TimeUnit.SECONDS)).isFalse();
			assertThat(activeMembershipCount(data.reservationMemberId())).isZero();
			assertThat(jdbcTemplate.queryForObject("""
				SELECT status FROM family_groups WHERE id = ?
				""", String.class, data.groupId())).isEqualTo("DISSOLVED");
			assertThat(couponState(data.couponId())).isEqualTo("active:1:0");
		}
		finally {
			commitDissolution.countDown();
			shutdown(executor);
		}
	}

	@Test
	void 기존_hold_반환과_구성원_제거가_겹쳐도_원_Coupon과_snapshot을_유지한다() throws Exception {
		final FamilyData data = createFamilyData("release-remove", 2, null);
		assertThat(selectAndHold(
			new CountDownLatch(0),
			data.reservationMemberId(),
			data.reservationId())).isTrue();
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		final ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			final Future<Boolean> release = executor.submit(() -> {
				ready.countDown();
				start.await();
				return holdService.release(
					data.reservationId(),
					OCCURRED_AT.plusMinutes(1),
					CouponActorType.MEMBER);
			});
			final Future<?> removal = executor.submit(() -> {
				ready.countDown();
				start.await();
				return familyGroupCommandService.removeMember(
					data.groupId(),
					data.reservationMemberId(),
					"m32-cross-admin",
					"반환 경쟁 구성원 제거");
			});
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(release.get(5, TimeUnit.SECONDS)).isTrue();
			removal.get(5, TimeUnit.SECONDS);
			assertThat(activeMembershipCount(data.reservationMemberId())).isZero();
			assertThat(couponState(data.couponId())).isEqualTo("active:2:0");
			assertFamilySnapshot(data, "held");
			assertFamilySnapshot(data, "released");
		}
		finally {
			shutdown(executor);
		}
	}

	@Test
	void 가족_Coupon_반환과_만료가_겹쳐도_횟수와_이력이_한_번씩_반영된다() throws Exception {
		final FamilyData data = createFamilyData(
			"release-expiry",
			2,
			LocalDateTime.of(2026, 8, 13, 9, 0));
		assertThat(selectAndHold(
			new CountDownLatch(0),
			data.reservationMemberId(),
			data.reservationId())).isTrue();
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		final ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			final Future<Boolean> release = executor.submit(() -> {
				ready.countDown();
				start.await();
				return holdService.release(
					data.reservationId(),
					OCCURRED_AT.plusDays(4),
					CouponActorType.MEMBER);
			});
			final Future<CouponExpiryResult> expiry = executor.submit(() -> {
				ready.countDown();
				start.await();
				return expiryService.expireDueCoupons();
			});
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(release.get(5, TimeUnit.SECONDS)).isTrue();
			final CouponExpiryResult expiryResult = expiry.get(5, TimeUnit.SECONDS);
			assertThat(expiryResult.expiredCouponCount()).isOne();
			assertThat(expiryResult.expiredAvailableCount()).isBetween(1, 2);
			assertThat(couponState(data.couponId())).isEqualTo("expired:0:0");
			assertThat(queryCount("""
				SELECT COUNT(*) FROM coupon_usage_logs
				WHERE coupon_id = ? AND action = 'released'
				""", data.couponId())).isOne();
			assertThat(queryCount("""
				SELECT COUNT(*) FROM coupon_usage_logs
				WHERE coupon_id = ? AND action = 'expired'
				""", data.couponId())).isOne();
			assertFamilySnapshot(data, "released");
		}
		finally {
			shutdown(executor);
		}
	}

	private boolean selectAndHold(
		CountDownLatch start,
		long reservationMemberId,
		long reservationId
	) throws InterruptedException {
		start.await();
		return Boolean.TRUE.equals(transaction().execute(status -> selectionService.selectForUpdate(
			reservationMemberId,
			RidingClass.FIRST_RIDE,
			LESSON_DATE).map(selection -> {
				hold(selection, reservationId, reservationMemberId);
				return true;
			}).orElse(false)));
	}

	private boolean selectAndHold(
		CountDownLatch ready,
		CountDownLatch start,
		long reservationMemberId,
		long reservationId
	) throws InterruptedException {
		ready.countDown();
		return selectAndHold(start, reservationMemberId, reservationId);
	}

	private void hold(CouponSelectionResult selection, long reservationId, long memberId) {
		holdService.hold(
			selection.couponId(),
			reservationId,
			memberId,
			selection.couponOwnerMemberId(),
			selection.familyGroupId(),
			LESSON_DATE,
			OCCURRED_AT,
			CouponActorType.MEMBER);
	}

	private FamilyData createFamilyData(String suffix, int remainingCount, LocalDateTime expiresAt) {
		final long ownerId = insertMember("m32-cross-owner-" + suffix);
		final long reservationMemberId = insertMember("m32-cross-requester-" + suffix);
		final long groupId = insertGroup();
		insertMembership(groupId, ownerId);
		insertMembership(groupId, reservationMemberId);
		final long couponId = insertCoupon(ownerId, remainingCount, 0, expiresAt);
		final long reservationId = insertReservation(
			reservationMemberId,
			couponId,
			"09:00:00");
		return new FamilyData(groupId, ownerId, reservationMemberId, couponId, reservationId);
	}

	private long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES (?, '교차 동시성 회원', '010-0000-0000')
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long insertGroup() {
		jdbcTemplate.update("INSERT INTO family_groups (name) VALUES ('교차 동시성 가족')");
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertMembership(long groupId, long memberId) {
		jdbcTemplate.update("""
			INSERT INTO family_memberships (family_group_id, member_id, joined_at)
			VALUES (?, ?, ?)
			""", groupId, memberId, OCCURRED_AT);
	}

	private long insertCoupon(
		long ownerId,
		int remainingCount,
		int heldCount,
		LocalDateTime expiresAt
	) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count,
				first_used_at, expires_at, status, created_by
			) VALUES (?, 'general', 10, ?, ?, ?, ?, 'active', 'm32-cross-admin')
			""",
			ownerId,
			remainingCount,
			heldCount,
			expiresAt == null ? null : expiresAt.minusMonths(3),
			expiresAt);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long insertReservation(long memberId, long couponId, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, ?, 'pending_admin_approval', 'coupon', ?, ?)
			""", memberId, LESSON_DATE, startTime, couponId, OCCURRED_AT);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private int activeMembershipCount(long memberId) {
		return queryCount("""
			SELECT COUNT(*) FROM family_memberships
			WHERE member_id = ? AND ended_at IS NULL
			""", memberId);
	}

	private String couponState(long couponId) {
		return jdbcTemplate.queryForObject("""
			SELECT CONCAT(status, ':', remaining_count, ':', held_count)
			FROM coupons
			WHERE id = ?
			""", String.class, couponId);
	}

	private void assertFamilySnapshot(FamilyData data, String action) {
		assertThat(jdbcTemplate.queryForObject("""
			SELECT CONCAT(member_id, ':', coupon_owner_member_id, ':', family_group_id)
			FROM coupon_usage_logs
			WHERE reservation_id = ? AND action = ?
			""", String.class, data.reservationId(), action))
			.isEqualTo(data.reservationMemberId() + ":" + data.ownerId() + ":" + data.groupId());
	}

	private int queryCount(String sql, Object... arguments) {
		return jdbcTemplate.queryForObject(sql, Integer.class, arguments);
	}

	private TransactionTemplate transaction() {
		return new TransactionTemplate(transactionManager);
	}

	private boolean getResult(Future<Boolean> future) {
		try {
			return future.get(10, TimeUnit.SECONDS);
		}
		catch (Exception exception) {
			throw new AssertionError("가족 Coupon 경쟁 결과를 확인할 수 없습니다.", exception);
		}
	}

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new AssertionError("교차 동시성 신호가 도착하지 않았습니다.");
			}
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError("교차 동시성 검증이 중단되었습니다.", exception);
		}
	}

	private void shutdown(ExecutorService executor) throws InterruptedException {
		executor.shutdownNow();
		assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
	}

	private void deleteTestData() {
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM family_group_audit_logs");
		jdbcTemplate.update("DELETE FROM family_memberships");
		jdbcTemplate.update("DELETE FROM family_groups");
		jdbcTemplate.update("DELETE FROM members WHERE auth_subject LIKE 'm32-cross-%'");
	}

	private record FamilyData(
		long groupId,
		long ownerId,
		long reservationMemberId,
		long couponId,
		long reservationId
	) {
	}
}
