package vn.com.truongsonbank.core.account.infrastructure.t29;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.com.truongsonbank.core.CoreProperties;
import vn.com.truongsonbank.core.account.domain.AccountErrors;
import vn.com.truongsonbank.shared.exception.BusinessException;

import java.math.BigDecimal;

@Component
public class T29AccountClient {
    private final RestClient client;

    public T29AccountClient(CoreProperties properties) {
        this.client = RestClient.builder().baseUrl(properties.getT29BaseUrl()).build();
    }

    public String open(String cccd, String idempotencyKey) {
        T29OpenAccountResponse response = client.post()
                .uri("/accounts")
                .header("X-Idempotency-Key", idempotencyKey)
                .body(new T29OpenAccountRequest(cccd))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response1) -> {
                    throw new BusinessException(AccountErrors.DOWNSTREAM_FAILURE);
                })
                .body(T29OpenAccountResponse.class);
        if (response == null || response.accountNumber() == null || response.accountNumber().isBlank()) {
            throw new BusinessException(AccountErrors.DOWNSTREAM_FAILURE);
        }
        return response.accountNumber();
    }

    public Balance balance(String accountNumber) {
        Balance response = client.get()
                .uri("/accounts/{accountNumber}/balance", accountNumber)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response1) -> {
                    throw new BusinessException(AccountErrors.DOWNSTREAM_FAILURE);
                })
                .body(Balance.class);
        if (response == null) {
            throw new BusinessException(AccountErrors.DOWNSTREAM_FAILURE);
        }
        return response;
    }

    public record Balance(String accountNumber, String cccd, BigDecimal balance) {
    }

    private record T29OpenAccountRequest(String cccd) {
    }

    private record T29OpenAccountResponse(String accountNumber) {
    }
}
