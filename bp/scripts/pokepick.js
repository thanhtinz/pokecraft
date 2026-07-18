// pokepick.js - search a Pokemon by name (or dex) and pick a level, the same
// way item search works. Returns { dex, level } or null.
import { ModalFormData } from "@minecraft/server-ui";
import { actionMenu } from "./forms.js";
import { POKENAMES } from "./pokenames.js";

export async function pickPokemonSearch(actor, titleText = "Grant Pokemon") {
  const f = await new ModalFormData()
    .title(titleText)
    .textField("Search Pokemon (name or dex)", "e.g. pika, char, 25")
    .slider("Level", 1, 100, { defaultValue: 5 })
    .show(actor);
  if (f.canceled || !f.formValues) return null;
  const kw = String(f.formValues[0]).trim().toLowerCase();
  const level = Number(f.formValues[1]) || 5;
  if (!kw) return null;

  const results = Object.entries(POKENAMES)
    .filter(([dex, name]) => name.toLowerCase().includes(kw) || dex === kw)
    .sort((a, b) => Number(a[0]) - Number(b[0]))
    .slice(0, 30);
  if (results.length === 0) {
    actor.sendMessage("\u00a7c[SunHub] No Pokemon matches: " + kw);
    return null;
  }
  const sel = await actionMenu(
    actor,
    "Results: " + kw,
    "Select a Pokemon (will be Lv." + level + "):",
    results.map(([dex, name]) => ({ label: name + "\n\u00a78#" + dex, icon: "pokedrock/pokedex/" + dex }))
  );
  if (sel < 0 || sel >= results.length) return null;
  return { dex: Number(results[sel][0]), level };
}
