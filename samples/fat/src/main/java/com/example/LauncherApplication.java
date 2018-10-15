package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.server.RouterFunction;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

import reactor.core.publisher.Mono;

@SpringBootApplication
public class LauncherApplication {

	@Bean
	public RouterFunction<?> endpoints() {
		return route(GET("/"),
				request -> ok().body(Mono.just("Hello"), String.class));
	}

	public static void main(String[] args) {
		SpringApplication.run(LauncherApplication.class, args);
	}

}
