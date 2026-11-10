package com.teamforge.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamforge.platform.api.ApiErrors;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds reads before CSRF/form parsing; never includes submitted content in errors. */
public final class RequestLimitsFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    private final int maximum;
    private final int bootstrapLimit;
    private final Map<String,long[]> bootstraps=new HashMap<>();
    public RequestLimitsFilter(ObjectMapper mapper,int maximum,int bootstrapLimit){
        if(maximum<16384 || maximum>1048576)throw new IllegalArgumentException("Invalid request-size bound.");
        if(bootstrapLimit<1 || bootstrapLimit>100000)throw new IllegalArgumentException("Invalid session-bootstrap bound.");
        this.mapper=mapper;this.maximum=maximum;this.bootstrapLimit=bootstrapLimit;
    }
    private synchronized boolean bootstrap(String address){
        long now=System.currentTimeMillis();bootstraps.entrySet().removeIf(e->now-e.getValue()[0]>=60000);
        var window=bootstraps.get(address);
        if(window==null){if(bootstraps.size()>=10000)return false;window=new long[]{now,0};bootstraps.put(address,window);}
        return ++window[1]<=bootstrapLimit;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws IOException,ServletException {
        if(request.getMethod().equals("GET") && request.getServletPath().equals("/api/v1/auth/csrf")
            && request.getSession(false)==null && !bootstrap(request.getRemoteAddr())){
            response.setHeader("Retry-After","60");ApiErrors.write(response,mapper,429,"RATE_LIMITED","Too many new sessions. Retry shortly.");return;
        }
        if(!request.getServletPath().startsWith("/api/") || Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())){
            forward(request,response,chain);return;
        }
        if(request.getServletPath().equals("/api/v1/auth/login") && request.getQueryString()!=null){
            ApiErrors.write(response,mapper,400,"INVALID_REQUEST","Send sign-in fields in the request body, not the URL.");return;
        }
        if(request.getContentLengthLong()>maximum){tooLarge(response);return;}
        byte[] body=request.getInputStream().readNBytes(maximum+1);
        if(body.length>maximum){tooLarge(response);return;}
        Map<String,String[]> parameters=null;
        if(Optional.ofNullable(request.getContentType()).orElse("").toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")){
            try{parameters=form(new String(body,StandardCharsets.UTF_8),request.getQueryString());}
            catch(IllegalArgumentException invalid){ApiErrors.write(response,mapper,400,"INVALID_REQUEST","The form body is invalid.");return;}
        }
        forward(new CachedRequest(request,body,parameters),response,chain);
    }
    private void forward(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        try{chain.doFilter(request,response);}
        catch(RuntimeException|ServletException failure){
            for(Throwable cause=failure;cause!=null;cause=cause.getCause()){
                if(cause instanceof org.apache.catalina.session.TooManyActiveSessionsException && !response.isCommitted()){
                    response.resetBuffer();response.setHeader("Retry-After","60");
                    ApiErrors.write(response,mapper,503,"SESSION_CAPACITY","Session capacity reached. Retry shortly.");return;
                }
                if(cause.getCause()==cause)break;
            }
            throw failure;
        }
    }
    private void tooLarge(HttpServletResponse response)throws IOException{
        ApiErrors.write(response,mapper,413,"REQUEST_TOO_LARGE","The request body exceeds the supported size.");
    }
    private static Map<String,String[]> form(String body,String query){
        var result=new LinkedHashMap<String,String[]>();
        String encoded=body+(query==null || query.isEmpty()?"":"&"+query);
        if(encoded.isEmpty())return result;
        var pairs=encoded.split("&",-1);if(pairs.length>100)throw new IllegalArgumentException();
        for(String pair:pairs){
            String[] entry=pair.split("=",2);String key=URLDecoder.decode(entry[0],StandardCharsets.UTF_8);
            String value=entry.length==1?"":URLDecoder.decode(entry[1],StandardCharsets.UTF_8);
            if(result.putIfAbsent(key,new String[]{value})!=null)throw new IllegalArgumentException();
        }
        return result;
    }
    private static final class CachedRequest extends HttpServletRequestWrapper {
        private final byte[] body;private final Map<String,String[]> form;
        CachedRequest(HttpServletRequest request,byte[] body,Map<String,String[]> form){super(request);this.body=body;this.form=form;}
        @Override public ServletInputStream getInputStream(){
            var input=new ByteArrayInputStream(body);
            return new ServletInputStream(){
                @Override public int read(){return input.read();}
                @Override public int read(byte[] bytes,int offset,int length){return input.read(bytes,offset,length);}
                @Override public boolean isFinished(){return input.available()==0;}
                @Override public boolean isReady(){return true;}
                @Override public void setReadListener(ReadListener listener){
                    Objects.requireNonNull(listener);
                    try{if(!isFinished())listener.onDataAvailable();if(isFinished())listener.onAllDataRead();}
                    catch(IOException error){listener.onError(error);}
                }
            };
        }
        @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
        @Override public String getParameter(String name){if(form==null)return super.getParameter(name);var values=form.get(name);return values==null?null:values[0];}
        @Override public Map<String,String[]> getParameterMap(){return form==null?super.getParameterMap():Collections.unmodifiableMap(form);}
        @Override public Enumeration<String> getParameterNames(){return form==null?super.getParameterNames():Collections.enumeration(form.keySet());}
        @Override public String[] getParameterValues(String name){return form==null?super.getParameterValues(name):form.get(name);}
    }
}
