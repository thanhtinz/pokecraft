// ui.js - shared UI helpers for SunnyX
// confirm() uses ActionFormData instead of MessageFormData because
// MessageForm button1/button2 selection indexes are a classic source of
// reversed-confirmation bugs; ActionForm button order is deterministic.

import { ActionFormData } from "@minecraft/server-ui";

export function confirm(player, title, body, yesLabel = "Confirm", noLabel = "Cancel") {
  return new ActionFormData()
    .title(title)
    .body(body)
    .button(yesLabel)
    .button(noLabel)
    .show(player)
    .then((res) => !res.canceled && res.selection === 0)
    .catch(() => false);
}

// themedForm(frame, header): every SunnyX form goes through here so it
// renders inside SERP's skinned frame (title = unlocalized serp.main.<frame>
// key drives their JSON UI background picker). The Vietnamese header moves
// into the body's first bold line since the title is now a frame selector.
const DEFAULT_ICON = "pokedrock/items/poke_ball";

// SERP's button template reserves a left icon slot; icon-less buttons look
// broken inside their skin, so every button gets at least the pokeball.
function wrapButtons(f) {
  const origButton = f.button.bind(f);
  f.button = (text, icon) => origButton(text, icon ?? DEFAULT_ICON);
  return f;
}

// raw framed form (no header injection) for callers that manage their own body
export function themedRaw(titleKey) {
  return wrapButtons(new ActionFormData().title(titleKey));
}

export function themedForm(frame, header) {
  const f = wrapButtons(new ActionFormData().title("serp.main." + frame));
  const origBody = f.body.bind(f);
  const origShow = f.show.bind(f);
  let bodySet = false;
  const head = "\u00a7l" + header + "\u00a7r";
  f.body = (b) => {
    bodySet = true;
    if (typeof b === "string") return origBody(head + (b ? "\n" + b : ""));
    if (b && b.rawtext) return origBody({ rawtext: [{ text: head + "\n" }, ...b.rawtext] });
    return origBody(b);
  };
  f.show = (p) => {
    if (!bodySet) origBody(head);
    return origShow(p);
  };
  return f;
}
