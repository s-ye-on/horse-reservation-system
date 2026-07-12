package com.horse.members.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.horse.members.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByAuthSubject(String authSubject);

}
