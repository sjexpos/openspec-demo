---
name: owasp-security-audit
description: Use when performing a cybersecurity audit, security review, OWASP Top 10 compliance check, vulnerability assessment, or preparing for a penetration test on a Node.js/Express/React application.
---

# OWASP Top 10 Security Audit

## Overview

Systematic methodology for auditing web applications against the OWASP Top 10:2021. Combines automated tooling with manual code review, produces a prioritized remediation plan with verification steps and CI/CD integration guidance.

**Core principle:** Every finding must be verified with tooling or code evidence, prioritized by exploitability, and paired with a concrete fix the agent can implement.

## When to Use

- Cybersecurity audit or security review request
- OWASP Top 10 compliance assessment
- Pre-release security gate or penetration test preparation
- Post-incident security hardening
- Dependency vulnerability triage

**When NOT to use:**
- Quick fix for a single known vulnerability (just fix it)
- General code quality review (use `code-auditing` skill instead)
- Infrastructure/cloud security review (out of scope - this covers application layer)

## Audit Methodology

### Phase 0: Environment Setup and Automated Scans

Run automated tools FIRST - they catch low-hanging fruit before manual review.

**Required scans (execute all):**

| Tool | Command | Covers |
|------|---------|--------|
| npm audit | `npm audit --json` | A06: Known CVEs in dependencies |
| ESLint security | `npx eslint --plugin security .` | A03, A05: Code-level vulnerabilities |
| Outdated check | `npm outdated` | A06: Outdated packages |
| Secret scan | `rg -i '(password\|secret\|api_key\|token)\s*[:=]' --glob '!node_modules' --glob '!*.lock'` | A02: Hardcoded secrets |
| .gitignore check | Verify `.env`, `*.pem`, `*.key` are in `.gitignore` | A02: Committed secrets |
| Git history secrets | `git log --all --diff-filter=A -- '*.env' '*.pem' '*.key'` | A02: Secrets in git history |
| Debug/telemetry code | `rg 'fetch\(.*127\.0\.0\.1\|localhost:[0-9]{4}' --glob '*.{ts,js,jsx,tsx}'` | A04: Dev-only outbound requests |

**Record baseline metrics:** Total vulnerabilities by severity, outdated dependency count, secret scan hits.

### Phase 1: Systematic Category Audit

Audit EVERY category using the checklist in the Quick Reference section. Do not skip categories even if they seem irrelevant - document "N/A" with justification.

For each category:
1. Run the specific checks listed in the checklist
2. Record findings with file path, line number, and severity
3. Note what you checked even if clean (proves thoroughness)

### Phase 2: Findings Classification

Rate each finding using this severity matrix:

| Severity | Criteria | Example |
|----------|----------|---------|
| **Critical** | Exploitable remotely, no auth required, data breach likely | Hardcoded DB credentials in git, zero authentication |
| **High** | Exploitable with some effort, significant impact | Missing security headers, no rate limiting, IDOR |
| **Medium** | Requires specific conditions, moderate impact | Outdated dependencies without known exploits, weak validation |
| **Low** | Minimal impact or unlikely exploitation | Missing CSP fine-tuning, verbose error messages in dev |

### Phase 3: Prioritized Remediation Plan

Group fixes into implementation phases:

**Phase A - Immediate (< 1 day, critical/high):**
- Rotate exposed credentials
- Add authentication middleware
- Install and configure Helmet
- Add rate limiting
- Fix `.gitignore` and purge secrets from git history

**Phase B - Short-term (1-3 days, high/medium):**
- Implement RBAC authorization
- Add input sanitization (xss/DOMPurify)
- Configure structured logging
- Set body size limits
- Add CSRF protection

**Phase C - Medium-term (1-2 weeks, medium/low):**
- Upgrade outdated dependencies
- Add CI/CD security pipeline
- Implement audit logging
- Add security monitoring/alerting

Each fix must include: what to change, where, a code example, and how to verify it works.

### Phase 4: Verification and CI/CD Integration

For each remediation, define a verification step:
- Unit test that validates the security control
- curl command that proves the vulnerability is fixed
- CI pipeline check that prevents regression

## Quick Reference: OWASP Top 10 Audit Checklist

### A01: Broken Access Control

**Step 1: Enumerate all routes first.** Run `rg 'router\.(get|post|put|patch|delete)' --glob '*.ts'` and list every endpoint. Then verify EACH has auth middleware.

| Check | How | Severity if missing |
|-------|-----|-------------------|
| Authentication middleware on ALL routes | Enumerate all routes, verify each has auth middleware in chain | Critical |
| RBAC / role-based authorization | Check for role checks before data access | Critical |
| IDOR protection | Verify resource ownership checks (e.g., `where: { id, userId }`) | High |
| CORS configuration | Check `cors()` options - no wildcard in production | High |
| Serverless CORS vs Express CORS | Compare `serverless.yml` CORS with Express CORS config | Medium |
| CSRF protection | Check for `csurf` or double-submit cookie pattern | Medium |

### A02: Cryptographic Failures

| Check | How | Severity if missing |
|-------|-----|-------------------|
| No hardcoded secrets | `rg '(password\|secret\|key)\s*[:=]\s*["\x27]' --glob '!*.lock'` | Critical |
| `.env` in `.gitignore` | `rg '\.env' .gitignore` — verify NOT commented out | Critical |
| Secrets in git history | `git log --all --diff-filter=A -- '*.env' '*.pem'` — if found, recommend `bfg-repo-cleaner` purge | Critical |
| Prisma uses `env("DATABASE_URL")` | Check `schema.prisma` datasource block — no inline connection string | Critical |
| HTTPS enforcement | Check for `https` redirects or HSTS headers | High |
| PII field filtering | Check API responses for unnecessary sensitive fields | Medium |
| Password hashing (if auth exists) | Verify bcrypt/argon2, not SHA/MD5 | Critical |

### A03: Injection

| Check | How | Severity if missing |
|-------|-----|-------------------|
| No raw SQL | `rg '\$(queryRaw\|executeRaw)\|rawQuery' --glob '*.ts'` | Critical |
| Parameterized queries (Prisma/ORM) | Verify all DB access through ORM, no string concatenation | Critical |
| Input validation on all endpoints | Check every route handler has validation before DB ops | High |
| File upload filename sanitization | Check multer/upload config for `originalname` usage | High |
| Sort/filter field allowlists | Verify user-supplied field names checked against allowlist | Medium |
| No `eval()` or `Function()` | `rg 'eval\(\|new Function\(' --glob '*.{ts,js}'` | Critical |
| No template literal injection in logs | Check log statements for unsanitized user input | Low |
| Mass assignment prevention | Verify `req.body` is NOT spread directly into Prisma `create`/`update` — use explicit field allowlists | High |

### A04: Insecure Design

| Check | How | Severity if missing |
|-------|-----|-------------------|
| Request body size limits | Check `express.json({ limit: ... })` | Medium |
| File upload size/type restrictions | Check multer config for `limits` and `fileFilter` | High |
| File upload path traversal | Verify upload destination is absolute, filename is sanitized | High |
| Validation not bypassable | Check validators cannot be skipped (e.g., with extra fields) | High |
| No debug/telemetry endpoints in production | `rg 'fetch\(.*127\.0\.0\.1\|localhost:[0-9]' --glob '*.{ts,js,jsx}'` | High |
| Error responses don't leak internals | Verify 500 errors return generic messages | Medium |

### A05: Security Misconfiguration

| Check | How | Severity if missing |
|-------|-----|-------------------|
| Helmet.js installed and configured | Check `package.json` for `helmet`, `index.ts` for `app.use(helmet())` | High |
| `x-powered-by` disabled | `app.disable('x-powered-by')` or Helmet handles it | Low |
| Rate limiting | Check for `express-rate-limit` or equivalent | High |
| Strict CORS (no wildcard) | Verify `origin` is not `*` or `true` | High |
| Environment variable validation | Check for startup validation of required env vars | Medium |
| No default credentials | Check seed files, test configs for hardcoded passwords | Medium |
| HTTP parameter pollution (HPP) | Check for `hpp` middleware or manual prevention | Low |
| `trust proxy` configured (if behind LB) | Check `app.set('trust proxy', ...)` for Lambda/ALB | Medium |
| CSP for React SPA | Verify `Content-Security-Policy` header restricts `script-src`, `style-src`, `connect-src` | High |
| No inline `<script>` in public HTML | Check `public/index.html` for inline scripts or event handlers | Medium |

### A06: Vulnerable and Outdated Components

| Check | How | Severity if missing |
|-------|-----|-------------------|
| `npm audit` clean | Run `npm audit --json`, count critical/high | Varies |
| Node.js runtime not EOL | Check `engines` field, Lambda runtime version | Medium |
| No deprecated packages | Run `npm outdated`, check for major version gaps | Low |
| Lock file exists and committed | Verify `package-lock.json` is in git | Medium |
| TypeScript version current | Check `package.json` TypeScript version | Low |
| No suspicious `postinstall` scripts | Check dependencies for `preinstall`/`postinstall` scripts: `rg '"preinstall\|postinstall"' node_modules/*/package.json \| head -20` | Medium |
| No typosquatting risk | Spot-check unusual or less-known package names against npm registry | Low |

### A07: Identification and Authentication Failures

| Check | How | Severity if missing |
|-------|-----|-------------------|
| Auth mechanism exists | `rg 'jwt\|jsonwebtoken\|passport\|auth\|session' --glob '*.ts' -i` | Critical |
| Password policy enforced | Check password validation (length, complexity) | High |
| Account lockout after failed attempts | Check for brute-force protection | High |
| Session/token expiration | Verify JWT expiry or session timeout | High |
| Secure cookie flags | Check `httpOnly`, `secure`, `sameSite` flags | Medium |

### A08: Software and Data Integrity Failures

| Check | How | Severity if missing |
|-------|-----|-------------------|
| HTML sanitization on text inputs | Check for `xss`, `sanitize-html`, or `DOMPurify` usage | Medium |
| No `dangerouslySetInnerHTML` without sanitization | `rg 'dangerouslySetInnerHTML' --glob '*.{tsx,jsx}'` | High |
| URL sanitization in `href`/`src` attributes | Check for `javascript:` protocol filtering | High |
| Prototype pollution prevention | Check for `Object.freeze`, `--disable-proto` flag, or `hpp` | Medium |
| Lock file integrity | Verify `package-lock.json` integrity hashes | Low |

### A09: Security Logging and Monitoring Failures

| Check | How | Severity if missing |
|-------|-----|-------------------|
| Structured logging (not console.log) | Check for `winston`, `pino`, or structured logger | High |
| Audit trail for CRUD operations | Verify create/update/delete actions are logged with actor | High |
| Request logging middleware order | Verify logger is BEFORE route handlers | Medium |
| No PII in error logs | Check error handlers for data leakage | Medium |
| Log injection prevention | Verify user input is not interpolated into log templates | Low |
| Failed auth attempt logging | Verify 401/403 responses are logged | Medium |

### A10: Server-Side Request Forgery (SSRF)

| Check | How | Severity if missing |
|-------|-----|-------------------|
| No outbound requests from user input | `rg 'fetch\(\|axios\.\|http\.request' --glob 'backend/**/*.ts'` | High |
| URL allowlist for external calls | Verify outbound URLs are validated against allowlist | High |
| No user-controlled redirect URLs | Check redirect endpoints for open redirect | Medium |
| File operations use absolute paths | Verify no `path.join(userInput)` without validation | Medium |

## Serverless/Lambda Specific Checks

If the project deploys to AWS Lambda (or similar FaaS), also check:

| Check | How | Severity if missing |
|-------|-----|-------------------|
| Lambda runtime not EOL | Check `serverless.yml` or `template.yaml` runtime version | Medium |
| IAM permissions least-privilege | Verify Lambda role has minimal permissions, not `*` | High |
| API Gateway CORS matches Express CORS | Compare gateway-level CORS with application-level | High |
| Environment variables encrypted at rest | Verify sensitive values use KMS encryption | Medium |
| Function timeout configured | Check for reasonable timeout to prevent resource exhaustion | Low |
| VPC configuration (if accessing private resources) | Verify Lambda is in VPC with proper security groups | Medium |

## Runtime Verification Commands

After implementing fixes, verify with actual HTTP requests:

```bash
# Verify Helmet headers
curl -sI http://localhost:3010/ | grep -iE '(x-powered-by|x-content-type|strict-transport|x-frame)'

# Verify rate limiting
for i in $(seq 1 110); do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:3010/; done | sort | uniq -c

# Verify CORS rejects unknown origins
curl -sI -H "Origin: http://evil.com" http://localhost:3010/ | grep -i access-control

# Verify body size limit
python3 -c "print('x'*2000000)" | curl -s -X POST -H "Content-Type: application/json" -d @- http://localhost:3010/candidates -w "\n%{http_code}"

# Verify auth required
curl -s http://localhost:3010/candidates -w "\n%{http_code}"
```

## Report Template

```markdown
# OWASP Top 10 Security Audit Report

- **Project**: [name]
- **Stack**: [technologies]
- **Date**: [YYYY-MM-DD]
- **Scope**: Static code analysis + automated tooling

## Automated Scan Results

### npm audit
- Critical: X | High: X | Medium: X | Low: X
- Key vulnerabilities: [list]

### Secret Scan
- Hits: X
- Locations: [list]

## Findings by Category

### A01: Broken Access Control — [CRITICAL/HIGH/MEDIUM/LOW/CLEAN]
**Checked:** [list what was examined]
**Findings:** [list with file:line references]
**Remediation:** [code examples]

[...repeat for A02-A10...]

## Remediation Priority Matrix

| Phase | Finding | Severity | Effort | Fix |
|-------|---------|----------|--------|-----|
| A | [finding] | Critical | [hours] | [brief description] |

## Verification Checklist
- [ ] [Finding 1]: [how to verify fix]
- [ ] [Finding 2]: [how to verify fix]

## CI/CD Security Integration
- [ ] `npm audit` in CI pipeline (fail on critical/high)
- [ ] ESLint security plugin in pre-commit hook
- [ ] Dependency update bot (Dependabot/Renovate)
- [ ] Secret scanning in CI (truffleHog/gitleaks)
```

## Common Mistakes

| Mistake | Why it's wrong | Fix |
|---------|----------------|-----|
| Skipping categories marked "N/A" without evidence | Auditor assumed rather than verified | Always document what you checked |
| Not running automated tools | Missing known CVEs that are trivially exploitable | Run `npm audit` and secret scan FIRST |
| Reporting findings without remediation code | Findings without fixes create toil, not progress | Every finding needs a code-level fix |
| Not prioritizing fixes | Treating all findings equally paralyzes teams | Use the severity matrix and phase grouping |
| Forgetting CI/CD integration | Manual audits rot; only automated gates persist | Always include pipeline integration steps |
| Auditing only backend OR frontend | XSS vectors cross the boundary | Audit both, trace data flow end-to-end |
| Trusting ORM = no injection risk | ORMs prevent SQL injection but not all injection types | Check for command injection, log injection, path traversal |
| Skipping `.gitignore` and git history check | Secrets removed from code may still be in git history | Check `.gitignore` AND `git log` history AND recommend purge if needed |
| Not checking for mass assignment | ORM prevents SQL injection but allows unfiltered field updates | Verify `req.body` is filtered through an allowlist before Prisma calls |
| Static analysis only | Some vulnerabilities only appear at runtime (CORS headers, rate limits) | Include runtime verification commands in the report |

## Node.js/Express Specific Hardening Checklist

Essential middleware stack (order matters):

```typescript
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import cors from 'cors';
import hpp from 'hpp';

// 1. Security headers
app.use(helmet());
app.disable('x-powered-by');

// 2. Rate limiting
app.use(rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 100,
  standardHeaders: true,
  legacyHeaders: false,
}));

// 3. CORS - explicit origins only
app.use(cors({
  origin: process.env.ALLOWED_ORIGINS?.split(',') || [],
  credentials: true,
  methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'],
}));

// 4. Body parsing with size limits
app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: true, limit: '1mb' }));

// 5. HTTP Parameter Pollution protection
app.use(hpp());

// 6. Request logging (BEFORE routes)
app.use(requestLogger);

// 7. Routes
app.use('/api', routes);

// 8. Error handler (AFTER routes, generic messages only)
app.use(errorHandler);
```

## Prisma/ORM Security Notes

- Always use `select` to limit returned fields (avoid PII leakage)
- Never trust `env()` in `schema.prisma` if the `.env` is committed
- Watch for validation bypass when `id` is present in request body
- Prisma prevents SQL injection but NOT mass assignment - validate allowed fields explicitly
