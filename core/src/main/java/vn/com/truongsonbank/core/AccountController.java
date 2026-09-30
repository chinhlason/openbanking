package vn.com.truongsonbank.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import vn.com.truongsonbank.shared.response.ResponseWrapper;

import java.time.Instant;
import java.util.Map;

@RestController
@ResponseWrapper
class AccountController {
    private final CoreCustomerRepository customers;
    private final RestClient t29;

    AccountController(CoreCustomerRepository customers, CoreProperties properties) {
        this.customers = customers;
        this.t29 = RestClient.builder().baseUrl(properties.getT29BaseUrl()).build();
    }

    @PostMapping("/core/api/v1/accounts/open")
    OpenAccountResponse open(@RequestBody OpenAccountRequest request) {
        CoreCustomerEntity customer = customers.findById(request.customerId()).orElseGet(CoreCustomerEntity::new);
        customer.setCustomerId(request.customerId());
        customer.setPhoneHash(request.phoneHash());
        customer.setCccdHash(request.cccdHash());
        customer.setFullName(request.fullName());
        customer.setStatus("ACTIVE");
        customer.setUpdatedAt(Instant.now());
        if (customer.getCreatedAt() == null) {
            customer.setCreatedAt(customer.getUpdatedAt());
        }
        customers.save(customer);

        T29OpenAccountResponse response = t29.post()
                .uri("/accounts")
                .body(Map.of("cccd", request.cccd()))
                .retrieve()
                .body(T29OpenAccountResponse.class);
        return new OpenAccountResponse(response == null ? "" : response.accountNumber());
    }

    record OpenAccountRequest(String customerId, String phoneHash, String cccdHash, String cccd, String fullName) {
    }

    record OpenAccountResponse(String accountNumber) {
    }

    record T29OpenAccountResponse(String accountNumber) {
    }
}

@Entity
@Table(name = "core_customer")
class CoreCustomerEntity {
    @Id
    private String customerId;
    private String phoneHash;
    private String cccdHash;
    private String fullName;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public void setPhoneHash(String phoneHash) {
        this.phoneHash = phoneHash;
    }

    public String getCccdHash() {
        return cccdHash;
    }

    public void setCccdHash(String cccdHash) {
        this.cccdHash = cccdHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

@Repository
interface CoreCustomerRepository extends JpaRepository<CoreCustomerEntity, String> {
}
