package com.horse.members.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

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
		assertThat(found.isLargeArenaAllowed()).isFalse();
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getUpdatedAt()).isNotNull();
	}

	@Test
	void 같은_인증_주체를_중복_저장할_수_없다() {
		memberRepository.saveAndFlush(Member.create("member-duplicate", "홍길동", "010-1111-1111"));

		assertThatThrownBy(() -> memberRepository.saveAndFlush(
			Member.create("member-duplicate", "김승마", "010-2222-2222")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
