package com.teamforge.platform.security;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEAMFORGE_DATABASE_TESTS",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.profiles.active=prod,proxy","server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.2"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class TrustedProxyIntegrationTest {
    @LocalServerPort int port;
    List<String> headers(String source)throws Exception{
        try(var socket=new Socket()){
            socket.bind(new InetSocketAddress(source,0));socket.connect(new InetSocketAddress("127.0.0.1",port),5000);socket.setSoTimeout(5000);
            socket.getOutputStream().write(("GET /api/v1/auth/csrf HTTP/1.1\r\nHost: localhost\r\nX-Forwarded-Proto: https\r\nX-Forwarded-For: 198.51.100.9\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
            assertThat(reader.readLine()).startsWith("HTTP/1.1 200");var result=new ArrayList<String>();String line;
            while((line=reader.readLine())!=null && !line.isEmpty())result.add(line.toLowerCase(Locale.ROOT));return result;
        }
    }
    @Test void untrustedPeerCannotSpoofHttps(){assertThatCode(()->{
        var headers=headers("127.0.0.1");assertThat(headers.stream().anyMatch(h->h.startsWith("strict-transport-security:"))).isFalse();
    }).doesNotThrowAnyException();}
    @Test void trustedPeerSetsHttpsAndSecureSessionFlags()throws Exception{
        var headers=headers("127.0.0.2");assertThat(headers.stream().anyMatch(h->h.startsWith("strict-transport-security:"))).isTrue();
        assertThat(headers.stream().anyMatch(h->h.startsWith("set-cookie:")&&h.contains("; secure")&&h.contains("; httponly")&&h.contains("samesite=lax"))).isTrue();
    }
}
