package com.teamforge.platform.security;

import org.apache.catalina.session.StandardManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
public class WebServerHardening {
    @Bean WebServerFactoryCustomizer<TomcatServletWebServerFactory> boundedSessions(
        @Value("${teamforge.security.max-sessions:20000}") int maximum){
        if(maximum<1)throw new IllegalArgumentException("Session capacity must be positive.");
        return factory->factory.addContextCustomizers(context->{
            var manager=new StandardManager();manager.setMaxActiveSessions(maximum);manager.setPathname(null);context.setManager(manager);
        });
    }
}
