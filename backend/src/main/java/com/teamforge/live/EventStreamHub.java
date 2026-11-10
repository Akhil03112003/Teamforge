package com.teamforge.live;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.support.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.teamforge.platform.api.ApiException;

/** Single-process, bounded invalidation delivery. PostgreSQL/REST is authoritative. */
@Component
public class EventStreamHub {
    private final Map<UUID,Subscription> subscribers=new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("sse-heartbeat").factory());
    private final int maximum,perUser,buffer;
    private final long heartbeat,timeout;
    private boolean stopping;
    public EventStreamHub(@Value("${teamforge.sse.max-connections:10000}") int maximum,
        @Value("${teamforge.sse.per-user:2}") int perUser,@Value("${teamforge.sse.buffer:16}") int buffer,
        @Value("${teamforge.sse.heartbeat-ms:15000}") long heartbeat,@Value("${teamforge.sse.timeout-ms:1800000}") long timeout) {
        if(maximum<1 || perUser<1 || buffer<1 || heartbeat<100 || timeout<heartbeat) throw new IllegalArgumentException("Invalid SSE bounds.");
        this.maximum=maximum;this.perUser=perUser;this.buffer=buffer;this.heartbeat=heartbeat;this.timeout=timeout;
        timer.scheduleAtFixedRate(this::heartbeat,heartbeat,heartbeat,TimeUnit.MILLISECONDS);
    }
    public final class Subscription {
        private final UUID id=UUID.randomUUID(),event,user;
        private final HttpSession session;
        private final ArrayBlockingQueue<String> queue=new ArrayBlockingQueue<>(buffer);
        private final AtomicBoolean closed=new AtomicBoolean();
        private final SseEmitter emitter=new SseEmitter(timeout);
        private Thread writer;
        private Subscription(UUID event,UUID user,HttpSession session) {this.event=event;this.user=user;this.session=session;}
        public SseEmitter emitter(){return emitter;}
        public UUID id(){return id;}
        private boolean authenticated() {
            try {return session.getAttribute("SPRING_SECURITY_CONTEXT")!=null &&
                (session.getMaxInactiveInterval()<=0 || System.currentTimeMillis()-session.getLastAccessedTime()<session.getMaxInactiveInterval()*1000L);}
            catch(IllegalStateException expired){return false;}
        }
        private void offer(String event) {if(!closed.get() && !queue.offer(event))close();}
        public void close() {
            if(closed.compareAndSet(false,true)) {subscribers.remove(id);queue.clear();if(writer!=null)writer.interrupt();try{emitter.complete();}catch(RuntimeException ignored){}}
        }
        private void write() {
            try {
                while(!closed.get()) {
                    String type=queue.take();
                    if(!authenticated()){emitter.send(SseEmitter.event().name("session-expired").data(Map.of("type","SESSION_EXPIRED")));break;}
                    if(type.equals("heartbeat"))emitter.send(SseEmitter.event().comment("heartbeat"));
                    else emitter.send(SseEmitter.event().name(type).reconnectTime(3000).data(Map.of("type","REFRESH")));
                }
            } catch(IOException|IllegalStateException ignored) { /* disconnect */ }
              catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            finally{close();}
        }
    }
    public synchronized Subscription subscribe(UUID event,UUID user,HttpSession session) {
        if(stopping)throw new ApiException(503,"STREAM_UNAVAILABLE","Live updates are restarting. Retry shortly.");
        if(session==null)throw new ApiException(401,"UNAUTHENTICATED","Sign in to continue.");
        if(subscribers.size()>=maximum || subscribers.values().stream().filter(s->s.user.equals(user)).count()>=perUser)
            throw new ApiException(429,"STREAM_LIMIT","Too many live connections. Close another tab or retry shortly.");
        var subscription=new Subscription(event,user,session);subscribers.put(subscription.id,subscription);
        subscription.emitter.onCompletion(subscription::close);subscription.emitter.onTimeout(subscription::close);
        subscription.emitter.onError(error->subscription.close());
        subscription.writer=Thread.ofVirtual().name("sse-"+subscription.id).unstarted(subscription::write);
        subscription.writer.start();subscription.offer("ready");return subscription;
    }
    private void heartbeat(){for(var subscriber:subscribers.values())subscriber.offer("heartbeat");}
    public int subscriberCount(){return subscribers.size();}
    public void publish(UUID event){for(var subscriber:subscribers.values())if(subscriber.event.equals(event))subscriber.offer("invalidate");}
    public void changed(UUID event) {
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Notifications require a transaction.");
        for(var sync:TransactionSynchronizationManager.getSynchronizations())if(sync instanceof Pending pending){pending.events.add(event);return;}
        var pending=new Pending();pending.events.add(event);TransactionSynchronizationManager.registerSynchronization(pending);
    }
    private final class Pending implements TransactionSynchronization {
        final Set<UUID> events=new HashSet<>();
        @Override public void afterCommit(){for(UUID event:events)publish(event);}
    }
    @EventListener(ContextClosedEvent.class)
    @PreDestroy public synchronized void shutdown(){stopping=true;timer.shutdownNow();for(var subscription:List.copyOf(subscribers.values()))subscription.close();}
}
