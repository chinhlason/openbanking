package vn.com.truongsonbank.client;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.com.truongsonbank.shared.response.ResponseWrapper;
import vn.com.truongsonbank.shared.sharding.TsbReplicatedTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
class ReplicatedConfigDemoController {
    private final TsbReplicatedTemplate replicatedTemplate;
    private final JdbcTemplate jdbcTemplate;

    ReplicatedConfigDemoController(TsbReplicatedTemplate replicatedTemplate, JdbcTemplate jdbcTemplate) {
        this.replicatedTemplate = replicatedTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void createTable() {
        replicatedTemplate.executeOnAll(jdbc -> jdbc.execute("""
                create table if not exists bank_config (
                    code varchar(64) primary key,
                    config_value varchar(512) not null
                )
                """));
    }

    @ResponseWrapper
    @PostMapping("/shared-test/sharding/replicated-config/{code}")
    Map<String, Object> upsert(@PathVariable String code, @RequestBody UpsertConfigRequest request) {
        replicatedTemplate.executeOnAll(jdbc -> jdbc.update("""
                        insert into bank_config (code, config_value)
                        values (?, ?)
                        on duplicate key update config_value = values(config_value)
                        """,
                code, request.value()));
        return Map.of("code", code, "value", request.value());
    }

    @ResponseWrapper
    @GetMapping("/shared-test/sharding/replicated-config/{code}")
    Map<String, Object> getDefault(@PathVariable String code) {
        return jdbcTemplate.queryForMap("select code, config_value from bank_config where code = ?", code);
    }

    @ResponseWrapper
    @GetMapping("/shared-test/sharding/replicated-config/{code}/verify")
    Map<String, Object> verify(@PathVariable String code) {
        List<Map<String, Object>> rows = new ArrayList<>();
        replicatedTemplate.executeOnAll(jdbc -> rows.add(jdbc.queryForMap(
                "select code, config_value from bank_config where code = ?", code)));
        return Map.of("copies", rows);
    }

    record UpsertConfigRequest(String value) {
    }
}
