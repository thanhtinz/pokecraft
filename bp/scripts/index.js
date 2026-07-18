// SerpLumen entry - no DX species, SERP handles all Pokemon.
import "./main.js";
import { openHub } from "./hubmain.js";
import { setHubOpener } from "./itemtools.js";
import { initSunnyScan } from "./sunnyscan.js";
import { initTracker } from "./tracker.js";
import { initGts } from "./gts.js";
import { initEvents } from "./events.js";
import { initRoam } from "./roam.js";
import { initSeason } from "./season.js";
import { initTitles } from "./titles.js";
import { initJobs } from "./jobs.js";
import { initClaims } from "./claims.js";
import { initGuards } from "./guards.js";
import { initWaypoints } from "./waypoints.js";
import { initNameplates } from "./nameplates.js";
import { initVehicles } from "./vehicles.js";
import { initPalBase } from "./palbase.js";
import { initBuddy } from "./buddy.js";
import { initMachines } from "./machines.js";
import { initNpcs } from "./npcs.js";
import { world, system as _sys } from "@minecraft/server";

// One-time cleanup of the removed Ranch feature: wipe its data and delete any
// pen Pokemon / floating text / PC boxes it left behind in the world.
function cleanupLegacy() {
  try {
    if (world.getDynamicProperty("sl:ranch") !== undefined) world.setDynamicProperty("sl:ranch", undefined);
    if (world.getDynamicProperty("sl:facil") !== undefined) world.setDynamicProperty("sl:facil", undefined);
  } catch {}
  _sys.runInterval(() => {
    try {
      for (const dimId of ["overworld", "nether", "the_end"]) {
        const dim = world.getDimension(dimId);
        for (const tag of ["sl_ranch", "sl_ranchtxt", "sl_ranchpc", "sl_pet", "sl_pettxt"]) {
          for (const e of dim.getEntities({ tags: [tag] })) { try { e.remove(); } catch {} }
        }
      }
    } catch {}
  }, 100);
}

setHubOpener(openHub);
for (const [name, init] of [["SunnyScan", initSunnyScan], ["Tracker", initTracker], ["GTS", initGts], ["Events", initEvents], ["Roam", initRoam], ["Season", initSeason], ["Titles", initTitles], ["Jobs", initJobs], ["Claims", initClaims], ["Guards", initGuards], ["Waypoints", initWaypoints], ["Nameplates", initNameplates], ["Vehicles", initVehicles], ["PalBase", initPalBase], ["Buddy", initBuddy], ["Machines", initMachines], ["NPCs", initNpcs], ["CleanupLegacy", cleanupLegacy]]) {
  try { init(); } catch (e) { console.warn(name + " init failed: " + (e && e.message)); }
}
