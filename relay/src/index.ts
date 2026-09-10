/**
 * FreeDarts Relay – Online-Remote-Scoring über Cloudflare Workers + Durable Objects.
 *
 * Das Handy (Spiel-Engine, Lens-Kamera) baut eine ausgehende WebSocket-Verbindung auf und
 * schiebt seinen Spielzustand hierher. Browser ("Viewer") verbinden sich mit demselben
 * Session-Code und bekommen den Zustand weitergereicht; Undo/Next laufen den Weg zurück.
 * Pro Session-Code existiert genau ein Durable Object (BoardSession).
 *
 * Routen:
 *   GET /ws/board/<code>?token=<secret>   WebSocket des Handys (schreibt Zustand)
 *   GET /ws/view/<code>                   WebSocket eines Zuschauers (liest Zustand, sendet Befehle)
 *   GET /api/state/<code>                 letzter Zustand als JSON (Debug / Polling-Fallback)
 *   alles andere                          statische Zuschauer-Seite (public/)
 *
 * Nachrichten (JSON, Text-Frames):
 *   Handy  -> Relay:  {"type":"state","state":{...}}
 *   Relay  -> Viewer: {"type":"state","state":{...}} | {"type":"online","online":bool}
 *   Viewer -> Relay:  {"type":"cmd","do":"undo"|"next"}
 *   Relay  -> Handy:  {"type":"cmd","do":"undo"|"next"}
 *   beide  -> Relay:  "ping"  (Auto-Antwort "pong", weckt das Objekt nicht)
 */
import { DurableObject } from "cloudflare:workers";

export interface Env {
  BOARD: DurableObjectNamespace<BoardSession>;
  ASSETS: Fetcher;
}

const ALLOWED_COMMANDS = new Set(["undo", "next"]);
/** Mindestabstand zwischen zwei Befehlen desselben Zuschauers. */
const COMMAND_MIN_INTERVAL_MS = 250;
/** Nach so langer Zeit ohne verbundenes Handy wird die Session (Code + Token + Zustand) freigegeben. */
const SESSION_TTL_MS = 12 * 60 * 60 * 1000;
const MAX_STATE_BYTES = 64 * 1024;

type Attachment = { role: "board" | "view"; lastCmd: number };

function normalizeCode(raw: string | undefined): string | null {
  const code = (raw ?? "").toUpperCase().replace(/[^A-Z0-9]/g, "");
  return code.length >= 4 && code.length <= 12 ? code : null;
}

export class BoardSession extends DurableObject<Env> {
  private stateJson: string | null | undefined; // undefined = noch nicht aus dem Storage geladen

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    // Keepalive ohne Aufwecken des Objekts (Hibernation).
    this.ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const role = url.searchParams.get("role");

    if (url.pathname === "/state") {
      const state = await this.loadState();
      return new Response(state ?? "null", {
        headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
      });
    }

    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("Expected WebSocket", { status: 426 });
    }
    if (role !== "board" && role !== "view") return new Response("Bad role", { status: 400 });

    if (role === "board") {
      const token = url.searchParams.get("token") ?? "";
      if (token.length < 16) return new Response("Token required", { status: 401 });
      const stored = await this.ctx.storage.get<string>("token");
      if (stored === undefined) {
        await this.ctx.storage.put("token", token);
      } else if (stored !== token) {
        // Code ist bereits von einem anderen Handy belegt.
        return new Response("Code in use", { status: 403 });
      }
    }

    const pair = new WebSocketPair();
    const [client, server] = [pair[0], pair[1]];
    server.serializeAttachment({ role, lastCmd: 0 } satisfies Attachment);
    this.ctx.acceptWebSocket(server, [role]);

    if (role === "board") {
      await this.ctx.storage.deleteAlarm();
      this.broadcastToViewers(JSON.stringify({ type: "online", online: true }));
    } else {
      const state = await this.loadState();
      server.send(JSON.stringify({ type: "online", online: this.boardOnline() }));
      if (state) server.send(`{"type":"state","state":${state}}`);
    }
    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer): Promise<void> {
    if (typeof message !== "string") return;
    const att = ws.deserializeAttachment() as Attachment | null;
    if (!att) return;

    let msg: { type?: string; state?: unknown; do?: string };
    try {
      msg = JSON.parse(message);
    } catch {
      return;
    }

    if (att.role === "board" && msg.type === "state" && msg.state !== undefined) {
      const json = JSON.stringify(msg.state);
      if (json.length > MAX_STATE_BYTES) return;
      this.stateJson = json;
      await this.ctx.storage.put("state", json);
      this.broadcastToViewers(`{"type":"state","state":${json}}`);
      return;
    }

    if (att.role === "view" && msg.type === "cmd" && typeof msg.do === "string") {
      const now = Date.now();
      if (!ALLOWED_COMMANDS.has(msg.do) || now - att.lastCmd < COMMAND_MIN_INTERVAL_MS) return;
      ws.serializeAttachment({ ...att, lastCmd: now } satisfies Attachment);
      const out = JSON.stringify({ type: "cmd", do: msg.do });
      for (const board of this.ctx.getWebSockets("board")) this.safeSend(board, out);
    }
  }

  async webSocketClose(ws: WebSocket, code: number, reason: string, wasClean: boolean): Promise<void> {
    await this.onSocketGone(ws);
  }

  async webSocketError(ws: WebSocket): Promise<void> {
    await this.onSocketGone(ws);
  }

  /** Aufräumen, wenn das Handy lange nicht mehr da war: Code wird wieder frei. */
  async alarm(): Promise<void> {
    if (this.boardOnline()) return;
    for (const v of this.ctx.getWebSockets("view")) {
      try {
        v.close(1001, "session expired");
      } catch {}
    }
    this.stateJson = null;
    await this.ctx.storage.deleteAll();
  }

  private async onSocketGone(ws: WebSocket): Promise<void> {
    const att = ws.deserializeAttachment() as Attachment | null;
    if (att?.role !== "board") return;
    // Der geschlossene Socket ist ggf. noch in getWebSockets() enthalten.
    const others = this.ctx.getWebSockets("board").filter((s) => s !== ws);
    if (others.length === 0) {
      this.broadcastToViewers(JSON.stringify({ type: "online", online: false }));
      await this.ctx.storage.setAlarm(Date.now() + SESSION_TTL_MS);
    }
  }

  private boardOnline(): boolean {
    return this.ctx.getWebSockets("board").length > 0;
  }

  private async loadState(): Promise<string | null> {
    if (this.stateJson === undefined) this.stateJson = (await this.ctx.storage.get<string>("state")) ?? null;
    return this.stateJson;
  }

  private broadcastToViewers(text: string): void {
    for (const v of this.ctx.getWebSockets("view")) this.safeSend(v, text);
  }

  private safeSend(ws: WebSocket, text: string): void {
    try {
      ws.send(text);
    } catch {
      // Socket ist bereits weg; Close-Handler räumt auf.
    }
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const parts = url.pathname.split("/").filter(Boolean);

    // /ws/board/<code>  |  /ws/view/<code>
    if (parts[0] === "ws" && (parts[1] === "board" || parts[1] === "view")) {
      const code = normalizeCode(parts[2]);
      if (!code) return new Response("Bad code", { status: 400 });
      if (request.headers.get("Upgrade") !== "websocket") return new Response("Expected WebSocket", { status: 426 });
      const target = new URL(request.url);
      target.pathname = "/ws";
      target.searchParams.set("role", parts[1]);
      return env.BOARD.getByName(code).fetch(new Request(target.toString(), request));
    }

    // /api/state/<code>
    if (parts[0] === "api" && parts[1] === "state") {
      const code = normalizeCode(parts[2]);
      if (!code) return new Response("Bad code", { status: 400 });
      const res = await env.BOARD.getByName(code).fetch(new Request(new URL("/state", request.url).toString()));
      const headers = new Headers(res.headers);
      headers.set("access-control-allow-origin", "*");
      return new Response(res.body, { status: res.status, headers });
    }

    // Zuschauer-Seite (/, /b/<code>) und sonstige statische Dateien.
    return env.ASSETS.fetch(request);
  },
} satisfies ExportedHandler<Env>;
