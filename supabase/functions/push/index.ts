// Scorelens – Push-Mitteilung (FCM) bei Lobby-Einladung oder Freundschaftsanfrage, wenn die App zu ist.
// Wird per Database-Webhook (Dashboard → Database → Webhooks) für INSERT auf invites und friendships aufgerufen;
// Header dort: Authorization: Bearer <service_role-Key>. Für den selfhost-Stack ohne Edge-Runtime bleibt push/index.mjs.
// Deploy: supabase secrets set FIREBASE_SERVICE_ACCOUNT="$(cat firebase-service-account.json)"
//         supabase functions deploy push
// ponytail: kein Retry, keine Warteschlange – schlägt FCM fehl, ist die Mitteilung weg (wie im Relay); reicht für Einladungen.
import { createClient } from "npm:@supabase/supabase-js@2";
import { JWT } from "npm:google-auth-library@9";

const sa = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT") ?? "{}");
const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
const fcm = new JWT({ email: sa.client_email, key: sa.private_key, scopes: ["https://www.googleapis.com/auth/firebase.messaging"] });

/** Nur der Webhook mit dem service_role-Key darf Pushs auslösen – der Anon-Key besteht die Gateway-Prüfung ebenfalls. */
function isServiceRole(req: Request): boolean {
  const token = req.headers.get("authorization")?.replace(/^Bearer\s+/i, "") ?? "";
  try {
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return payload.role === "service_role";
  } catch { return false; }
}

async function notify(toId: string, fromId: string, text: string) {
  const [{ data: from }, { data: rows }] = await Promise.all([
    db.from("profiles").select("name").eq("id", fromId).maybeSingle(),
    db.from("push_tokens").select("token").eq("user_id", toId),
  ]);
  const tokens = (rows ?? []).map((r) => r.token as string);
  if (!tokens.length) return 0;
  const { token: bearer } = await fcm.getAccessToken();
  const dead: string[] = [];
  let sent = 0;
  for (const token of tokens) {
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
      method: "POST",
      headers: { authorization: `Bearer ${bearer}`, "content-type": "application/json" },
      body: JSON.stringify({ message: { token, notification: { title: "Scorelens", body: `${from?.name ?? "Ein Freund"} ${text}` }, android: { priority: "high" } } }),
    });
    if (res.ok) sent++;
    else if (res.status === 404 || (await res.text()).includes("UNREGISTERED")) dead.push(token);
  }
  if (dead.length) await db.from("push_tokens").delete().in("token", dead);
  console.log(`→ ${toId}: ${sent} gesendet, ${dead.length} Token entfernt`);
  return sent;
}

Deno.serve(async (req) => {
  if (!isServiceRole(req)) return new Response("Forbidden", { status: 403 });
  const { type, table, record } = await req.json();
  if (type !== "INSERT") return new Response("ignored");
  let sent = 0;
  if (table === "invites") sent = await notify(record.to_id, record.from_id, `lädt dich in eine Lobby ein (Code ${record.code})`);
  else if (table === "friendships" && record.status === "pending") sent = await notify(record.addressee, record.requester, "möchte dein Freund werden");
  return Response.json({ sent });
});
