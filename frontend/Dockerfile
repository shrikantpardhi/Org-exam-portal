# ---------------------------------------------------------------------------
# Frontend container image (multi-stage).
# Build context: ./frontend
# ---------------------------------------------------------------------------
FROM node:22-alpine AS deps

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

# ---------------------------------------------------------------------------
FROM node:22-alpine AS build

WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# NEXT_PUBLIC_* values are inlined at build time. The default is deliberately the RELATIVE base,
# not an absolute origin: the Nginx edge serves both the UI and /api/, so the browser resolves the
# API against whatever origin served the page. An absolute origin baked in here would work only for
# the exact host it was built with; every other host (127.0.0.1, a LAN address, a hostname) would
# make the call cross-origin and the edge's `connect-src 'self'` policy would block it (GAP-164).
ARG NEXT_PUBLIC_API_BASE_URL=/api/v1
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
ENV NEXT_TELEMETRY_DISABLED=1

RUN npm run build

# ---------------------------------------------------------------------------
FROM node:22-alpine AS runtime

WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

RUN addgroup --system --gid 1001 nodejs \
    && adduser --system --uid 1001 nextjs

# `output: "standalone"` produces a self-contained server bundle.
#
# The repository has no `public/` directory, and `COPY` of a missing path fails
# the build. Next.js treats `public/` as optional (it only serves files placed at
# the site root), so it is not copied; static assets come from `.next/static`.
COPY --from=build /app/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000

CMD ["node", "server.js"]
