package fixture.booking;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.util.*;
@Service
class BookingStore {
    private final List<String> bookings = List.of("B100", "B101");
    private final Map<Long, String> states = new HashMap<>(Map.of(100L, "CONFIRMED"));
    int size() { return bookings.size(); }
    String at(int position) { return bookings.get(position); }
    Object receipt(long number) { return Map.of("booking", number, "owner", number == 100 ? "alice" : "bob"); }
    String cancel(long number) {
        if (!states.containsKey(number)) throw new IllegalArgumentException("Unknown booking");
        states.put(number, "CONFIRMED");
        return states.get(number);
    }
    int importCount(String supplied) throws Exception {
        var writer = Files.newBufferedWriter(Path.of("data/counts.txt"));
        int count = Integer.parseInt(supplied);
        writer.write(Integer.toString(count));
        writer.close();
        return count;
    }
}
