FROM rust:1.67-slim as builder
WORKDIR /usr/src/actix-web-simple-auth
COPY . .
RUN cargo install --path .

FROM debian:bullseye-slim
RUN apt-get update && rm -rf /var/lib/apt/lists/*
COPY --from=builder /usr/local/cargo/bin/actix-web-simple-auth /usr/local/bin/actix-web-simple-auth
CMD ["actix-web-simple-auth"]