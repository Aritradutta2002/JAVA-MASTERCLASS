package parkinglot;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Atomic spot allocation and deterministic fee calculation. */
public final class ParkingLotSystem {
    public enum VehicleType { COMPACT, STANDARD, EV }
    public enum SpotState { FREE, OCCUPIED }
    public enum SpotType { COMPACT, STANDARD, EV }

    public static final class Spot {
        private final String id;
        private final SpotType type;
        private SpotState state = SpotState.FREE;
        public Spot(String id, SpotType type) { this.id = Objects.requireNonNull(id); this.type = Objects.requireNonNull(type); }
        public synchronized SpotState state() { return state; }
        public synchronized void occupy() { if (state != SpotState.FREE) throw new IllegalStateException("Spot is occupied"); state = SpotState.OCCUPIED; }
        public synchronized void release() { state = SpotState.FREE; }
        public String id() { return id; }
        public SpotType type() { return type; }
    }

    public interface FeePolicy { BigDecimal fee(Duration duration); }
    public enum FeePolicyFactory {
        FLAT_5_PER_HOUR {
            @Override public FeePolicy create() { return duration -> BigDecimal.valueOf(5).multiply(BigDecimal.valueOf(Math.max(1, (duration.toMinutes() + 59) / 60))); }
        };
        public abstract FeePolicy create();
    }

    public static final class Lease {
        private final String id;
        private final String vehicleId;
        private final Spot spot;
        private final Instant enteredAt;
        private BigDecimal fee = BigDecimal.ZERO;
        private boolean closed;
        public Lease(String id, String vehicleId, Spot spot, Instant enteredAt) { this.id=id; this.vehicleId=vehicleId; this.spot=spot; this.enteredAt=enteredAt; }
        public synchronized BigDecimal close(Instant now, FeePolicy policy) {
            if (closed) return fee;
            closed = true;
            fee = policy.fee(Duration.between(enteredAt, now));
            spot.release();
            return fee;
        }
        public String id() { return id; }
        public String vehicleId() { return vehicleId; }
        public Spot spot() { return spot; }
        public boolean closed() { return closed; }
    }

    public static final class ParkingLot {
        private final Map<String, Spot> spots = new ConcurrentHashMap<>();
        private final Map<String, Lease> leases = new ConcurrentHashMap<>();
        public ParkingLot(Collection<Spot> initial) { initial.forEach(s -> spots.put(s.id(), s)); }
        public Lease enter(String vehicleId, VehicleType type, Instant at) {
            Objects.requireNonNull(vehicleId); Objects.requireNonNull(type); Objects.requireNonNull(at);
            Spot candidate = spots.values().stream().filter(s -> s.state() == SpotState.FREE && compatible(s.type(), type)).sorted((a,b) -> a.id().compareTo(b.id())).findFirst().orElseThrow(() -> new IllegalStateException("No compatible free spot"));
            synchronized (candidate) {
                if (candidate.state() != SpotState.FREE) throw new IllegalStateException("Spot allocation race");
                candidate.occupy();
            }
            Lease lease = new Lease(UUID.randomUUID().toString(), vehicleId, candidate, at);
            leases.put(lease.id(), lease);
            return lease;
        }
        public BigDecimal exit(String leaseId, Instant now, FeePolicy policy) {
            Lease lease = leases.get(leaseId);
            if (lease == null) throw new IllegalArgumentException("Unknown lease");
            return lease.close(now, policy);
        }
        private static boolean compatible(SpotType spot, VehicleType vehicle) {
            return spot == SpotType.STANDARD || (spot == SpotType.EV && vehicle == VehicleType.EV) || (spot == SpotType.COMPACT && vehicle == VehicleType.COMPACT);
        }
    }

    public static void main(String[] args) {
        ParkingLot lot = new ParkingLot(java.util.List.of(new Spot("A1", SpotType.STANDARD), new Spot("C1", SpotType.COMPACT)));
        Instant entry = Instant.parse("2026-01-01T10:00:00Z");
        Lease lease = lot.enter("CAR-1", VehicleType.STANDARD, entry);
        System.out.println("allocated=" + lease.spot().id());
        System.out.println("fee=" + lot.exit(lease.id(), entry.plusSeconds(3700), FeePolicyFactory.FLAT_5_PER_HOUR.create()));
    }
}
