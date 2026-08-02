package com.horse.auth.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.horse.auth.UserRole;
import com.horse.auth.domain.AuthCredentialPolicy;
import com.horse.auth.domain.exception.AuthException;
import com.horse.auth.infrastructure.AuthAccountRepository;
import com.horse.global.exception.ExceptionCode;

@Service
public class InitialAdminBootstrapService {

	private final PasswordEncoder passwordEncoder;
	private final AuthAccountRepository authAccountRepository;
	private final InitialAdminBootstrapTransaction bootstrapTransaction;

	public InitialAdminBootstrapService(
		PasswordEncoder passwordEncoder,
		AuthAccountRepository authAccountRepository,
		InitialAdminBootstrapTransaction bootstrapTransaction
	) {
		this.passwordEncoder = passwordEncoder;
		this.authAccountRepository = authAccountRepository;
		this.bootstrapTransaction = bootstrapTransaction;
	}

	public boolean bootstrap(String email, String password) {
		final String normalizedEmail;
		try {
			normalizedEmail = AuthCredentialPolicy.normalizeEmail(email);
			AuthCredentialPolicy.validatePassword(password);
		} catch (AuthException exception) {
			throw new AuthException(ExceptionCode.AUTH_BOOTSTRAP_CONFIGURATION_INVALID);
		}
		final String passwordHash = passwordEncoder.encode(password);
		try {
			return bootstrapTransaction.createIfAbsent(normalizedEmail, passwordHash);
		} catch (DataIntegrityViolationException exception) {
			if (authAccountRepository.existsByRole(UserRole.ADMIN)) {
				return false;
			}
			throw new AuthException(ExceptionCode.AUTH_BOOTSTRAP_FAILED);
		}
	}
}
