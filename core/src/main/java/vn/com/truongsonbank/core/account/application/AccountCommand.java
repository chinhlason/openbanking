package vn.com.truongsonbank.core.account.application;

public record AccountCommand(
        String customerId,
        String phoneHash,
        String cccdHash,
        String cccd,
        String fullName,
        String currency) {
}
