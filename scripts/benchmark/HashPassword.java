import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Offline benchmark fixture utility; password is read from the environment, not argv. */
class HashPassword {
    public static void main(String[] args) {
        String password=System.getenv("BENCHMARK_PASSWORD");
        if(password==null || password.length()<12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("Set a synthetic BENCHMARK_PASSWORD of 12 to 72 UTF-8 bytes.");
        System.out.print(new BCryptPasswordEncoder(12).encode(password));
    }
}
