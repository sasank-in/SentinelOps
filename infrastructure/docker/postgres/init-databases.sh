#!/bin/bash
# Creates one database and one owner role per service on first container start.
# A single Postgres instance is shared on purpose: the demo needs "other services on
# the same database stayed healthy" to be a meaningful piece of evidence.
set -euo pipefail

create_db() {
  local db="$1" user="$2" password="$3"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-SQL
	CREATE ROLE ${user} LOGIN PASSWORD '${password}';
	CREATE DATABASE ${db} OWNER ${user};
SQL
}

create_db orders   "$ORDERS_DB_USER"   "$ORDERS_DB_PASSWORD"
create_db payments "$PAYMENTS_DB_USER" "$PAYMENTS_DB_PASSWORD"
