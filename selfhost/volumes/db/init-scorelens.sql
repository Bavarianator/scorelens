-- Wird beim allerersten Start der Datenbank ausgeführt (leeres Datenverzeichnis): Scorelens-Schema einspielen.
-- Spätere Änderungen an ../supabase/migrations: ./migrate.sh
\i /docker-entrypoint-initdb.d/migrations/scorelens/20260911120000_scorelens_online.sql
