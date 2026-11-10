package com.teamforge.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class ProductionGuardTest {
    @Test void rejectsLocalProductionCombination(){var env=new MockEnvironment();env.setActiveProfiles("prod","local");assertThatThrownBy(()->new ProductionGuard(env)).isInstanceOf(IllegalStateException.class);}
    @Test void rejectsInsecureProductionCookies(){var env=new MockEnvironment().withProperty("server.servlet.session.cookie.secure","false");assertThatThrownBy(()->new ProductionGuard(env)).isInstanceOf(IllegalStateException.class);}
    @Test void rejectsUrlCredentialsWithoutEcho(){var env=new MockEnvironment().withProperty("spring.datasource.url","jdbc:postgresql://example/db?password=private-marker");assertThatThrownBy(()->new ProductionGuard(env)).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("private-marker");}
    @Test void acceptsSecureDefaultsAndSeparateCredentials(){assertThatCode(()->new ProductionGuard(new MockEnvironment().withProperty("spring.datasource.url","jdbc:postgresql://example/db?sslmode=verify-full"))).doesNotThrowAnyException();}
}
