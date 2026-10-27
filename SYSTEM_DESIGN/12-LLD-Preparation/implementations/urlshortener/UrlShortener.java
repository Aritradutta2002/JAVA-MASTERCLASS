package urlshortener;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Collision-checked code allocation and optional click observation. */
public final class UrlShortener {
    public interface Analytics { void clicked(String code, Instant at); }
    public static final class NoAnalytics implements Analytics { @Override public void clicked(String code, Instant at) {} }
    public interface CodeGenerator { String next(); }
    public static final class RandomCodeGenerator implements CodeGenerator {
        private static final char[] ALPHABET="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
        private final SecureRandom random = new SecureRandom();
        private final int length;
        public RandomCodeGenerator(int length) { if (length < 7) throw new IllegalArgumentException("Use at least 7 characters"); this.length=length; }
        @Override public String next() { StringBuilder out=new StringBuilder(length); for(int i=0;i<length;i++) out.append(ALPHABET[random.nextInt(ALPHABET.length)]); return out.toString(); }
    }
    public record Mapping(String code, String target, String ownerId, Instant createdAt, Instant expiresAt) { }
    public static final class Service {
        private final CodeGenerator generator; private final ConcurrentMap<String,Mapping> mappings=new ConcurrentHashMap<>(); private final Analytics analytics;
        public Service(CodeGenerator generator, Analytics analytics) { this.generator=Objects.requireNonNull(generator); this.analytics=Objects.requireNonNull(analytics); }
        public Mapping create(String target, String ownerId, Instant now, Instant expiresAt) {
            if (!target.startsWith("https://") && !target.startsWith("http://")) throw new IllegalArgumentException("Only HTTP(S) targets are supported");
            for(int attempt=0; attempt<10; attempt++) { String code=generator.next(); Mapping m=new Mapping(code,target,ownerId,now,expiresAt); if(mappings.putIfAbsent(code,m)==null) return m; }
            throw new IllegalStateException("Code allocation exhausted");
        }
        public Optional<String> resolve(String code, Instant now) { Mapping m=mappings.get(code); if(m==null || m.expiresAt().isBefore(now)) return Optional.empty(); analytics.clicked(code,now); return Optional.of(m.target()); }
        public boolean isActive(String code, Instant now) { Mapping m=mappings.get(code); return m!=null && !m.expiresAt().isBefore(now); }
    }
    public static void main(String[] args) {
        Service service=new Service(new RandomCodeGenerator(10),new NoAnalytics()); Instant now=Instant.parse("2026-01-01T00:00:00Z");
        Mapping mapping=service.create("https://example.com","u1",now,now.plusSeconds(3600));
        System.out.println("code="+mapping.code()+",resolved="+service.resolve(mapping.code(),now).orElse("missing"));
    }
}
