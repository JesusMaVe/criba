package com.criba.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Actor fijo mientras el MVP sea local y de un solo usuario. */
@Configuration
public class FixedActorConfiguration {

    @Bean
    CurrentActor currentActor() {
        return () -> Actor.user("local-user");
    }
}
