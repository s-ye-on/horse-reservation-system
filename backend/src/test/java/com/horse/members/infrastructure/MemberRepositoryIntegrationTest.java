package com.horse.members.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.horse.TestcontainersConfiguration;
import com.horse.members.domain.Member;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class MemberRepositoryIntegrationTest {

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	EntityManager entityManager;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 인증_주체로_회원의_기본_기승_상태를_조회한다() {
		Member member = memberRepository.saveAndFlush(
			Member.create("member-1", "홍길동", "010-1234-5678"));
		entityManager.clear();

		Member found = memberRepository.findByAuthSubject("member-1").orElseThrow();

		assertThat(found.getId()).isEqualTo(member.getId());
		assertThat(found.getName()).isEqualTo("홍길동");
		assertThat(found.getPhone()).isEqualTo("010-1234-5678");
		assertThat(found.getGeneralRideCount()).isZero();
		assertThat(found.getDressageRideCount()).isZero();
		assertThat(found.getJumpingRideCount()).isZero();
		assertThat(found.isDressageApproved()).isFalse();
		assertThat(found.isJumpingApproved()).isFalse();
		assertThat(found.canUseLargeArena()).isFalse();
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getUpdatedAt()).isNotNull();
	}

	@Test
	void 대마장_이용_가능_여부는_일반_기승_횟수로_계산한다() {
		insertMember("member-20", 20, false, false, true);
		insertMember("member-21", 21, false, false, false);
		entityManager.clear();

		Member memberAtTwenty = memberRepository.findByAuthSubject("member-20").orElseThrow();
		Member memberAtTwentyOne = memberRepository.findByAuthSubject("member-21").orElseThrow();

		assertThat(memberAtTwenty.canUseLargeArena()).isFalse();
		assertThat(memberAtTwentyOne.canUseLargeArena()).isTrue();
	}

	@Test
	void 특수_클래스_승인_회원은_일반_기승_횟수와_무관하게_대마장을_이용할_수_있다() {
		insertMember("dressage-member", 0, true, false, false);
		insertMember("jumping-member", 0, false, true, false);
		entityManager.clear();

		Member dressageMember = memberRepository.findByAuthSubject("dressage-member").orElseThrow();
		Member jumpingMember = memberRepository.findByAuthSubject("jumping-member").orElseThrow();

		assertThat(dressageMember.canUseLargeArena()).isTrue();
		assertThat(jumpingMember.canUseLargeArena()).isTrue();
	}

	@Test
	void 같은_인증_주체를_중복_저장할_수_없다() {
		memberRepository.saveAndFlush(Member.create("member-duplicate", "홍길동", "010-1111-1111"));

		assertThatThrownBy(() -> memberRepository.saveAndFlush(
			Member.create("member-duplicate", "김승마", "010-2222-2222")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private void insertMember(
		String authSubject,
		int generalRideCount,
		boolean dressageApproved,
		boolean jumpingApproved,
		boolean storedLargeArenaAllowed
	) {
		jdbcTemplate.update("""
			INSERT INTO members (
				auth_subject,
				name,
				phone,
				general_ride_count,
				dressage_ride_count,
				jumping_ride_count,
				dressage_approved,
				jumping_approved,
				large_arena_allowed,
				progression_management_started_at,
				special_approval_progression_credit
			) VALUES (
				?, '테스트 회원', '010-0000-0000', ?, 0, 0, ?, ?, ?,
				CURRENT_TIMESTAMP(6), ?
			)
			""", authSubject, generalRideCount, dressageApproved, jumpingApproved,
			storedLargeArenaAllowed,
			dressageApproved || jumpingApproved ? Math.max(0, 26 - generalRideCount) : 0);
	}

}
