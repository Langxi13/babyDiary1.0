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
