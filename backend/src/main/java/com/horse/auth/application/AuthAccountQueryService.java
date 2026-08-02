package com.horse.auth.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.auth.domain.exception.AuthException;
import com.horse.auth.infrastructure.AuthAccountRepository;
import com.horse.global.exception.ExceptionCode;

@Service
public class AuthAccountQueryService {

	private final AuthAccountRepository authAccountRepository;

	public AuthAccountQueryService(AuthAccountRepository authAccountRepository) {
		this.authAccountRepository = authAccountRepository;
	}

	@Transactional(readOnly = true)
	public AuthAccountResult findMe(String authSubject) {
		return authAccountRepository.findByAuthSubject(authSubject)
			.map(AuthAccountResult::from)
			.orElseThrow(() -> new AuthException(ExceptionCode.AUTH_INVALID_CREDENTIALS));
	}
}
