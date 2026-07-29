package com.horse.members.infrastructure;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import com.horse.members.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByAuthSubject(String authSubject);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT member FROM Member member WHERE member.id = :memberId")
	Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

}
