package vn.com.truongsonbank.client;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.truongsonbank.shared.response.ResponseWrapper;
import vn.com.truongsonbank.shared.sharding.TsbReplicatedTemplate;
import vn.com.truongsonbank.shared.sharding.TsbShardTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
class ShardingDemoController {
    private final ShardedOrderRepository repository;
    private final TsbShardTemplate shardTemplate;
    private final TsbReplicatedTemplate replicatedTemplate;
    private final JdbcTemplate jdbcTemplate;

    ShardingDemoController(ShardedOrderRepository repository,
                           TsbShardTemplate shardTemplate,
                           TsbReplicatedTemplate replicatedTemplate,
                           JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.shardTemplate = shardTemplate;
        this.replicatedTemplate = replicatedTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void createBaseTable() {
        replicatedTemplate.executeOnAll(jdbc -> jdbc.execute("""
                create table if not exists t_order (
                    id bigint primary key auto_increment,
                    user_id bigint not null,
                    created_at datetime not null,
                    amount bigint not null,
                    index idx_t_order_user_id (user_id),
                    index idx_t_order_created_at (created_at)
                )
                """));
    }

    @ResponseWrapper
    @PostMapping("/shared-test/sharding/orders")
    Map<String, Object> create(@RequestBody CreateOrderRequest request) {
        ShardedOrderEntity entity = new ShardedOrderEntity();
        entity.setUserId(request.userId());
        entity.setCreatedAt(LocalDateTime.parse(request.createdAt()));
        entity.setAmount(request.amount());
        return toResponse(repository.save(entity));
    }

    @ResponseWrapper
    @GetMapping("/shared-test/sharding/orders/{id}")
    Map<String, Object> get(@PathVariable Long id,
                            @RequestParam Long userId,
                            @RequestParam String createdAt) {
        LocalDateTime time = LocalDateTime.parse(createdAt);
        return shardTemplate.execute(ShardedOrderEntity.class, userId, time,
                () -> toResponse(repository.findById(id).orElseThrow()));
    }

    @ResponseWrapper
    @GetMapping("/shared-test/sharding/orders/{id}/raw")
    Map<String, Object> raw(@PathVariable Long id,
                            @RequestParam Long userId,
                            @RequestParam String createdAt) {
        LocalDateTime time = LocalDateTime.parse(createdAt);
        String table = shardTemplate.tablesForRange(ShardedOrderEntity.class, time, time).getFirst();
        return shardTemplate.execute(ShardedOrderEntity.class, userId, time,
                () -> jdbcTemplate.queryForMap("select id, user_id, created_at, amount from " + table + " where id = ?", id));
    }

    @ResponseWrapper
    @GetMapping("/shared-test/sharding/tables")
    Map<String, Object> tables(@RequestParam String from, @RequestParam String to) {
        return Map.of("tables", shardTemplate.tablesForRange(
                ShardedOrderEntity.class,
                LocalDateTime.parse(from),
                LocalDateTime.parse(to)));
    }

    private Map<String, Object> toResponse(ShardedOrderEntity entity) {
        return Map.of(
                "id", entity.getId(),
                "userId", entity.getUserId(),
                "createdAt", entity.getCreatedAt().toString(),
                "amount", entity.getAmount());
    }

    record CreateOrderRequest(Long userId, String createdAt, Long amount) {
    }
}
