#!/bin/sh
set -eu

mkdir -p /app/storage
chown -R spring:spring /app/storage

exec su-exec spring:spring java -jar /app/app.jar
