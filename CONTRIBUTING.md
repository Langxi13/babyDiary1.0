# Contributing

## Development Workflow

1. Create a focused branch from `main`.
2. Copy `.env.example` to `.env` and use local-only credentials.
3. Keep changes scoped and add tests for behavior changes.
4. Run `scripts/verify.sh` before opening a pull request.
5. Describe user-visible changes, migrations, and deployment considerations in the pull request.

## Privacy Checklist

Before committing, verify that the change does not contain:

- Production domains, IP addresses, filesystem layouts, usernames, email addresses, or account identifiers
- Passwords, invitation codes, JWT secrets, encryption keys, API keys, certificates, or environment files
- Real diary text, image filenames, uploaded photos, database dumps, logs, or backup paths
- Screenshots or fixtures derived from private user data

Use `example.com`, localhost addresses, generic usernames, and clearly marked placeholders in examples.

## Tests

```bash
npm --prefix frontend ci
scripts/verify.sh
```

Backend-only changes may also be checked with `mvn -f backend/pom.xml clean test`. Frontend-only changes may be checked with `npm --prefix frontend run build` and the Node test command used by `scripts/verify.sh`.

## Database Changes

Add schema changes as a new Flyway migration under `backend/src/main/resources/db/migration`. Do not edit an already released migration unless the repository has not yet published that migration.
