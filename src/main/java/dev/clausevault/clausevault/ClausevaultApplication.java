package dev.clausevault.clausevault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ClausevaultApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClausevaultApplication.class, args);
	}

}
