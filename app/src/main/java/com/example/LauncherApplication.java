package com.example;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LauncherApplication {

	@Bean
	public ApplicationRunner runner() {
		return app -> {
			if (app.containsOption("fail")) {
				throw new RuntimeException("Planned!");
			}
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(LauncherApplication.class, args);
	}
}
