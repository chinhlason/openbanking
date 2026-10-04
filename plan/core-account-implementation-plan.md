# Core Account Implementation Plan

## 1. Goal

Build the core/account module to:

- Open an account for a customerId.
- Store the customerId -> accountNumber mapping in Core.
- Query account information and balances.
- Ensure onboarding retries do not create duplicate accounts.
- Keep T29 as a mock core banking system and the source of truth for balances in the initial phase.

Do not store PINs, credentials, Keycloak subjects, or plaintext CCCD numbers in the Core database.

## Implementation status

Phases 1-3 have been implemented for local development:

- Split `core/account` into a controller, application service, domain errors/status, persistence adapter, and T29 adapter.
- Created the JPA tables `core_customer`, `core_account`, and `core_account_operation` in `coredb`.
- Added idempotency across Onboarding, Core, and T29 using `X-Idempotency-Key`.
- Added list/detail/balance APIs, signed auth context, and ownership checks.
- Added the `/bff/api/core/**` route and configured the internal secret for onboarding.
- Updated sharedpackage so a missing internal-auth header returns a standard 401 response.

An integration smoke test confirmed that opening an account returns an account number, replaying the same idempotency key does not create a second account, and Core/BFF health endpoints return 200. Transfer/payment and reconciliation phases remain deferred.

## 2. Ownership

~~~text
customer domain
  owns full customer profile and customer_account_links

auth
  owns Keycloak subject, session and device

core/account
  owns account lifecycle, account ownership projection and idempotency

t29
  owns mock account number and balance in phase 1
~~~

customerId is the business key linking an account to a customer. Core does not read the Customer or Auth databases directly; the minimum customer data is sent in an internal command when opening an account.

## 3. Refactor package structure

Refactor the current AccountController into a hexagonal structure:

~~~text
core/src/main/java/vn/com/truongsonbank/core/account/
  adapter/in/web/
    AccountController.java
    AccountRequest.java
    AccountResponse.java
  application/
    AccountService.java
    AccountCommand.java
  domain/
    Account.java
    AccountStatus.java
    AccountErrors.java
  infrastructure/
    persistence/
      CoreCustomerEntity.java
      CoreAccountEntity.java
      AccountOperationEntity.java
      repositories
    t29/
      T29AccountClient.java
~~~

The T29 client must sit behind a port/adapter. The controller must not call RestClient directly.

## 4. Data model

### core_customer

Keep the existing minimal snapshot:

~~~text
customer_id       PK
phone_hash        nullable
cccd_hash         nullable
full_name         nullable
status
created_at
updated_at
~~~

Do not store plaintext phone numbers or CCCD numbers.

### core_account

~~~text
id                     BIGINT/UUID PK
customer_id            VARCHAR NOT NULL
account_number         VARCHAR NOT NULL UNIQUE
currency               VARCHAR NOT NULL DEFAULT 'VND'
status                 VARCHAR NOT NULL
t29_account_reference  VARCHAR NULL
opened_at              TIMESTAMP NOT NULL
closed_at              TIMESTAMP NULL
created_at             TIMESTAMP NOT NULL
updated_at             TIMESTAMP NOT NULL
~~~

Constraints:

- Unique account_number.
- Index customer_id and status.
- Do not make customer_id unique, because a customer may have multiple accounts later.
- Phase 1 may limit each customer to one active account through an application rule.

### core_account_operation

Idempotency table for the open-account command:

~~~text
id
request_id
operation_type
customer_id
account_id
status
response_payload
failure_code
created_at
updated_at
~~~

Unique key:

~~~text
UNIQUE(operation_type, request_id)
~~~

The same requestId must return the stored response without calling T29 a second time.

## 5. API contract

### 5.1 Internal open account

~~~http
POST /core/api/v1/accounts/open
X-Internal-Core-Secret: local-core-internal-secret
X-Idempotency-Key: onboarding-{id}
~~~

Internal request:

~~~json
{
  "customerId": "cus_123",
  "phoneHash": "...",
  "cccdHash": "...",
  "cccd": "mock-only-cccd",
  "fullName": "NGUYEN VAN A",
  "currency": "VND"
}
~~~

The CCCD number is used temporarily to call the T29 mock; do not persist or log it.

Response:

~~~json
{
  "accountId": 123,
  "accountNumber": "100000001",
  "customerId": "cus_123",
  "currency": "VND",
  "status": "ACTIVE"
}
~~~

### 5.2 List accounts

~~~http
GET /core/api/v1/accounts
~~~

Read customerId from the signed auth context forwarded by BFF. Do not trust a customerId supplied in the query or request body.

### 5.3 Account detail

~~~http
GET /core/api/v1/accounts/{accountNumber}
~~~

Return an account only if it belongs to the customer in the verified auth context.

### 5.4 Balance

~~~http
GET /core/api/v1/accounts/{accountNumber}/balance
~~~

Core checks ownership and then calls T29:

~~~http
GET /accounts/{accountNumber}/balance
~~~

Do not cache balances in phase 1 because transactions can change them.

## 6. Open account flow

~~~text
Customer domain
  -> Core: open(customerId, idempotencyKey)
  -> Core DB: insert operation PENDING
  -> Core DB: check active account
  -> T29: POST /accounts with idempotency key
  -> Core DB: save core_account and mark SUCCESS
  -> Customer domain: accountId and accountNumber
~~~

Retry rules:

- Operation SUCCESS: return the stored response.
- Operation PENDING: check its status before calling T29 again.
- T29 timeout after it may have created an account: do not retry blindly; use T29 idempotency or reconciliation.
- T29 fails definitively before creating an account: mark FAILED and allow retries according to policy.

## 7. T29 adapter requirements

Update the T29 mock in phase 1:

- POST /accounts accepts X-Idempotency-Key.
- The same key returns the same account number.
- Add a test proving an account is not created twice.
- Keep balances in memory and document that state is lost when the container restarts.
- Core must not depend on T29's response format outside the adapter.

## 8. Security and entitlement

Operation catalog:

~~~text
ACCOUNT_OPEN
ACCOUNT_VIEW
ACCOUNT_BALANCE_VIEW
ACCOUNT_CLOSE
~~~

Rules:

1. BFF reads the Redis session and service package metadata.
2. BFF signs AuthContext and forwards it.
3. Core verifies the signature through sharedpackage.
4. Core checks the entitlement.
5. Core checks account.customerId == authContext.customerId.
6. A client cannot supply or replace the customer identity header.

Example:

~~~java
@RequireEntitlement("ACCOUNT_VIEW")
@GetMapping("/{accountNumber}")
~~~

## 9. Observability

Metrics:

~~~text
tsb.account.open.total{outcome}
tsb.account.open.duration{outcome}
tsb.account.balance.lookup.total{outcome}
tsb.account.t29.request.duration{operation,outcome}
tsb.account.idempotency.replay.total{operation}
~~~

Trace spans:

~~~text
core.account.open
core.account.t29.open
core.account.balance
core.account.t29.balance
~~~

Logs include traceId, requestId, operation, and outcome. Mask/hash customerId and accountNumber. Do not log plaintext CCCD numbers.

## 10. Implementation phases

### Phase 1: Foundation

- Create the hexagonal core.account package.
- Extract the T29 adapter.
- Create CoreAccountEntity and its repository.
- Create the migration/schema.
- Keep the open endpoint compatible with onboarding.

### Phase 2: Idempotent open

- Add X-Idempotency-Key.
- Create the operation table and unique constraint.
- Onboarding passes its onboarding ID as the idempotency key.
- Test retries across Auth/Core/T29.

### Phase 3: Query accounts and balances

- List/detail accounts.
- Add the balance endpoint through T29.
- Check ownership from the signed auth context.
- Add ACCOUNT_VIEW and ACCOUNT_BALANCE_VIEW to the entitlement catalog.

### Phase 4: Integration test

- Onboarding creates a customer, account, and account link.
- Log in again and request the balance through BFF.
- A different customer receives 403.
- Retrying with the same idempotency key does not create a second account.
- Restart T29 and confirm mock balances are lost, as documented for phase 1.

### Phase 5: Prepare for transfer/payment

- Standardize the T29 transfer contract with an idempotency key.
- Create a money-transaction state machine.
- Do not implement direct debit/credit in the account controller.

## 11. Acceptance criteria

- Retrying onboarding does not create a duplicate account.
- Customers can query their own accounts.
- A different customer receives ACCOUNT_ACCESS_DENIED.
- Balances come from T29, not Client.
- The Core database does not store plaintext CCCD numbers.
- Mobile and portal call Core through BFF.
- Requests carry trace, metric, and signed entitlement context.
- A T29 timeout does not trigger a blind retry that creates a duplicate account.

## 12. Deferred decisions

- A persistent database for T29 instead of RAM.
- A double-entry ledger as the source of truth for balances.
- Multiple currencies and account types.
- Account closure/freezing.
- A reconciliation job between Core and T29.
- Transfer/payment and transaction MFA.
