import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** Small JRE-only readiness probe; no curl or runtime package manager required. */
public final class HealthCheck {
    public static void main(String[] args) {
        try {
            int port=Integer.parseInt(System.getenv().getOrDefault("PORT","8080"));
            if(port<1 || port>65535)throw new IllegalArgumentException();
            try(var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()){
                var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/actuator/health/readiness")).timeout(Duration.ofSeconds(3)).GET().build();
                if(client.send(request,HttpResponse.BodyHandlers.discarding()).statusCode()!=200)System.exit(1);
            }
        }catch(Exception unavailable){System.exit(1);}
    }
}
