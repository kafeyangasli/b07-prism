package com.github.kafeyangasli.prism.config.seed;

import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityTypeRepository;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityImageRepository;
import com.github.kafeyangasli.prism.feature.facility.service.FacilityImageStorage;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DevelopmentSeederProfileTest {
    @Test void runsByDefaultAndRejectsProductionProfiles() {
        for (String[] profiles : new String[][]{{}, {"test"}, {"prod"}, {"production"},
                {"dev", "prod"}, {"dev", "production"}, {"dev"}}) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().setActiveProfiles(profiles);
                context.registerBean(FacilityRepository.class, () -> mock(FacilityRepository.class));
                context.registerBean(FacilityTypeRepository.class, () -> mock(FacilityTypeRepository.class));
                context.registerBean(FacilityImageRepository.class, () -> mock(FacilityImageRepository.class));
                context.registerBean(FacilityImageStorage.class, () -> mock(FacilityImageStorage.class));
                context.registerBean(UserRepository.class, () -> mock(UserRepository.class));
                context.registerBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class));
                context.registerBean(Clock.class, Clock::systemUTC);
                context.register(DevelopmentDatabaseSeeder.class);
                context.refresh();
                boolean production = java.util.Arrays.asList(profiles).contains("prod")
                        || java.util.Arrays.asList(profiles).contains("production");
                assertThat(context.getBeansOfType(DevelopmentDatabaseSeeder.class)).hasSize(production ? 0 : 1);
            }
        }
    }

    @Test void explicitDisabledSettingPreventsSeederCreation() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource("test",
                            java.util.Map.of("prism.seed.enabled", "false")));
            context.registerBean(FacilityRepository.class, () -> mock(FacilityRepository.class));
            context.registerBean(FacilityTypeRepository.class, () -> mock(FacilityTypeRepository.class));
            context.registerBean(FacilityImageRepository.class, () -> mock(FacilityImageRepository.class));
            context.registerBean(FacilityImageStorage.class, () -> mock(FacilityImageStorage.class));
            context.registerBean(UserRepository.class, () -> mock(UserRepository.class));
            context.registerBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class));
            context.registerBean(Clock.class, Clock::systemUTC);
            context.register(DevelopmentDatabaseSeeder.class);
            context.refresh();
            assertThat(context.getBeansOfType(DevelopmentDatabaseSeeder.class)).isEmpty();
        }
    }
}
