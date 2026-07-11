# Security Policy

## Supported Version

Security fixes are applied to the latest commit on `main`.

## Reporting a Vulnerability

Please use GitHub's private vulnerability reporting feature from the repository Security tab. Do not open a public issue containing credentials, private diary content, user images, server addresses, database exports, or exploit details.

Include the affected component, reproduction steps, expected impact, and any suggested mitigation. Reports will be reviewed before public disclosure.

## Deployment Responsibilities

- Replace every placeholder in `.env.example` with private values.
- Keep production environment files outside the repository with restrictive permissions.
- Use a dedicated database account instead of `root`.
- Restrict CORS to trusted origins and keep Redis/MySQL bound to private interfaces.
- Rotate a credential immediately if it is ever committed, logged, or otherwise exposed.
- Back up and protect uploaded images and database dumps as personal data.

## Security Boundaries

- The browser uses 15-minute access tokens and an HttpOnly rotating refresh cookie. Production must keep `AUTH_SECURE_COOKIE=true` and terminate TLS before the application.
- Email-verification and password-reset tokens are stored as hashes, locked and consumed exactly once inside the account update transaction.
- Locked diaries require a short-lived password step-up token. Their content, tags, mood, media, search index, insights, sync payloads, and AI inputs are redacted until verification succeeds; compatibility list, draft, album, photo, and export endpoints omit locked entries rather than bypassing step-up.
- V2 rich media is stored outside the web root and delivered through expiring signed URLs. `DIARY_OBJECT_PATH` must never be placed below `DIARY_FILE_PATH`.
- Legacy `/images/**` URLs remain publicly readable for backward compatibility. Their generated names are difficult to guess but are not authorization. Treat this endpoint as a compatibility boundary and use V2 media for new sensitive uploads.
- Private share URLs are bearer secrets. Use short expiration times, optional passwords, view limits, and revoke links that are no longer needed.
- Swagger/OpenAPI is disabled by default in production. Enable `SPRINGDOC_ENABLED=true` only on a restricted administrative network.
- The application trusts `X-Forwarded-For` only from a loopback proxy. Keep Nginx and the backend on the same trusted host or adjust proxy handling before using a different topology.

## Sensitive Data

Database rows, object storage, legacy images, exports, backups, AI prompts, email tokens, refresh cookies, recovery codes, and VAPID/S3/SMTP credentials all contain or protect private data. Encrypt backups at rest, restrict filesystem permissions, and avoid sending real diary content to an AI provider without the users' informed consent.
