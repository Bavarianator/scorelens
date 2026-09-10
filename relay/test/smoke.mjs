// Smoke-Test: Handy-Socket und Zuschauer-Socket gegen ein laufendes Relay (npm run dev).
//   RELAY_URL=http://localhost:8787 node test/smoke.mjs
const base = (process.env.RELAY_URL ?? "http://localhost:8787").replace(/^http/, "ws");
const code = "T" + Math.random().toString(36).slice(2, 7).toUpperCase();
const token = "smoke-test-token-" + Math.random().toString(36).slice(2);

const wait = (ws, pred, ms = 4000) => new Promise((res, rej) => {
  const t = setTimeout(() => rej(new Error("timeout waiting for " + pred)), ms);
  ws.addEventListener("message", (ev) => { if (pred(ev.data)) { clearTimeout(t); res(ev.data); } });
});
const open = (url) => new Promise((res, rej) => {
  const ws = new WebSocket(url);
  ws.addEventListener("open", () => res(ws));
  ws.addEventListener("error", () => rej(new Error("connect failed " + url)));
});
const assert = (c, m) => { if (!c) { console.error("FAIL:", m); process.exit(1); } console.log("ok:", m); };

// 1) Zuschauer vor dem Handy: bekommt online=false
const viewer = await open(`${base}/ws/view/${code}`);
const first = JSON.parse(await wait(viewer, (d) => d.includes('"online"')));
assert(first.online === false, "viewer sieht Board offline");

// 2) Handy verbindet sich, Zuschauer bekommt online=true und danach den Zustand
const onP = wait(viewer, (d) => d.includes('"online":true'));
const board = await open(`${base}/ws/board/${code}?token=${token}`);
const on = JSON.parse(await onP);
assert(on.online === true, "viewer sieht Board online");
const state = { hasGame: true, title: "501", headline: "Leg 1", players: [{ name: "A", score: "501" }] };
const stP = wait(viewer, (d) => d.includes('"state"'));
board.send(JSON.stringify({ type: "state", state }));
const st = JSON.parse(await stP);
assert(st.state.title === "501" && st.state.players[0].name === "A", "zustand kommt beim viewer an");

// 3) Befehl vom Zuschauer erreicht das Handy, unbekannte Befehle nicht
const cmdP = wait(board, (d) => d.includes('"cmd"'));
viewer.send(JSON.stringify({ type: "cmd", do: "undo" }));
const cmd = JSON.parse(await cmdP);
assert(cmd.do === "undo", "undo erreicht das handy");
viewer.send(JSON.stringify({ type: "cmd", do: "reset" }));
let bad = false; board.addEventListener("message", (ev) => { if (ev.data.includes("reset")) bad = true; });
await new Promise((r) => setTimeout(r, 400));
assert(!bad, "unbekannter befehl wird verworfen");

// 4) Ping/Pong-Auto-Antwort
const pongP = wait(viewer, (d) => d === "pong");
viewer.send("ping");
assert((await pongP) === "pong", "ping/pong");

// 5) Spät beitretender Zuschauer bekommt den letzten Zustand sofort
const late = new WebSocket(`${base}/ws/view/${code}`);
const lateState = JSON.parse(await wait(late, (d) => d.includes('"state"')));
assert(lateState.state.title === "501", "später viewer bekommt letzten zustand");

// 6) Fremdes Handy mit anderem Token wird abgewiesen; REST-Abfrage liefert Zustand
let rejected = false;
try { await open(`${base}/ws/board/${code}?token=other-token-1234567890`); } catch { rejected = true; }
assert(rejected, "fremdes token wird abgewiesen (403)");
const rest = await fetch(`${base.replace(/^ws/, "http")}/api/state/${code}`).then((r) => r.json());
assert(rest.title === "501", "REST /api/state liefert zustand");

// 7) Handy trennt: Zuschauer sieht offline
const offP = wait(viewer, (d) => d.includes('"online":false'));
board.close();
const off = JSON.parse(await offP);
assert(off.online === false, "viewer sieht offline nach trennung");

viewer.close(); late.close();
console.log("alle tests bestanden");
process.exit(0);
