package com.teamforge.platform.security;

import com.teamforge.live.EventStreamHub;
import com.teamforge.platform.api.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;

class StreamShutdownTest {
    @Test void shutdownClosesSubscriptionsAndRejectsNewOnes() {
        var hub=new EventStreamHub(10,2,16,15000,30000);
        var session=new MockHttpSession();session.setAttribute("SPRING_SECURITY_CONTEXT",new Object());
        try {
            hub.subscribe(UUID.randomUUID(),UUID.randomUUID(),session);
            assertThat(hub.subscriberCount()).isEqualTo(1);
            hub.shutdown();hub.shutdown();
            assertThat(hub.subscriberCount()).isZero();
            assertThatThrownBy(()->hub.subscribe(UUID.randomUUID(),UUID.randomUUID(),session)).isInstanceOf(ApiException.class);
        } finally {hub.shutdown();}
    }
}
