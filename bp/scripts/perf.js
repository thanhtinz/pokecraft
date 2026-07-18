// perf.js - one place to tune how hard the addon works.
// Players can switch to Low mode (Hub -> Performance) on weaker phones.
import { world } from "@minecraft/server";

const KEY = "sl:perf"; // per-player: "low"
export function isLow(p) { try { return p.getDynamicProperty(KEY) === "low"; } catch { return false; } }
export function setLow(p, on) { try { p.setDynamicProperty(KEY, on ? "low" : undefined); } catch {} }

// server-wide: true when EVERY online player asked for low mode
export function serverLow() {
  const ps = world.getAllPlayers();
  return ps.length > 0 && ps.every(isLow);
}
export function anyPlayers() { return world.getAllPlayers().length > 0; }
