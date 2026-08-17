# Security Policy

ThePatientGamerHelper is a single-user, offline-first Android app maintained
by one person in their spare time. There is no dedicated security team and
no SLA, but reports are taken seriously and looked at as soon as possible.

## Reporting a vulnerability

Please **do not** open a public issue for security vulnerabilities.

Instead, use GitHub's private reporting flow:
[Report a vulnerability](../../security/advisories/new) (Security tab →
"Report a vulnerability"). This opens a private advisory visible only to
the maintainer until a fix is ready.

If you can't use that flow, open a regular issue asking for an alternative
contact without including any vulnerability details.

## Scope

Things worth reporting: anything that could leak locally-stored review/
backlog data, bypass the Google Drive OAuth scope restrictions, or allow
arbitrary code execution via a crafted import file (Markdown/JSON/backup
ZIP/backlog export).

Things generally out of scope: issues that require a rooted/compromised
device, or reports about third-party services the app talks to (Google
Drive, TheGamesDB, HowLongToBeat) that aren't caused by this app's code.

## Supported versions

Only the latest published release is supported; older releases don't
receive backported fixes. See the
[Releases](../../releases) page for the current version.
