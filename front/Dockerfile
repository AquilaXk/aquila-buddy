# syntax=docker/dockerfile:1

FROM node:22-alpine AS deps
WORKDIR /workspace/front

COPY front/package.json front/package-lock.json ./
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /workspace/front

ARG NEXT_PUBLIC_API_URL
ARG NEXT_PUBLIC_BACKEND_OAUTH_URL
ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL}
ENV NEXT_PUBLIC_BACKEND_OAUTH_URL=${NEXT_PUBLIC_BACKEND_OAUTH_URL}
ENV NEXT_TELEMETRY_DISABLED=1

COPY --from=deps /workspace/front/node_modules ./node_modules
COPY front ./
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000

RUN addgroup -S aquila && adduser -S aquila -G aquila

COPY --from=build /workspace/front/package.json ./package.json
COPY --from=build /workspace/front/package-lock.json ./package-lock.json
COPY --from=build /workspace/front/node_modules ./node_modules
COPY --from=build /workspace/front/.next ./.next
COPY --from=build /workspace/front/public ./public

EXPOSE 3000

USER aquila
CMD ["npm", "run", "start", "--", "--hostname", "0.0.0.0"]
