package vn.com.truongsonbank.core.account.application;

import org.springframework.stereotype.Service;
import vn.com.truongsonbank.core.account.domain.AccountErrors;
import vn.com.truongsonbank.core.account.domain.AccountStatus;
import vn.com.truongsonbank.core.account.infrastructure.persistence.AccountOperationEntity;
import vn.com.truongsonbank.core.account.infrastructure.persistence.AccountOperationRepository;
import vn.com.truongsonbank.core.account.infrastructure.persistence.CoreAccountEntity;
import vn.com.truongsonbank.core.account.infrastructure.persistence.CoreAccountRepository;
import vn.com.truongsonbank.core.account.infrastructure.persistence.CoreCustomerEntity;
import vn.com.truongsonbank.core.account.infrastructure.persistence.CoreCustomerRepository;
import vn.com.truongsonbank.core.account.infrastructure.t29.T29AccountClient;
import vn.com.truongsonbank.shared.exception.BusinessException;
import vn.com.truongsonbank.shared.exception.NotFoundException;

import java.time.Instant;
import java.util.List;

@Service
public class AccountService {
    private static final String OPEN_ACCOUNT = "OPEN_ACCOUNT";

    private final CoreCustomerRepository customers;
    private final CoreAccountRepository accounts;
    private final AccountOperationRepository operations;
    private final T29AccountClient t29;

    public AccountService(CoreCustomerRepository customers,
                          CoreAccountRepository accounts,
                          AccountOperationRepository operations,
                          T29AccountClient t29) {
        this.customers = customers;
        this.accounts = accounts;
        this.operations = operations;
        this.t29 = t29;
    }

    public AccountView open(AccountCommand command, String requestId) {
        require(command.customerId(), "customerId");
        require(command.cccd(), "cccd");
        require(requestId, "X-Idempotency-Key");

        AccountOperationEntity operation = operations.findByOperationTypeAndRequestId(OPEN_ACCOUNT, requestId)
                .orElseGet(() -> operations.save(new AccountOperationEntity(requestId, OPEN_ACCOUNT,
                        command.customerId(), Instant.now())));
        if (!command.customerId().equals(operation.getCustomerId())) {
            throw new BusinessException(AccountErrors.INVALID_REQUEST);
        }
        if (operation.getStatus() == AccountOperationEntity.Status.SUCCESS && operation.getAccountId() != null) {
            return view(accounts.findById(operation.getAccountId()).orElseThrow(() -> new BusinessException(AccountErrors.DOWNSTREAM_FAILURE)));
        }

        CoreAccountEntity existing = accounts.findAllByCustomerIdAndStatusOrderByOpenedAtAsc(command.customerId(), AccountStatus.ACTIVE)
                .stream().findFirst().orElse(null);
        if (existing != null) {
            operation.success(existing.getId());
            operations.save(operation);
            return view(existing);
        }

        saveCustomer(command);
        try {
            String accountNumber = t29.open(command.cccd(), requestId);
            CoreAccountEntity account = accounts.save(new CoreAccountEntity(command.customerId(), accountNumber,
                    currency(command.currency()), Instant.now()));
            operation.success(account.getId());
            operations.save(operation);
            return view(account);
        } catch (RuntimeException exception) {
            operation.failed(exception.getClass().getSimpleName());
            operations.save(operation);
            throw exception;
        }
    }

    public List<AccountView> list(String customerId) {
        require(customerId, "customerId");
        return accounts.findAllByCustomerIdAndStatusOrderByOpenedAtAsc(customerId, AccountStatus.ACTIVE)
                .stream().map(this::view).toList();
    }

    public AccountView detail(String customerId, String accountNumber) {
        return view(owned(customerId, accountNumber));
    }

    public BalanceView balance(String customerId, String accountNumber) {
        CoreAccountEntity account = owned(customerId, accountNumber);
        T29AccountClient.Balance balance = t29.balance(account.getAccountNumber());
        return new BalanceView(balance.accountNumber(), account.getCurrency(), balance.balance());
    }

    private CoreAccountEntity owned(String customerId, String accountNumber) {
        require(customerId, "customerId");
        require(accountNumber, "accountNumber");
        CoreAccountEntity account = accounts.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new NotFoundException("account", accountNumber));
        if (!customerId.equals(account.getCustomerId())) {
            throw new BusinessException(AccountErrors.ACCOUNT_ACCESS_DENIED);
        }
        return account;
    }

    private void saveCustomer(AccountCommand command) {
        CoreCustomerEntity customer = customers.findById(command.customerId())
                .orElseGet(() -> new CoreCustomerEntity(command.customerId()));
        Instant now = Instant.now();
        customer.setPhoneHash(command.phoneHash());
        customer.setCccdHash(command.cccdHash());
        customer.setFullName(command.fullName());
        customer.setStatus("ACTIVE");
        if (customer.getCreatedAt() == null) {
            customer.setCreatedAt(now);
        }
        customer.setUpdatedAt(now);
        customers.save(customer);
    }

    private AccountView view(CoreAccountEntity account) {
        return new AccountView(account.getId(), account.getCustomerId(), account.getAccountNumber(),
                account.getCurrency(), account.getStatus().name(), account.getOpenedAt());
    }

    private String currency(String value) {
        return value == null || value.isBlank() ? "VND" : value.trim().toUpperCase();
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(AccountErrors.INVALID_REQUEST, field);
        }
    }

    public record AccountView(Long accountId, String customerId, String accountNumber, String currency,
                              String status, Instant openedAt) {
    }

    public record BalanceView(String accountNumber, String currency, java.math.BigDecimal balance) {
    }
}
