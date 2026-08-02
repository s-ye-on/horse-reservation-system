package com.horse.auth.application;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.horse.auth.UserRole;
import com.horse.auth.domain.AuthAccount;
import com.horse.auth.infrastructure.AuthAccountRepository;

@Component
public class InitialAdminBootstrapTransaction {

	private final AuthAccountRepository authAccountRepository;

	public InitialAdminBootstrapTransaction(AuthAccountRepository authAccountRepository) {
		this.authAccountRepository = authAccountRepository;
	}

	@Transactional
	public boolean createIfAbsent(String normalizedEmail, String passwordHash) {
		if (authAccountRepository.existsByRole(UserRole.ADMIN)) {
			return false;
		}
		authAccountRepository.saveAndFlush(AuthAccount.createInitialAdmin(
			UUID.randomUUID().toString(),
			normalizedEmail,
			passwordHash
		));
		return true;
	}
}
