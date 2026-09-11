#!/usr/bin/env bash
# Erzeugt selfhost/.env mit zufälligen Geheimnissen und den beiden API-Keys (ANON_KEY, SERVICE_ROLE_KEY).
#
#   ./setup.sh                              → http://localhost:8000 (nur zum Testen auf demselben Rechner)
#   ./setup.sh http://192.168.178.50:8000   → im WLAN erreichbar (HTTP)
#   ./setup.sh https://darts.example.com    → Domain zeigt auf den Server, Caddy holt ein Let's-Encrypt-Zertifikat
#
# Danach: docker compose up -d  und  ./migrate.sh   (Schema ist beim ersten Start schon eingespielt; migrate.sh ist
# idempotent und aktualisiert später). In der App: Supabase-URL = die URL oben, Anon-Key = ANON_KEY aus .env.
set -euo pipefail
cd "$(dirname "$0")"

PUBLIC_URL=${1:-http://localhost:8000}
PUBLIC_URL=${PUBLIC_URL%/}
if [ -e .env ]; then
  echo ".env existiert bereits – zum Neuerzeugen erst löschen (Achtung: Keys ändern sich, Datenbank-Volume ggf. entfernen)." >&2
  exit 1
fi
command -v openssl > /dev/null || { echo "openssl wird benötigt" >&2; exit 1; }

rand_b64() { openssl rand -base64 "$1" | tr -d '\n' | tr '+/' '-_' | tr -d '='; }
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
jwt() { # jwt <role> <secret>
  local header payload sig iat exp
  iat=$(date +%s); exp=$((iat + 10 * 365 * 24 * 3600))
  header=$(printf '{"alg":"HS256","typ":"JWT"}' | b64url)
  payload=$(printf '{"role":"%s","iss":"supabase","iat":%d,"exp":%d}' "$1" "$iat" "$exp" | b64url)
  sig=$(printf '%s.%s' "$header" "$payload" | openssl dgst -sha256 -hmac "$2" -binary | b64url)
  printf '%s.%s.%s' "$header" "$payload" "$sig"
}

POSTGRES_PASSWORD=$(rand_b64 24)
JWT_SECRET=$(rand_b64 40)
ANON_KEY=$(jwt anon "$JWT_SECRET")
SERVICE_ROLE_KEY=$(jwt service_role "$JWT_SECRET")
SECRET_KEY_BASE=$(rand_b64 48)
REALTIME_DB_ENC_KEY=$(openssl rand -hex 8)
PG_META_CRYPTO_KEY=$(rand_b64 24)

case "$PUBLIC_URL" in
  https://*) HOST=${PUBLIC_URL#https://}; HOST=${HOST%%/*}; SITE_ADDRESS=$HOST ;;
  http://*)  SITE_ADDRESS=":8000" ;;
  *) echo "URL muss mit http:// oder https:// beginnen" >&2; exit 1 ;;
esac

sed -e "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$POSTGRES_PASSWORD|" \
    -e "s|^JWT_SECRET=.*|JWT_SECRET=$JWT_SECRET|" \
    -e "s|^ANON_KEY=.*|ANON_KEY=$ANON_KEY|" \
    -e "s|^SERVICE_ROLE_KEY=.*|SERVICE_ROLE_KEY=$SERVICE_ROLE_KEY|" \
    -e "s|^SECRET_KEY_BASE=.*|SECRET_KEY_BASE=$SECRET_KEY_BASE|" \
    -e "s|^REALTIME_DB_ENC_KEY=.*|REALTIME_DB_ENC_KEY=$REALTIME_DB_ENC_KEY|" \
    -e "s|^PG_META_CRYPTO_KEY=.*|PG_META_CRYPTO_KEY=$PG_META_CRYPTO_KEY|" \
    -e "s|^SUPABASE_PUBLIC_URL=.*|SUPABASE_PUBLIC_URL=$PUBLIC_URL|" \
    -e "s|^API_EXTERNAL_URL=.*|API_EXTERNAL_URL=$PUBLIC_URL/auth/v1|" \
    -e "s|^CADDY_SITE_ADDRESS=.*|CADDY_SITE_ADDRESS=$SITE_ADDRESS|" \
    .env.example > .env
chmod 600 .env

cat <<MSG
.env erzeugt.

  Supabase-URL für die App:  $PUBLIC_URL
  Anon-Key für die App:      $ANON_KEY

Start:      docker compose up -d
Schema:     ./migrate.sh            (beim ersten Start automatisch; für Updates)
Studio:     docker compose --profile studio up -d   →  http://127.0.0.1:3000
OAuth:      GOOGLE_/GITHUB_/DISCORD_-Zeilen in .env ausfüllen, Redirect-URL beim Anbieter: $PUBLIC_URL/auth/v1/callback
MSG
