package vn.com.truongsonbank.core.account.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.truongsonbank.core.account.application.AccountCommand;
import vn.com.truongsonbank.core.account.application.AccountService;
import vn.com.truongsonbank.shared.exception.UnauthorizedException;
import vn.com.truongsonbank.shared.response.ResponseWrapper;
import vn.com.truongsonbank.shared.security.AuthContextHolder;
import vn.com.truongsonbank.shared.security.RequireEntitlement;
import vn.com.truongsonbank.shared.security.RequireServicePermission;

import java.util.List;

@RestController
@ResponseWrapper
@RequestMapping("/core/api/v1/accounts")
public class AccountController {
    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping("/open")
    @RequireServicePermission("core.account.open")
    public AccountService.AccountView open(
            @org.springframework.web.bind.annotation.RequestHeader(name = "X-Idempotency-Key", required = false) String requestId,
            @RequestBody OpenAccountRequest request) {
        return service.open(new AccountCommand(request.customerId(), request.phoneHash(), request.cccdHash(),
                request.cccd(), request.fullName(), request.currency()), requestId);
    }

    @GetMapping
    @RequireEntitlement("ACCOUNT_VIEW")
    public List<AccountService.AccountView> list() {
        return service.list(customerId());
    }

    @GetMapping("/{accountNumber}")
    @RequireEntitlement("ACCOUNT_VIEW")
    public AccountService.AccountView detail(@PathVariable String accountNumber) {
        return service.detail(customerId(), accountNumber);
    }

    @GetMapping("/{accountNumber}/balance")
    @RequireEntitlement("ACCOUNT_BALANCE_VIEW")
    public AccountService.BalanceView balance(@PathVariable String accountNumber) {
        return service.balance(customerId(), accountNumber);
    }

    private String customerId() {
        return AuthContextHolder.current()
                .map(context -> context.customerId())
                .filter(value -> value != null && !value.isBlank())
                .orElseThrow(UnauthorizedException::new);
    }

    public record OpenAccountRequest(String customerId, String phoneHash, String cccdHash,
                                     String cccd, String fullName, String currency) {
    }
}
