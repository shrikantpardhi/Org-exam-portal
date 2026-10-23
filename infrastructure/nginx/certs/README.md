# Nginx certificates (placeholder)

TLS certificates are **not** committed to this repository. Nginx refuses to start
when a configured certificate is missing, so the TLS server block in
`../nginx.conf` ships **inert (commented out)** and the 443 listener in
`docker-compose.yml` is likewise inactive.

## Enabling TLS locally

Generate a self-signed pair for local use only:

```bash
mkdir -p infrastructure/nginx/certs
openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
  -keyout infrastructure/nginx/certs/tls.key \
  -out    infrastructure/nginx/certs/tls.crt \
  -subj "/CN=localhost"
```

Then:

1. uncomment the `server { listen 443 ssl; ... }` block in `../nginx.conf`;
2. uncomment the `8443:443` port mapping on the `nginx` service in
   `docker-compose.yml`;
3. `docker compose --profile edge up -d --force-recreate nginx`.

`tls.key` must never be committed. `.gitignore` excludes everything in this
directory except this file.

A real deployment terminates TLS with a certificate issued by the organization's
CA or a public CA, and sets `Strict-Transport-Security` accordingly. The TLS
listener in `../nginx.conf` already carries the full header set — HSTS **and** the
CSP, `X-Frame-Options`, `Referrer-Policy`, `X-Content-Type-Options`,
`X-Permitted-Cross-Domain-Policies` and `Cross-Origin-Opener-Policy` — because
Nginx does not inherit `add_header` into a server block that declares its own.
Enabling the listener therefore does not drop the policies that the `http` block
sets for the plaintext listener (GAP-137).

The application independently emits the same headers, and emits HSTS when the
request reaches it with `X-Forwarded-Proto: https`, which this edge always sets.

Remaining work on the edge is verification rather than configuration: HTTPS
enforcement (a plaintext-to-TLS redirect), HSTS activation against a real
certificate, and CORS preflight behaviour (GAP-015), which cannot be proven until
a certificate is mounted and the stack is exercised end to end.
