package com.teamforge.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamforge.platform.api.ApiErrors;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded single-instance per-account and per-IP windows. Never records invitation content. */
public final class InviteRateLimitFilter extends OncePerRequestFilter {
    private record Window(long start,int count) {}
    private final Map<String,Window> windows=new HashMap<>();
    private final ObjectMapper mapper;
    private final int limit;
    public InviteRateLimitFilter(ObjectMapper mapper,int limit) {
        if (limit<1 || limit>100000) throw new IllegalArgumentException("Invite rate limit must be between 1 and 100000.");
        this.mapper=mapper;this.limit=limit;
    }
    private synchronized boolean allow(String account,String address) {
        long now=System.currentTimeMillis();windows.entrySet().removeIf(e->now-e.getValue().start()>=60000);
        var keys=List.of("user:"+account,"ip:"+address);
        for (String key:keys) {
            var old=windows.get(key);
            int threshold=key.startsWith("ip:")?limit*10:limit;
            if (old!=null && old.count()>=threshold || old==null && windows.size()>=20000) return false;
        }
        for (String key:keys) {var old=windows.get(key);windows.put(key,new Window(old==null?now:old.start(),old==null?1:old.count()+1));}
        return true;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws ServletException,IOException {
        if (request.getMethod().equals("POST") && request.getServletPath().matches("/api/v1/hackathons/[^/]+/invite-joins")
            && request.getUserPrincipal()!=null && !allow(request.getUserPrincipal().getName(),request.getRemoteAddr())) {
            response.setHeader("Retry-After","60");
            ApiErrors.write(response,mapper,429,"RATE_LIMITED","Too many invitation attempts. Try again shortly.");return;
        }
        chain.doFilter(request,response);
    }
}
