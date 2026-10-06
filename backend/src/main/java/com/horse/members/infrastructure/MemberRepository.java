package com.horse.members.infrastructure;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.horse.members.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByAuthSubject(String authSubject);

	@Query("""
		SELECT member FROM Member member
		WHERE LOCATE(LOWER(:query), LOWER(member.name)) > 0
			OR (:phoneQuery <> ''
				AND LOCATE(:phoneQuery, REPLACE(REPLACE(member.phone, '-', ''), ' ', '')) > 0)
		""")
	Page<Member> searchByNameOrPhone(
		@Param("query") String query,
		@Param("phoneQuery") String phoneQuery,
		Pageable pageable
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT member FROM Member member WHERE member.id = :memberId")
	Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

}
