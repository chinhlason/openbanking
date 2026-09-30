# Backend Hexagonal Folder Convention

All future backend bounded contexts should follow this folder shape so each block can later be split into a microservice with minimal reshuffling.

Example:

```text
<context>/
  adapter/
    in/
      web/
  application/
  domain/
  infrastructure/
    persistence/
    keycloak/
    security/
  config/
```

Current `auth` module context:

```text
authentication/
  adapter/in/web        # REST controller + request/response DTOs
  application           # use-case services
  domain                # domain records/state
  infrastructure        # DB, Keycloak, DPoP/security integration
  config                # Spring/config properties
```

Keep behavior and API paths stable during folder refactors. Add ports/interfaces only when there are at least two real implementations or a real split boundary.

Java package names should mirror the folder path so IntelliJ can resolve packages without manual source-root fixes.
