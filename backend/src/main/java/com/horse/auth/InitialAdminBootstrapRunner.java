package com.horse.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.horse.auth.application.InitialAdminBootstrapService;

@Component
@Profile("bootstrap-admin")
public class InitialAdminBootstrapRunner implements ApplicationRunner {

	private final InitialAdminBootstrapService bootstrapService;
	private final String email;
	private final String password;

	public InitialAdminBootstrapRunner(
		InitialAdminBootstrapService bootstrapService,
		@Value("${BOOTSTRAP_ADMIN_EMAIL:}") String email,
		@Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password
	) {
		this.bootstrapService = bootstrapService;
		this.email = email;
		this.password = password;
	}

	@Override
	public void run(ApplicationArguments arguments) {
		bootstrapService.bootstrap(email, password);
	}
}
