package vn.com.truongsonbank.t29;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class T24Controller {
    private final AtomicLong accountSequence = new AtomicLong(290000000000L);
    private final Map<String, Account> accounts = new HashMap<>();
    private final Map<String, String> accountsByCccd = new HashMap<>();

    @PostMapping("/accounts")
    synchronized OpenAccountResponse openAccount(@RequestBody OpenAccountRequest request) {
        if (request.cccd() == null || request.cccd().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cccd is required");
        }

        String accountNumber = accountsByCccd.computeIfAbsent(request.cccd(), cccd -> {
            String nextAccountNumber = String.valueOf(accountSequence.incrementAndGet());
            accounts.put(nextAccountNumber, new Account(nextAccountNumber, cccd, BigDecimal.ZERO));
            return nextAccountNumber;
        });

        return new OpenAccountResponse(accountNumber);
    }

    @GetMapping("/accounts/{accountNumber}/balance")
    synchronized BalanceResponse balance(@PathVariable String accountNumber) {
        Account account = account(accountNumber);
        return new BalanceResponse(account.accountNumber(), account.cccd(), account.balance());
    }

    @PostMapping("/accounts/{accountNumber}/deposit")
    synchronized BalanceResponse deposit(@PathVariable String accountNumber, @RequestBody MoneyRequest request) {
        Account account = account(accountNumber);
        BigDecimal amount = positiveAmount(request.amount());
        account.balance = account.balance.add(amount);
        return new BalanceResponse(account.accountNumber(), account.cccd(), account.balance());
    }

    @PostMapping("/accounts/{accountNumber}/withdraw")
    synchronized BalanceResponse withdraw(@PathVariable String accountNumber, @RequestBody MoneyRequest request) {
        Account account = account(accountNumber);
        debit(account, positiveAmount(request.amount()));
        return new BalanceResponse(account.accountNumber(), account.cccd(), account.balance());
    }

    @PostMapping("/transfers")
    synchronized TransferResponse transfer(@RequestBody TransferRequest request) {
        Account from = account(request.fromAccountNumber());
        Account to = account(request.toAccountNumber());
        BigDecimal amount = positiveAmount(request.amount());

        debit(from, amount);
        to.balance = to.balance.add(amount);

        return new TransferResponse(from.accountNumber(), to.accountNumber(), amount, from.balance(), to.balance());
    }

    private Account account(String accountNumber) {
        Account account = accounts.get(accountNumber);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found");
        }
        return account;
    }

    private BigDecimal positiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive");
        }
        return amount;
    }

    private void debit(Account account, BigDecimal amount) {
        if (account.balance.compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "insufficient balance");
        }
        account.balance = account.balance.subtract(amount);
    }

    record OpenAccountRequest(String cccd) {
    }

    record OpenAccountResponse(String accountNumber) {
    }

    record MoneyRequest(BigDecimal amount) {
    }

    record TransferRequest(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
    }

    record TransferResponse(
            String fromAccountNumber,
            String toAccountNumber,
            BigDecimal amount,
            BigDecimal fromBalance,
            BigDecimal toBalance) {
    }

    record BalanceResponse(String accountNumber, String cccd, BigDecimal balance) {
    }

    private static final class Account {
        private final String accountNumber;
        private final String cccd;
        private BigDecimal balance;

        private Account(String accountNumber, String cccd, BigDecimal balance) {
            this.accountNumber = accountNumber;
            this.cccd = cccd;
            this.balance = balance;
        }

        private String accountNumber() {
            return accountNumber;
        }

        private String cccd() {
            return cccd;
        }

        private BigDecimal balance() {
            return balance;
        }
    }
}
