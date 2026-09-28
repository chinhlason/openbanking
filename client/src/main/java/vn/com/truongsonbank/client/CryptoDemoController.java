package vn.com.truongsonbank.client;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.truongsonbank.shared.crypto.TsbCryptoService;
import vn.com.truongsonbank.shared.response.ResponseWrapper;

import java.util.Map;

@RestController
class CryptoDemoController {
    private final CryptoCustomerRepository repository;
    private final TsbCryptoService cryptoService;
    private final JdbcTemplate jdbcTemplate;

    CryptoDemoController(CryptoCustomerRepository repository,
                         TsbCryptoService cryptoService,
                         JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void createTable() {
        jdbcTemplate.execute("""
                create table if not exists crypto_customer_demo (
                    id bigint primary key auto_increment,
                    cccd varchar(512),
                    cccd_hash varchar(256),
                    full_name varchar(512),
                    index idx_crypto_customer_demo_cccd_hash (cccd_hash)
                )
                """);
    }

    @ResponseWrapper
    @PostMapping("/shared-test/crypto/customers")
    Map<String, Object> create(@RequestBody CreateCustomerRequest request) {
        CryptoCustomerEntity entity = new CryptoCustomerEntity();
        entity.setCccd(request.cccd());
        entity.setFullName(request.fullName());
        CryptoCustomerEntity saved = repository.save(entity);
        return toResponse(saved);
    }

    @ResponseWrapper
    @GetMapping("/shared-test/crypto/customers/{id}")
    Map<String, Object> get(@PathVariable Long id) {
        return toResponse(repository.findById(id).orElseThrow());
    }

    @ResponseWrapper
    @GetMapping("/shared-test/crypto/customers/search")
    Map<String, Object> search(@RequestParam String cccd) {
        String hash = cryptoService.hashForLookup(cccd);
        return toResponse(repository.findByCccdHash(hash).orElseThrow());
    }

    @ResponseWrapper
    @GetMapping("/shared-test/crypto/customers/{id}/raw")
    Map<String, Object> raw(@PathVariable Long id) {
        return jdbcTemplate.queryForMap(
                "select id, cccd, cccd_hash, full_name from crypto_customer_demo where id = ?",
                id);
    }

    private Map<String, Object> toResponse(CryptoCustomerEntity entity) {
        return Map.of(
                "id", entity.getId(),
                "cccd", entity.getCccd(),
                "cccdHash", entity.getCccdHash(),
                "fullName", entity.getFullName());
    }

    record CreateCustomerRequest(String cccd, String fullName) {
    }
}
