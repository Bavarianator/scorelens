// Scorelens Push-Relay: beobachtet invites und friendships per Realtime (service_role) und schickt FCM-Mitteilungen
// an die Tokens aus push_tokens. Läuft gegen supabase.com wie gegen den selfhost-Stack.
//   SUPABASE_URL, SERVICE_ROLE_KEY, GOOGLE_APPLICATION_CREDENTIALS=<Firebase-Service-Account.json>
// ponytail: Ereignisse während eines Reconnects gehen verloren (keine Warteschlange) – für Einladungen verschmerzbar;
//           bei Bedarf beim Verbinden offene invites der letzten Minuten nachholen
import { createClient } from "@supabase/supabase-js";
import admin from "firebase-admin";

const { SUPABASE_URL, SERVICE_ROLE_KEY } = process.env;
if (!SUPABASE_URL || !SERVICE_ROLE_KEY) throw new Error("SUPABASE_URL und SERVICE_ROLE_KEY setzen");
const db = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, { auth: { persistSession: false } });
admin.initializeApp({ credential: admin.credential.applicationDefault() });

export async function notify(toId, fromId, text) {
  const [{ data: from }, { data: rows }] = await Promise.all([
    db.from("profiles").select("name").eq("id", fromId).maybeSingle(),
    db.from("push_tokens").select("token").eq("user_id", toId),
  ]);
  const tokens = (rows ?? []).map((r) => r.token);
  if (!tokens.length) return;
  const res = await admin.messaging().sendEachForMulticast({
    tokens,
    notification: { title: "Scorelens", body: `${from?.name ?? "Ein Freund"} ${text}` },
    android: { priority: "high" },
  });
  const dead = tokens.filter((_, i) => res.responses[i].error?.code === "messaging/registration-token-not-registered");
  if (dead.length) await db.from("push_tokens").delete().in("token", dead);
  console.log(new Date().toISOString(), `→ ${toId}: ${res.successCount} gesendet, ${dead.length} Token entfernt`);
}

db.channel("push")
  .on("postgres_changes", { event: "INSERT", schema: "public", table: "invites" },
    (p) => notify(p.new.to_id, p.new.from_id, `lädt dich in eine Lobby ein (Code ${p.new.code})`).catch(console.error))
  .on("postgres_changes", { event: "INSERT", schema: "public", table: "friendships" },
    (p) => p.new.status === "pending" && notify(p.new.addressee, p.new.requester, "möchte dein Freund werden").catch(console.error))
  .subscribe((status, err) => console.log("Realtime:", status, err ?? ""));
