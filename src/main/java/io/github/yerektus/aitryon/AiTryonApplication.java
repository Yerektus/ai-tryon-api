package io.github.yerektus.aitryon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiTryonApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiTryonApplication.class, args);
	}

}
