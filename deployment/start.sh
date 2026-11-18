#!/bin/sh
set -eu

if [ -z "${DB_URL:-}" ]; then
    database_address=
    if [ -n "${RENDER_DATABASE_URL:-}" ]; then
        case "$RENDER_DATABASE_URL" in
            postgresql://*@*/*) database_address=${RENDER_DATABASE_URL##*@} ;;
            *)
                printf '%s\n' 'Render database URL must contain a PostgreSQL host and database.' >&2
                exit 1
                ;;
        esac
        database_address=${database_address%%\?*}
    elif [ -n "${DB_HOST:-}" ]; then
        : "${DB_NAME:?Set the database name}"
        database_address="${DB_HOST}:${DB_PORT:-5432}/${DB_NAME}"
    fi
    if [ -n "$database_address" ]; then
        export DB_URL="jdbc:postgresql://${database_address}?sslmode=${DB_SSL_MODE:-require}"
    fi
fi
unset RENDER_DATABASE_URL

exec java -jar /app/app.jar
