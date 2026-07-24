package com.horse.schedules.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.horse.TestcontainersConfiguration;
import com.horse.members.domain.Member;
import com.horse.members.infrastructure.MemberRepository;
import com.horse.schedules.domain.ScheduleDate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ScheduleLockingIntegrationTest {

	private static final LocalDate FIRST_DATE = LocalDate.of(2027, 1, 10);
	private static final LocalDate SECOND_DATE = LocalDate.of(2027, 1, 11);

	@Autowired
	ScheduleConfigGuardRepository configGuardRepository;

	@Autowired
	ScheduleDateRepository scheduleDateRepository;

	@Autowired
	ReservationMemberDayGuardRepository memberDayGuardRepository;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	PlatformTransactionManager transactionManager;

	@BeforeEach
	void 일정_잠금_데이터를_초기화한다() {
		jdbcTemplate.update("DELETE FROM reservation_member_day_guards");
		jdbcTemplate.update("DELETE FROM schedule_audit_logs");
		jdbcTemplate.update("DELETE FROM schedule_dates");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM regular_schedule_templates");
		jdbcTemplate.update("DELETE FROM recurring_holiday_rules");
		jdbcTemplate.update("DELETE FROM members WHERE auth_subject LIKE 'r02-lock-%'");
	}

	@Test
	void 설정_Guard의_공유_잠금끼리는_동시에_획득한다() throws Exception {
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			final CountDownLatch acquired = new CountDownLatch(1);
			final CountDownLatch release = new CountDownLatch(1);
			final Future<Void> holder = holdLock(
				executor,
				acquired,
				release,
				() -> configGuardRepository.findSingletonForShare());
			assertThat(acquired.await(2, TimeUnit.SECONDS)).isTrue();

			final Future<Void> compatible = runInTransaction(
				executor,
				() -> configGuardRepository.findSingletonForShare());

			assertThat(compatible.get(2, TimeUnit.SECONDS)).isNull();
			release.countDown();
			assertThat(holder.get(2, TimeUnit.SECONDS)).isNull();
		}
	}

	@Test
	void 설정_Guard의_공유_잠금과_배타_잠금은_직렬화된다() throws Exception {
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			final CountDownLatch acquired = new CountDownLatch(1);
			final CountDownLatch release = new CountDownLatch(1);
			final Future<Void> holder = holdLock(
				executor,
				acquired,
				release,
				() -> configGuardRepository.findSingletonForShare());
			assertThat(acquired.await(2, TimeUnit.SECONDS)).isTrue();

			final Future<Void> contender = runInTransaction(
				executor,
				() -> configGuardRepository.findSingletonForUpdate());

			assertBlocked(contender);
			release.countDown();
			assertThat(holder.get(2, TimeUnit.SECONDS)).isNull();
			assertThat(contender.get(2, TimeUnit.SECONDS)).isNull();
		}
	}

	@Test
	void 동일한_운영_날짜의_배타_잠금은_직렬화된다() throws Exception {
		createScheduleDates();

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			final CountDownLatch acquired = new CountDownLatch(1);
			final CountDownLatch release = new CountDownLatch(1);
			final Future<Void> holder = holdLock(
				executor,
				acquired,
				release,
				() -> scheduleDateRepository.findByScheduleDateForUpdate(FIRST_DATE).orElseThrow());
			assertThat(acquired.await(2, TimeUnit.SECONDS)).isTrue();

			final Future<Void> contender = runInTransaction(
				executor,
				() -> scheduleDateRepository.findByScheduleDateForUpdate(FIRST_DATE).orElseThrow());

			assertBlocked(contender);
			release.countDown();
			assertThat(holder.get(2, TimeUnit.SECONDS)).isNull();
			assertThat(contender.get(2, TimeUnit.SECONDS)).isNull();
		}
	}

	@Test
	void 서로_다른_운영_날짜의_잠금은_직렬화하지_않는다() throws Exception {
		createScheduleDates();

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			final CountDownLatch acquired = new CountDownLatch(1);
			final CountDownLatch release = new CountDownLatch(1);
			final Future<Void> holder = holdLock(
				executor,
				acquired,
				release,
				() -> scheduleDateRepository.findByScheduleDateForUpdate(FIRST_DATE).orElseThrow());
			assertThat(acquired.await(2, TimeUnit.SECONDS)).isTrue();

			final Future<Void> independent = runInTransaction(
				executor,
				() -> scheduleDateRepository.findByScheduleDateForUpdate(SECOND_DATE).orElseThrow());

			assertThat(independent.get(2, TimeUnit.SECONDS)).isNull();
			release.countDown();
			assertThat(holder.get(2, TimeUnit.SECONDS)).isNull();
		}
	}

	@Test
	void 겹치는_운영_날짜_범위_잠금은_직렬화된다() throws Exception {
		createScheduleDates();

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			final CountDownLatch acquired = new CountDownLatch(1);
			final CountDownLatch release = new CountDownLatch(1);
			final Future<Void> holder = holdLock(
				executor,
				acquired,
				release,
				() -> scheduleDateRepository.findAllByScheduleDateBetweenForUpdate(
					FIRST_DATE,
					SECOND_DATE));
			assertThat(acquired.await(2, TimeUnit.SECONDS)).isTrue();

			final Future<Void> contender = runInTransaction(
				executor,
				() -> scheduleDateRepository.findByScheduleDateForUpdate(FIRST_DATE).orElseThrow());

			assertBlocked(contender);
			release.countDown();
			assertThat(holder.get(2, TimeUnit.SECONDS)).isNull();
			assertThat(contender.get(2, TimeUnit.SECONDS)).isNull();
		}
	}

	@Test
	void 동일한_회원과_날짜_Guard의_upsert와_배타_잠금은_직렬화된다() throws Exception {
		final Long memberId = createMember("r02-lock-same");

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			final CountDownLatch acquired = new CountDownLatch(1);
			final CountDownLatch release = new CountDownLatch(1);
			final Future<Void> holder = holdLock(
				executor,
				acquired,
				release,
				() -> memberDayGuardRepository.acquire(memberId, FIRST_DATE));
			assertThat(acquired.await(2, TimeUnit.SECONDS)).isTrue();

			final Future<Void> contender = runInTransaction(
				executor,
				() -> memberDayGuardRepository.acquire(memberId, FIRST_DATE));

			assertBlocked(contender);
			release.countDown();
			assertThat(holder.get(2, TimeUnit.SECONDS)).isNull();
			assertThat(contender.get(2, TimeUnit.SECONDS)).isNull();
			assertThat(jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM reservation_member_day_guards
				WHERE member_id = ? AND lesson_date = ?
				""", Integer.class, memberId, FIRST_DATE)).isEqualTo(1);
		}
	}

	@Test
	void 서로_다른_회원_날짜_Guard는_직렬화하지_않는다() throws Exception {
		final Long firstMemberId = createMember("r02-lock-first");
		final Long secondMemberId = createMember("r02-lock-second");

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			final CountDownLatch acquired = new CountDownLatch(1);
			final CountDownLatch release = new CountDownLatch(1);
			final Future<Void> holder = holdLock(
				executor,
				acquired,
				release,
				() -> memberDayGuardRepository.acquire(firstMemberId, FIRST_DATE));
			assertThat(acquired.await(2, TimeUnit.SECONDS)).isTrue();

			final Future<Void> independent = runInTransaction(
				executor,
				() -> memberDayGuardRepository.acquire(secondMemberId, FIRST_DATE));

			assertThat(independent.get(2, TimeUnit.SECONDS)).isNull();
			release.countDown();
			assertThat(holder.get(2, TimeUnit.SECONDS)).isNull();
		}
	}

	@Test
	void 잠금_Repository는_기존_트랜잭션을_필수로_요구한다() {
		final Long memberId = createMember("r02-lock-mandatory");

		assertThatThrownBy(() -> configGuardRepository.findSingletonForShare())
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThatThrownBy(() -> scheduleDateRepository.findByScheduleDateForUpdate(FIRST_DATE))
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThatThrownBy(() -> scheduleDateRepository.findAllByScheduleDateBetweenForUpdate(
			FIRST_DATE,
			SECOND_DATE))
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThatThrownBy(() -> memberDayGuardRepository.acquire(memberId, FIRST_DATE))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	private void createScheduleDates() {
		scheduleDateRepository.saveAndFlush(ScheduleDate.create(FIRST_DATE, 1L));
		scheduleDateRepository.saveAndFlush(ScheduleDate.create(SECOND_DATE, 1L));
	}

	private Long createMember(String authSubject) {
		return memberRepository.saveAndFlush(
			Member.create(authSubject, "잠금 회원", "010-0000-0000")).getId();
	}

	private Future<Void> holdLock(
		ExecutorService executor,
		CountDownLatch acquired,
		CountDownLatch release,
		Runnable acquire
	) {
		return executor.submit(() -> {
			transactionTemplate().executeWithoutResult(status -> {
				acquire.run();
				acquired.countDown();
				await(release);
			});
			return null;
		});
	}

	private Future<Void> runInTransaction(ExecutorService executor, Runnable action) {
		return executor.submit(() -> {
			transactionTemplate().executeWithoutResult(status -> action.run());
			return null;
		});
	}

	private TransactionTemplate transactionTemplate() {
		return new TransactionTemplate(transactionManager);
	}

	private void assertBlocked(Future<Void> contender) {
		assertThatThrownBy(() -> contender.get(300, TimeUnit.MILLISECONDS))
			.isInstanceOf(TimeoutException.class);
	}

	private void await(CountDownLatch latch) {
		try {
			latch.await();
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError("잠금 테스트 대기 중 인터럽트가 발생했습니다.", exception);
		}
	}
}
