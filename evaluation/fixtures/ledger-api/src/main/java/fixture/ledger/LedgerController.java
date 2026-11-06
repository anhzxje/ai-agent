package fixture.ledger;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@RestController
class LedgerController {
    private final Ledger ledger;
    LedgerController(Ledger ledger) { this.ledger = ledger; }
    @PostMapping("/ledger/credit")
    long credit(@RequestParam long amount) {
        if (amount < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        ledger.credit(amount);
        return ledger.balance();
    }
}
