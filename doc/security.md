# Security

## Description

For the moment being, security is limited to Authentication, there is no Authorization in place.
In order for it to work we need a OpenID / OAuth 2.0 capable server, ex Keycloak.

## Configuration

By default, the Security is disabled, in order to enable it, the entry `hapi.fhir.security` in the
`application.yaml` must be set to true, or the environment variable `SECURITY_ENABLED` must be set to true.
The following should be

```yaml
    security:
      enabled: ${SECURITY_ENABLED:false}
      server: ${SECURITY_SERVER:http://localhost:8181/realms/clinomic}
      jwks: ${SECURITY_JWKS:http://localhost:8181/realms/clinomic/protocol/openid-connect/certs}
```