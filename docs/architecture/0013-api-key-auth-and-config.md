# ADR-0013 — API key authentication, configured per environment

## Status

Proposed (pending approval).

## Context

The read API ([Spec 6](../specs/06-public-api.md)) exposes aggregated personal data
(administrator/attorney names and the links between companies and people). In production it
must **not** be anonymous: access has to be gated to deter abuse and support the
data-controller obligations ([Spec 7](../specs/07-data-protection.md)). At the same time,
**local development must stay frictionless** — contributors should run the API without
managing credentials.

Configuration must follow **cloud-native / 12-factor** practice: all runtime config comes
from the environment, secrets are injected at run time (never baked into images or
committed), and behaviour differs **by configuration, not by build or code branch**.

## Decision

- The read API requires a valid **API key on every request in production**; a missing or
  invalid key returns `401 Unauthorized`. There are **no anonymous data endpoints** in
  production (health/readiness probes excepted).
- Authentication is **toggled by environment** and is **enabled by default** — i.e. **fail
  closed**: an unconfigured or misconfigured deployment denies access rather than opening up.
  It is disabled only in the explicit local/development environment, so local dev is
  anonymous and needs no key.
- Clients pass the key in a request header (`X-API-Key`). The set of accepted keys is read
  from an **environment variable / secret** (e.g. `MERCATOR_API_KEYS`, comma-separated),
  never from the repository or the image.
- The mechanism is **Micronaut environments** (e.g. `dev` vs `prod`) plus environment-variable
  property overrides; secrets are supplied by the platform (Docker/K8s secret, cloud secret
  manager) at deploy time. **The same artifact runs in every environment.**
- The names above (`X-API-Key`, `MERCATOR_API_KEYS`, environment identifiers) are the proposed
  defaults, to be finalised in implementation.

## Consequences

- Production is gated; local development is zero-config.
- No credentials in the repo, image, or `application.yml`; keys rotate by changing the
  secret/env — no code redeploy.
- The fail-closed default avoids the classic "auth accidentally disabled in prod" footgun.
- A shared API key is **coarse** — it authenticates the caller but gives no per-consumer
  identity or rate limiting. Acceptable for a small set of trusted consumers (the contracts
  project); it can evolve to per-consumer keys / quotas later without changing the model.
- Consumers (incl. the contracts project) must hold and send a key in production
  ([contracts-integration](../features/contracts-integration.md)).

## Alternatives considered

- **Anonymous public API** — rejected: personal data + GDPR + abuse exposure.
- **OAuth2 / JWT / an identity provider** — stronger and identity-aware, but heavier
  operationally than a few trusted consumers warrant for V1; revisit if the consumer set grows.
- **Network-only restriction (VPN / IP allowlist)** — brittle and location-bound; the
  contracts project may run elsewhere. An API key is portable, and the two can be combined later.
- **Config via build profiles / a separate image per environment** — violates 12-factor
  (config baked into the artifact); rejected in favour of one artifact + environment config.
