package dev.clausevault.clausevault;

import dev.clausevault.clausevault.config.NearAiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
public class ClausevaultApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClausevaultApplication.class, args);
	}

}
