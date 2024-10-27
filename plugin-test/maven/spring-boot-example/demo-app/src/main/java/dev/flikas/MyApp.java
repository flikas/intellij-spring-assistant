package dev.flikas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.Charset;

/**
 * {@link org.springframework.boot.context.config.ConfigFileApplicationListener}
 */
@SpringBootApplication
public class MyApp implements ApplicationRunner {

    @Autowired private MyProperties properties;

    public static void main(String[] args) {
        SpringApplication.run(MyApp.class);
    }

    @GetMapping("/hello")
    public String hello() {
        return "world";
    }

    @Bean
    public String bean() {
        return "A";
    }

    @Bean
    @ConfigurationProperties("example.server")
    public LombokPojo lombokPojo() {
        return new LombokPojo();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println(properties.getKeyStore());
    }
}
