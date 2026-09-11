#!/usr/bin/env bash
# Spielt alle Migrationen aus ../supabase/migrations in die laufende Datenbank ein (idempotent).
set -euo pipefail
cd "$(dirname "$0")"
[ -e .env ] || { echo "Keine .env – zuerst ./setup.sh ausführen." >&2; exit 1; }
for f in ../supabase/migrations/*.sql; do
  echo "→ $(basename "$f")"
  docker compose exec -T db psql -v ON_ERROR_STOP=1 -U supabase_admin -d postgres < "$f"
done
echo "Fertig."
