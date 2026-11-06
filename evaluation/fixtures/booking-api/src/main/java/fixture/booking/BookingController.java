package fixture.booking;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
@RestController
class BookingController {
    private final BookingStore store;
    BookingController(BookingStore store) { this.store = store; }
    @GetMapping("/bookings/position/{position}")
    String at(@PathVariable int position) {
        if (position < 0 || position > store.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return store.at(position);
    }
    @GetMapping("/receipts/{number}")
    Object receipt(@PathVariable long number, Principal caller) { return store.receipt(number); }
    @PostMapping("/bookings/{number}/cancel")
    String cancel(@PathVariable long number) { return store.cancel(number); }
    @PostMapping("/bookings/import-count")
    int importCount(@RequestParam String count) throws Exception { return store.importCount(count); }
}
