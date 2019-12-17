package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		if (args.length>0 && args[0].equals("--fail")) {
			throw new RuntimeException("Fail!");
		}
		SpringApplication.run(DemoApplication.class, args);
	}

}
