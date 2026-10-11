package com.app.accounting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AccountingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccountingServiceApplication.class, args);
	}

}
