package com.horse;

import org.springframework.boot.SpringApplication;

public class TestHorseBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(HorseBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
