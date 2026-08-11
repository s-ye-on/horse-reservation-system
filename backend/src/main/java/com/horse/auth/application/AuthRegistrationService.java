package com.horse.auth.application;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.horse.auth.domain.AuthAccount;
import com.horse.auth.domain.AuthCredentialPolicy;
import com.horse.auth.domain.exception.AuthException;
import com.horse.auth.infrastructure.AuthAccountRepository;
import com.horse.global.exception.ExceptionCode;

@Service
public class AuthRegistrationService {

	private final PasswordEncoder passwordEncoder;
	private final AuthAccountRepository authAccountRepository;
	private final AuthRegistrationTransaction registrationTransaction;

	public AuthRegistrationService(
		PasswordEncoder passwordEncoder,
		AuthAccountRepository authAccountRepository,
		AuthRegistrationTransaction registrationTransaction
	) {
		this.passwordEncoder = passwordEncoder;
		this.authAccountRepository = authAccountRepository;
		this.registrationTransaction = registrationTransaction;
	}

	public AuthAccountResult signup(String email, String password, String name, String phone) {
		final String normalizedEmail = AuthCredentialPolicy.normalizeEmail(email);
		AuthCredentialPolicy.validatePassword(password);
		final String passwordHash = passwordEncoder.encode(password);
		try {
			final AuthAccount account = registrationTransaction.createMemberAccount(
				UUID.randomUUID().toString(),
				normalizedEmail,
				passwordHash,
				name,
				phone
			);
			return AuthAccountResult.from(account);
		} catch (DataIntegrityViolationException exception) {
			if (authAccountRepository.existsByNormalizedEmail(normalizedEmail)) {
				throw new AuthException(ExceptionCode.AUTH_EMAIL_ALREADY_EXISTS);
			}
			throw new AuthException(ExceptionCode.AUTH_ACCOUNT_CREATION_CONFLICT);
		}
	}
}
