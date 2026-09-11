// Scorelens – teilbarer Freundes-Link: https://<server>/functions/v1/friend/<Nutzer-ID>
// Öffnet im Browser und springt per intent:// in die App (scorelens://friend/<id>, siehe AndroidManifest.xml).
// Deploy: supabase functions deploy friend --no-verify-jwt   (selfhost: gleiche Seite aus der Caddyfile)
Deno.serve((req) => {
  const id = new URL(req.url).pathname.split("/").pop() ?? "";
  if (!/^[0-9a-f-]{36}$/i.test(id)) return new Response("Ungültiger Freundes-Link", { status: 400 });
  const html = `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Scorelens – Freund hinzufügen</title>
<p style="font-family:sans-serif;padding:24px">Scorelens wird geöffnet …<br><br>Passiert nichts? <a href="scorelens://friend/${id}">App öffnen</a> (Scorelens muss installiert sein).</p>
<script>location.replace("intent://friend/${id}#Intent;scheme=scorelens;package=com.freedarts.scorer;end")</script>`;
  return new Response(html, { headers: { "content-type": "text/html; charset=utf-8" } });
});
