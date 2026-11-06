package fixture.ledger;
import org.springframework.stereotype.Service;
@Service
class Ledger {
    private long balance;
    synchronized void credit(long amount) {
        if (amount < 1 || amount > Long.MAX_VALUE - balance) throw new IllegalArgumentException("Invalid credit");
        balance += amount;
    }
    synchronized boolean debit(long amount) {
        if (amount < 1 || amount > balance) return false;
        balance -= amount;
        return true;
    }
    synchronized long balance() { return balance; }
}
