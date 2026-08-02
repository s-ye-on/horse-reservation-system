package com.horse.auth.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.horse.auth.UserRole;
import com.horse.auth.domain.AuthAccount;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {

	Optional<AuthAccount> findByNormalizedEmail(String normalizedEmail);

	Optional<AuthAccount> findByAuthSubject(String authSubject);

	boolean existsByNormalizedEmail(String normalizedEmail);

	boolean existsByRole(UserRole role);
}
