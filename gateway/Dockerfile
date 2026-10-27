FROM golang:1.27.3-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY *.go app_prompts.json ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /gateway .

# Static binary on distroless: CA certificates for the providers, no shell, an unprivileged user.
FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /gateway /gateway
USER nonroot
# The host terminates TLS and passes the port in $PORT.
ENTRYPOINT ["/gateway"]
