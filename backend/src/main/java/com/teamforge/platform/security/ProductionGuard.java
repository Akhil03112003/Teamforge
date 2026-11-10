package com.teamforge.platform.security;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionGuard {
    public ProductionGuard(Environment environment){
        if(environment.matchesProfiles("local") || !environment.getProperty("server.servlet.session.cookie.secure",Boolean.class,true)
            || !environment.getProperty("server.servlet.session.cookie.http-only",Boolean.class,true))
            throw new IllegalStateException("Production requires Secure/HttpOnly cookies and forbids the local profile.");
        String url=environment.getProperty("spring.datasource.url","");
        if(url.matches("(?is).*([?&](password|user|username)=).*"))
            throw new IllegalStateException("Use separate datasource credentials; do not embed credentials in the JDBC URL.");
    }
}
