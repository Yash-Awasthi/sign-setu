FROM golang:1.27.3-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY *.go app_prompts.json ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /gateway .
# distroless has no shell to make it: without this, the nonroot user cannot create CONTRIB_DIR and every upload fails.
RUN mkdir -p /out/data/contrib

# Static binary on distroless: CA certificates for the providers, no shell, an unprivileged user.
FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /gateway /gateway
COPY --from=build --chown=65532:65532 /out/data /data
USER nonroot
# The host terminates TLS and passes the port in $PORT.
ENTRYPOINT ["/gateway"]
