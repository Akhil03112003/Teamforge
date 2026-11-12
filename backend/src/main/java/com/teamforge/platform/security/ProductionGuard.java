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
        String strategy=environment.getProperty("server.forward-headers-strategy","none");
        if(!strategy.equalsIgnoreCase("none")){
            if(!strategy.equalsIgnoreCase("native") || !environment.matchesProfiles("proxy"))
                throw new IllegalStateException("Forwarded headers require the explicit prod,proxy profile and trusted edge configuration.");
            String peers=environment.getProperty("server.tomcat.remoteip.internal-proxies","");
            if(peers.isBlank())throw new IllegalStateException("Set TRUSTED_PROXY_REGEX to the immediate trusted proxy peers.");
            try{
                var pattern=java.util.regex.Pattern.compile(peers);
                if(pattern.matcher("").matches() || pattern.matcher("203.0.113.254").matches() && pattern.matcher("198.51.100.254").matches())
                    throw new IllegalArgumentException();
            }catch(IllegalArgumentException unsafe){throw new IllegalStateException("Invalid or unrestricted trusted-proxy regex; use reviewed peer addresses.");}
        }
        String migrationUrl=environment.getProperty("spring.flyway.url","");
        if(migrationUrl.matches("(?is).*([?&](password|user|username)=).*"))
            throw new IllegalStateException("Use separate migration credentials; do not embed credentials in the JDBC URL.");
    }
}
