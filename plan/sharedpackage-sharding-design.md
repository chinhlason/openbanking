# TruongSonBank Sharedpackage Data Sharding Design

Status: approved for implementation planning.

## 1. Goal

Add a data sharding module to `sharedpackage` for Spring Boot services using JPA/Hibernate and MySQL.

The module should let a domain service declare sharding rules on an entity with annotations, while datasource and runtime behavior are configured in `application.yml`.

Primary use case:

- Route an entity to the correct database shard.
- Rewrite the logical table name to the correct physical table.
- Support composite sharding: hash-based DB shard plus time-based table shard.

## 2. Approved Scope

Phase 1 supports:

- MySQL.
- Spring Data JPA / Hibernate.
- One operation routes to one database and one physical table.
- Hash sharding by `@ShardKey`.
- Time sharding by `@ShardTimeKey`.
- Replicated table annotation for small reference data that must exist on every datasource.
- Helper API to calculate table shards for a time range.
- Optional best-effort table auto-create by cloning the base table DDL.

Phase 1 does not support:

- Cross-shard transaction.
- Broadcast query.
- Automatic fan-out and result merge for range queries.
- Non-MySQL dialects.
- Replacing Flyway/Liquibase/DBA-managed schema migration.

## 3. Public API

Entity annotation:

```java
@Entity
@Table(name = "t_order")
@ShardEntity(
    db = 2,
    tb = 12,
    dbStrategy = ShardStrategy.HASH,
    tbStrategy = ShardStrategy.TIME,
    timeUnit = ShardTimeUnit.MONTH,
    tablePattern = "t_order_${yyyyMM}"
)
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ShardKey
    @Column(name = "user_id")
    private Long userId;

    @ShardTimeKey
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```

Annotations:

- `@ShardEntity`: declares DB/table shard counts and strategies.
- `@ShardKey`: marks the hash key field.
- `@ShardTimeKey`: marks the time key field.
- `@ReplicatedEntity`: marks an entity/table that is copied to every configured datasource.
- Optional method-param annotation can be added later if repository/query routing needs explicit shard key outside an entity.

Enums:

- `ShardStrategy.HASH`
- `ShardStrategy.TIME`
- `ShardTimeUnit.DAY`
- `ShardTimeUnit.MONTH`
- `ShardTimeUnit.YEAR`

## 4. Configuration

Example:

```yaml
tsb:
  shared:
    sharding:
      enabled: true
      auto-create-table: false
      datasources:
        shard-0:
          url: jdbc:mysql://localhost:3306/order_0
          username: app
          password: app
        shard-1:
          url: jdbc:mysql://localhost:3306/order_1
          username: app
          password: app
```

Rules:

- `enabled=false` by default.
- `auto-create-table=false` by default.
- Datasource keys map to calculated database shard ids.
- The module should fail fast if sharding is enabled but datasource config is missing.

## 5. Runtime Flow

Write flow:

1. Service saves an entity annotated with `@ShardEntity`.
2. Framework reads shard metadata from the entity class.
3. Framework reads `@ShardKey` value and calculates `dbIndex = hash(shardKey) % db`.
4. Framework reads `@ShardTimeKey` value and calculates physical table name from `tablePattern`.
5. Framework stores the routing result in `TsbShardContext`.
6. `AbstractRoutingDataSource` picks the target datasource.
7. Hibernate `StatementInspector` rewrites the logical table name to the physical table name.
8. If `auto-create-table=true`, framework checks/creates the physical table by cloning the base table DDL.

Read flow:

1. A query must have an active shard context.
2. If no shard context exists for a sharded entity query, fail fast.
3. The selected datasource and table name are applied the same way as write flow.

Range read:

- Framework does not fan out automatically.
- Framework provides helper API to calculate target table names for a time range.
- Domain code loops explicitly when it needs multiple physical tables.

## 6. Architecture

Package:

```text
vn.com.truongsonbank.shared.sharding
```

Main classes:

- `ShardEntity`, `ShardKey`, `ShardTimeKey`
- `ShardStrategy`, `ShardTimeUnit`
- `ShardingProperties`
- `TsbShardContext`
- `TsbShardResolver`
- `TsbShardingDataSource`
- `TsbShardingStatementInspector`
- `TsbShardTableHelper`
- `TsbShardTableInitializer`
- `ShardingAutoConfiguration`

Integration points:

- `AbstractRoutingDataSource` for database routing.
- Hibernate `StatementInspector` for table rewrite.
- Hibernate event listener or repository/service AOP for deriving shard context from entity writes.

Preferred implementation path:

1. Implement metadata and resolver.
2. Implement datasource routing.
3. Implement table rewrite.
4. Add optional table auto-create.
5. Add demo entity/controller in `client`.

## 7. Routing Rules

Hash DB shard:

```text
dbIndex = stableHash(shardKey) % db
datasource = datasources["shard-" + dbIndex]
```

Time table shard:

```text
tableName = tablePattern rendered from @ShardTimeKey
```

Example:

```text
base table: t_order
createdAt: 2026-09-28
tablePattern: t_order_${yyyyMM}
physical table: t_order_202609
```

If table strategy is hash:

```text
tableIndex = stableHash(shardKey) % tb
physical table = baseTable + "_" + tableIndex
```

## 8. Table Auto-Create

When `auto-create-table=true`:

1. Check whether the physical table exists.
2. If missing, run `SHOW CREATE TABLE <base_table>`.
3. Replace the table name in the DDL with the physical table name.
4. Execute the cloned DDL.
5. If creation fails, log a warning and continue startup.
6. If the application later uses the missing table, the DB error is returned normally.

This feature is best-effort for dev/demo and controlled environments. Production should normally use migration tooling.

## 9. Failure Rules

- Missing `@ShardKey` when DB strategy is hash: fail fast.
- Missing `@ShardTimeKey` when table strategy is time: fail fast.
- Missing datasource for calculated shard: fail fast.
- Sharded query without active shard context: fail fast.
- More than one shard in a single transaction: not supported by framework.

## 10. Observability

Add metrics:

- `tsb.sharding.resolve.duration`
- `tsb.sharding.route.count`
- `tsb.sharding.table.create.count`

Tags:

- `entity`
- `databaseShard`
- `table`
- `outcome`

Add trace attributes when tracing is available:

- `tsb.sharding.entity`
- `tsb.sharding.database_shard`
- `tsb.sharding.table`

## 11. Replicated Tables

Use `@ReplicatedEntity` for small, mostly-read reference/config tables that must be available from every datasource.

Example:

```java
@Entity
@Table(name = "bank_config")
@ReplicatedEntity
public class BankConfigEntity {
    @Id
    private String code;

    private String value;
}
```

Behavior:

- Read: route to the current shard datasource if a shard context exists; otherwise use the primary/default datasource.
- Write: write to every configured datasource.
- Write failure on any datasource fails the operation.
- No cross-datasource transaction guarantee in phase 1.
- Intended for low-volume reference data only.

Validation:

- `@ReplicatedEntity` cannot be combined with `@ShardEntity`.
- A replicated entity does not need `@ShardKey` or `@ShardTimeKey`.

Recommended use cases:

- bank/system config
- lookup code tables
- feature flags
- static reference catalog

Non-goals:

- customer/account/order/transaction data
- high-write tables
- strong distributed transaction semantics

## 12. Demo Plan

In `client`, add a demo entity:

```java
@Entity
@Table(name = "t_order")
@ShardEntity(
    db = 1,
    tb = 12,
    dbStrategy = ShardStrategy.HASH,
    tbStrategy = ShardStrategy.TIME,
    timeUnit = ShardTimeUnit.MONTH,
    tablePattern = "t_order_${yyyyMM}"
)
class ShardedOrderEntity {
    @ShardKey
    private Long userId;

    @ShardTimeKey
    private LocalDateTime createdAt;
}
```

Demo endpoints:

- `POST /shared-test/sharding/orders`
- `GET /shared-test/sharding/orders/{id}?userId=...&createdAt=...`
- `GET /shared-test/sharding/tables?from=...&to=...`

Expected behavior:

- API uses logical entity.
- DB stores rows in physical monthly table.
- Helper endpoint returns table names for the requested range.

## 13. Open Items For Implementation

- Decide whether entity writes derive shard context through Hibernate event listener or service/repository AOP.
- Decide exact SQL rewrite approach for quoted table names and aliases.
- Decide whether demo should use one database with table sharding only or two MySQL databases.
- Decide whether replicated writes are implemented by repository AOP or a helper service in phase 1.
