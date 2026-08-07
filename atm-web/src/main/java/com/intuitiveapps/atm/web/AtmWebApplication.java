package com.intuitiveapps.atm.web;

import com.intuitiveapps.atm.domain.Bank;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AtmWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtmWebApplication.class, args);
    }

    /**
     * The single in-memory {@link Bank} backing this process.
     *
     * <p>Declared with {@code @Bean} rather than by annotating {@code Bank} with
     * {@code @Component}, because {@code Bank} lives in the domain module and must not import a
     * Spring annotation. Wiring is the application's job, not the domain's - this is the seam
     * that keeps the dependency arrow pointing inwards.
     *
     * <p>State is per process, so restarting the application starts an empty bank, as required.
     */
    @Bean
    Bank bank() {
        return new Bank();
    }
}
