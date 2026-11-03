package elevator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Direction-aware request dispatch with an explicit car state machine. */
public final class ElevatorSystem {
    public enum Direction { UP, DOWN, NONE }
    public enum CarState { IDLE, MOVING, DOOR_OPEN, OUT_OF_SERVICE }
    public enum DispatchPolicy { NEAREST }

    public static final class Request {
        private final int origin;
        private final int destination;
        private final Direction requestedDirection;
        public Request(int origin, int destination, Direction requestedDirection) { this.origin=origin; this.destination=destination; this.requestedDirection=Objects.requireNonNull(requestedDirection); }
        public int origin() { return origin; }
        public int destination() { return destination; }
        public Direction requestedDirection() { return requestedDirection; }
    }

    public static final class ElevatorCar {
        private final int id;
        private final int capacity;
        private int floor;
        private int passengers;
        private Direction direction = Direction.NONE;
        private CarState state = CarState.IDLE;
        public ElevatorCar(int id, int floor, int capacity) { this.id=id; this.floor=floor; this.capacity=capacity; }
        public synchronized void moveTo(int target) {
            if (state == CarState.OUT_OF_SERVICE) throw new IllegalStateException("Car unavailable");
            if (target < 1) throw new IllegalArgumentException("Floor must be positive");
            state = CarState.MOVING;
            floor = target;
            state = CarState.IDLE;
        }
        public synchronized void openDoor() { if (state == CarState.OUT_OF_SERVICE) throw new IllegalStateException(); state = CarState.DOOR_OPEN; }
        public synchronized void closeDoor() { if (state != CarState.DOOR_OPEN) throw new IllegalStateException("Door is not open"); state = CarState.IDLE; }
        public synchronized void board(int count) { if (passengers + count > capacity) throw new IllegalStateException("Capacity exceeded"); passengers += count; }
        public synchronized void alight(int count) { if (count > passengers) throw new IllegalStateException("Invalid passenger count"); passengers -= count; }
        public synchronized void setDirection(Direction direction) { this.direction = Objects.requireNonNull(direction); }
        public int id() { return id; }
        public synchronized int floor() { return floor; }
        public synchronized int passengers() { return passengers; }
        public synchronized Direction direction() { return direction; }
        public synchronized CarState state() { return state; }
    }

    public static final class ElevatorController {
        private final Map<Integer, ElevatorCar> cars = new HashMap<>();
        private final Deque<Request> requests = new ArrayDeque<>();
        public ElevatorController(ElevatorCar... cars) { for (ElevatorCar car : cars) this.cars.put(car.id(), car); }
        public synchronized void request(Request request) { requests.addLast(request); }
        public synchronized ElevatorCar dispatch(DispatchPolicy policy) {
            if (requests.isEmpty()) throw new IllegalStateException("No request");
            Request request = requests.removeFirst();
            ElevatorCar selected = cars.values().stream().min((a,b) -> Integer.compare(Math.abs(a.floor()-request.origin()), Math.abs(b.floor()-request.origin()))).orElseThrow();
            selected.setDirection(request.requestedDirection());
            selected.moveTo(request.origin());
            selected.openDoor();
            return selected;
        }
        public synchronized int pendingRequests() { return requests.size(); }
    }

    public static void main(String[] args) {
        ElevatorController controller = new ElevatorController(new ElevatorCar(1, 1, 8), new ElevatorCar(2, 5, 8));
        controller.request(new Request(1, 7, Direction.UP));
        ElevatorCar car = controller.dispatch(DispatchPolicy.NEAREST);
        car.board(2); car.closeDoor(); car.moveTo(7); car.alight(2);
        System.out.println("car=" + car.id() + ",floor=" + car.floor() + ",state=" + car.state());
    }
}
