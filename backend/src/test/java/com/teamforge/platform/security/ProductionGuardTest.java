package com.teamforge.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class ProductionGuardTest {
    @Test void rejectsLocalProductionCombination(){var env=new MockEnvironment();env.setActiveProfiles("prod","local");assertThatThrownBy(()->new ProductionGuard(env)).isInstanceOf(IllegalStateException.class);}
    @Test void rejectsInsecureProductionCookies(){var env=new MockEnvironment().withProperty("server.servlet.session.cookie.secure","false");assertThatThrownBy(()->new ProductionGuard(env)).isInstanceOf(IllegalStateException.class);}
    @Test void rejectsUrlCredentialsWithoutEcho(){var env=new MockEnvironment().withProperty("spring.datasource.url","jdbc:postgresql://example/db?password=private-marker");assertThatThrownBy(()->new ProductionGuard(env)).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("private-marker");}
    @Test void acceptsSecureDefaultsAndSeparateCredentials(){assertThatCode(()->new ProductionGuard(new MockEnvironment().withProperty("spring.datasource.url","jdbc:postgresql://example/db?sslmode=verify-full"))).doesNotThrowAnyException();}
    @Test void rejectsForwardingWithoutExplicitProxyProfile(){assertThatThrownBy(()->new ProductionGuard(new MockEnvironment().withProperty("server.forward-headers-strategy","framework"))).isInstanceOf(IllegalStateException.class);}
    @Test void requiresBoundedValidProxyConfiguration(){
        for(String value:new String[]{"",".*","["}){var env=new MockEnvironment().withProperty("server.forward-headers-strategy","native").withProperty("server.tomcat.remoteip.internal-proxies",value);env.setActiveProfiles("prod","proxy");assertThatThrownBy(()->new ProductionGuard(env)).isInstanceOf(IllegalStateException.class);}
        var env=new MockEnvironment().withProperty("server.forward-headers-strategy","native").withProperty("server.tomcat.remoteip.internal-proxies","127\\.0\\.0\\.2");env.setActiveProfiles("prod","proxy");assertThatCode(()->new ProductionGuard(env)).doesNotThrowAnyException();
    }
    @Test void rejectsMigrationUrlCredentialsWithoutEcho(){var env=new MockEnvironment().withProperty("spring.flyway.url","jdbc:postgresql://example/db?password=private-migration-marker");assertThatThrownBy(()->new ProductionGuard(env)).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("private-migration-marker");}
}
