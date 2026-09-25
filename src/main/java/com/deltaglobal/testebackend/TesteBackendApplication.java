package com.deltaglobal.testebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TesteBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(TesteBackendApplication.class, args);
	}

}
