-- Scorelens Online – Dart-Korrektur als Ereignis.
-- Bisher war ein von Lens falsch erkannter Dart online nur über Rückgängig zu reparieren, und das nur einen Schritt
-- weit. Neu: kind = 'correct' mit idx = Dart der Aufnahme (0..2); jeder Client wendet correctDart an.
-- Idempotent wie die anderen Migrationen.

alter table public.match_events add column if not exists idx smallint;

alter table public.match_events drop constraint if exists match_events_kind_check;
alter table public.match_events add constraint match_events_kind_check
  check (kind in ('throw', 'next', 'undo', 'correct'));
