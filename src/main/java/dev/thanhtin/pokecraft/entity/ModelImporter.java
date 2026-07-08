package dev.thanhtin.pokecraft.entity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.thanhtin.pokecraft.PokeCraftPlugin;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Turns Bedrock entity geometry ({@code .geo.json}, as shipped by many model
 * packs) into BlockBench {@code .bbmodel} blueprints at server start, so admins
 * can just drop model folders into {@code plugins/PokeCraft/models-import/} and
 * have them show up in BetterModel with no manual BlockBench work.
 *
 * <p>For each {@code <name>.geo.json} it looks (recursively, by matching name)
 * for a {@code <name>.png} texture and a {@code <name>.animation.json}, builds a
 * {@code .bbmodel} (bones -&gt; groups, cubes -&gt; elements, box/per-face UV,
 * inflate, rotation, embedded texture, animation keyframes) into
 * {@code plugins/BetterModel/models/}, then runs {@code /bm reload}. Only the
 * static mesh + keyframe animations are converted; molang is carried through
 * as-is. The plugin then binds species to blueprints by name as usual.</p>
 */
public class ModelImporter {

    private final PokeCraftPlugin plugin;
    private final Gson gson = new GsonBuilder().create();
    private int[] rotSign = {-1, -1, 1};
    private boolean onlySpecies = true;
    private boolean importAnimations = true;
    private final java.util.Set<String> extraNames = new java.util.HashSet<>();
    private int skipped = 0;

    public ModelImporter(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** Convert everything in the import folder and reload BetterModel if anything changed. */
    public void run() {
        if (!plugin.getConfig().getBoolean("models.auto-import", true)) return;
        List<Integer> cfg = plugin.getConfig().getIntegerList("models.import-rotation-sign");
        if (cfg.size() == 3) rotSign = new int[]{cfg.get(0), cfg.get(1), cfg.get(2)};
        onlySpecies = plugin.getConfig().getBoolean("models.import-only-species", true);
        importAnimations = plugin.getConfig().getBoolean("models.import-animations", true);
        extraNames.clear();
        for (String n : plugin.getConfig().getStringList("models.import-extra")) {
            extraNames.add(n.toLowerCase(Locale.ROOT));
        }
        // also allow any blueprint referenced by the ball / npc model maps
        for (String key : new String[]{"models.ball-blueprints", "models.npc-blueprints"}) {
            var sec = plugin.getConfig().getConfigurationSection(key);
            if (sec != null) {
                for (String k : sec.getKeys(false)) {
                    String v = sec.getString(k);
                    if (v != null && !v.isBlank()) extraNames.add(v.toLowerCase(Locale.ROOT));
                }
            }
        }
        skipped = 0;

        File importDir = new File(plugin.getDataFolder(), "models-import");
        if (!importDir.exists()) {
            importDir.mkdirs();
            writeReadme(importDir);
            return;
        }
        File pluginsDir = plugin.getDataFolder().getParentFile();
        // BetterModel (free, open-source, supports current MC versions).
        File bmDir = new File(pluginsDir, "BetterModel");
        if (!bmDir.isDirectory()) {
            plugin.getLogger().info("[..] BetterModel not installed - skipping model import");
            return;
        }
        File outDir = new File(bmDir, "models");
        String reloadCmd = "bm reload"; // BetterModel's command is /bm
        outDir.mkdirs();

        // Converting a full pack (~900 models, each embedding a base64 texture) must
        // NOT run on the main thread or it freezes startup. Do it async, then reload
        // the model engine once, back on the main thread.
        final File fImportDir = importDir;
        final File fOutDir = outDir;
        final String fReloadCmd = reloadCmd;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> convertAll(fImportDir, fOutDir, fReloadCmd));
    }

    private void convertAll(File importDir, File outDir, String reloadCmd) {
        List<File> geos = new ArrayList<>();
        collectGeos(importDir, geos);
        if (geos.isEmpty()) return;

        int done = 0;
        for (File geo : geos) {
            try {
                if (convert(geo, importDir, outDir)) done++;
            } catch (Exception e) {
                plugin.getLogger().warning("[WARN] Model import failed for "
                        + geo.getName() + ": " + e.getMessage());
            }
        }
        if (skipped > 0) {
            plugin.getLogger().info("[..] Skipped " + skipped
                    + " model(s) with no matching species (set models.import-only-species: false to keep them)");
        }
        if (done > 0) {
            plugin.getLogger().info("[OK] Imported " + done + " model(s) into "
                    + outDir.getParentFile().getName() + " - reloading");
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), reloadCmd));
        }
    }

    private void collectGeos(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collectGeos(f, out);
            else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".geo.json")) out.add(f);
        }
    }

    private String stem(File geo) {
        String n = geo.getName();
        int i = n.toLowerCase(Locale.ROOT).indexOf(".geo.json");
        return i >= 0 ? n.substring(0, i) : n;
    }

    private File firstPngIn(File dir) {
        if (dir == null) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".png")) return f;
        }
        return null;
    }

    private File findByStem(File root, String stem, String suffix) {
        File[] files = root.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isDirectory()) {
                File hit = findByStem(f, stem, suffix);
                if (hit != null) return hit;
            } else if (f.getName().equalsIgnoreCase(stem + suffix)) {
                return f;
            }
        }
        return null;
    }

    private boolean convert(File geoFile, File importRoot, File outDir) throws Exception {
        JsonObject root;
        try (FileReader r = new FileReader(geoFile)) {
            root = JsonParser.parseReader(r).getAsJsonObject();
        }
        if (!root.has("minecraft:geometry")) return false;
        JsonArray geos = root.getAsJsonArray("minecraft:geometry");
        String name = stem(geoFile).toLowerCase(Locale.ROOT);

        File tex = findByStem(importRoot, stem(geoFile), ".png");
        if (tex == null) tex = firstPngIn(geoFile.getParentFile()); // Cobblemon: texture beside the model
        String texB64 = null;
        if (tex != null) {
            texB64 = Base64.getEncoder().encodeToString(Files.readAllBytes(tex.toPath()));
        }
        File animFile = findByStem(importRoot, stem(geoFile), ".animation.json");
        JsonObject animData = null;
        if (animFile != null) {
            try (FileReader r = new FileReader(animFile)) {
                animData = JsonParser.parseReader(r).getAsJsonObject();
            }
        }

        boolean wrote = false;
        for (int gi = 0; gi < geos.size(); gi++) {
            JsonObject geo = geos.get(gi).getAsJsonObject();
            String modelName = geos.size() == 1 ? name : name + "_" + gi;
            if (onlySpecies && plugin.species().getSpecies(modelName) == null
                    && !extraNames.contains(modelName)) {
                skipped++;
                continue; // not a known pokemon or configured ball/npc - skip
            }
            JsonObject desc = geo.has("description") ? geo.getAsJsonObject("description") : new JsonObject();
            int resW = desc.has("texture_width") ? desc.get("texture_width").getAsInt() : 64;
            int resH = desc.has("texture_height") ? desc.get("texture_height").getAsInt() : 64;

            File out = new File(outDir, modelName + ".bbmodel");
            if (out.isFile() && out.lastModified() >= geoFile.lastModified()) {
                continue; // already converted and up to date - skip (fast restarts)
            }

            Map<String, String> boneUuid = new HashMap<>();
            JsonObject model = buildModel(geo, modelName, texB64, resW, resH, boneUuid);
            if (importAnimations && animData != null) {
                JsonArray anims = buildAnimations(animData, modelName, boneUuid);
                if (anims.size() > 0) model.add("animations", anims);
            }
            try (FileWriter w = new FileWriter(out)) {
                gson.toJson(model, w);
            }
            wrote = true;
        }
        return wrote;
    }

    // ---------- mesh ----------

    private JsonObject buildModel(JsonObject geo, String name, String texB64,
                                  int resW, int resH, Map<String, String> boneUuid) {
        JsonArray elements = new JsonArray();
        Map<String, JsonObject> groups = new HashMap<>();
        Map<String, String> parentOf = new HashMap<>();
        List<String> order = new ArrayList<>();

        JsonArray bones = geo.has("bones") ? geo.getAsJsonArray("bones") : new JsonArray();
        for (JsonElement be : bones) {
            JsonObject bone = be.getAsJsonObject();
            String bname = bone.get("name").getAsString();
            String uuid = UUID.randomUUID().toString();
            boneUuid.put(bname, uuid);

            JsonObject g = new JsonObject();
            g.addProperty("name", bname);
            g.add("origin", vec(arr3(bone, "pivot")));
            g.add("rotation", vec(rot(arr3(bone, "rotation"))));
            g.addProperty("uuid", uuid);
            g.addProperty("export", true);
            g.addProperty("isOpen", false);
            g.addProperty("visibility", true);
            JsonArray children = new JsonArray();

            if (bone.has("cubes")) {
                double[] bonePivot = arr3(bone, "pivot");
                int ci = 0;
                for (JsonElement ce : bone.getAsJsonArray("cubes")) {
                    JsonObject el = cubeElement(ce.getAsJsonObject(), bonePivot);
                    // unique element name: BetterModel maps elements by name and
                    // throws on duplicates, so cubes can't all be named "cube"
                    el.addProperty("name", bname + "_c" + ci++);
                    elements.add(el);
                    children.add(el.get("uuid").getAsString());
                }
            }
            g.add("children", children);
            groups.put(bname, g);
            order.add(bname);
            if (bone.has("parent")) parentOf.put(bname, bone.get("parent").getAsString());
        }

        JsonArray outliner = new JsonArray();
        for (String nm : order) {
            JsonObject g = groups.get(nm);
            String parent = parentOf.get(nm);
            if (parent != null && groups.containsKey(parent)) {
                groups.get(parent).getAsJsonArray("children").add(g);
            } else {
                outliner.add(g);
            }
        }

        JsonObject meta = new JsonObject();
        meta.addProperty("format_version", "4.5");
        meta.addProperty("model_format", "bedrock");
        meta.addProperty("box_uv", true);

        JsonObject res = new JsonObject();
        res.addProperty("width", resW);
        res.addProperty("height", resH);

        JsonObject model = new JsonObject();
        model.add("meta", meta);
        model.addProperty("name", name);
        model.addProperty("geometry_name", name);
        model.add("resolution", res);
        model.add("elements", elements);
        model.add("outliner", outliner);
        JsonArray textures = new JsonArray();
        if (texB64 != null) {
            JsonObject t = new JsonObject();
            t.addProperty("name", name + ".png");
            t.addProperty("id", "0");
            t.addProperty("particle", false);
            t.addProperty("mode", "bitmap");
            t.addProperty("visible", true);
            t.addProperty("uuid", UUID.randomUUID().toString());
            t.addProperty("source", "data:image/png;base64," + texB64);
            textures.add(t);
        }
        model.add("textures", textures);
        return model;
    }

    private JsonObject cubeElement(JsonObject cube, double[] bonePivot) {
        double[] origin = arr3(cube, "origin");
        double[] size = arr3(cube, "size");
        double[] to = {origin[0] + size[0], origin[1] + size[1], origin[2] + size[2]};
        double[] pivot = cube.has("pivot") ? arr3(cube, "pivot") : bonePivot;

        JsonObject el = new JsonObject();
        boolean boxUv = cube.has("uv") && cube.get("uv").isJsonArray();
        el.addProperty("name", "cube");
        el.addProperty("box_uv", boxUv);
        el.addProperty("rescale", false);
        el.addProperty("locked", false);
        el.add("from", vec(origin));
        el.add("to", vec(to));
        el.addProperty("autouv", 0);
        el.addProperty("color", 0);
        el.addProperty("inflate", cube.has("inflate") ? cube.get("inflate").getAsDouble() : 0);
        el.add("origin", vec(pivot));
        el.addProperty("uuid", UUID.randomUUID().toString());
        if (cube.has("rotation")) el.add("rotation", vec(rot(arr3(cube, "rotation"))));

        if (boxUv) {
            JsonArray uv = cube.getAsJsonArray("uv");
            JsonArray off = new JsonArray();
            off.add(uv.get(0).getAsDouble());
            off.add(uv.get(1).getAsDouble());
            el.add("uv_offset", off);
            el.addProperty("mirror_uv", cube.has("mirror") && cube.get("mirror").getAsBoolean());
        } else {
            JsonObject uvObj = cube.has("uv") ? cube.getAsJsonObject("uv") : new JsonObject();
            JsonObject faces = new JsonObject();
            for (String f : new String[]{"north", "east", "south", "west", "up", "down"}) {
                JsonObject face = new JsonObject();
                if (uvObj.has(f) && uvObj.getAsJsonObject(f).has("uv")) {
                    JsonObject fd = uvObj.getAsJsonObject(f);
                    JsonArray u = fd.getAsJsonArray("uv");
                    JsonArray s = fd.has("uv_size") ? fd.getAsJsonArray("uv_size") : null;
                    double u0 = u.get(0).getAsDouble(), v0 = u.get(1).getAsDouble();
                    double uw = s != null ? s.get(0).getAsDouble() : 0;
                    double uh = s != null ? s.get(1).getAsDouble() : 0;
                    JsonArray rect = new JsonArray();
                    rect.add(u0); rect.add(v0); rect.add(u0 + uw); rect.add(v0 + uh);
                    face.add("uv", rect);
                    face.addProperty("texture", 0);
                } else {
                    JsonArray zero = new JsonArray();
                    zero.add(0); zero.add(0); zero.add(0); zero.add(0);
                    face.add("uv", zero);
                }
                faces.add(f, face);
            }
            el.add("faces", faces);
        }
        return el;
    }

    // ---------- animation ----------

    private JsonArray buildAnimations(JsonObject animData, String modelName, Map<String, String> boneUuid) {
        JsonArray out = new JsonArray();
        if (!animData.has("animations")) return out;
        JsonObject anims = animData.getAsJsonObject("animations");
        for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
            String shortName = e.getKey();
            if (shortName.startsWith("animation.")) shortName = shortName.substring("animation.".length());
            String prefix = modelName + ".";
            if (shortName.startsWith(prefix)) shortName = shortName.substring(prefix.length());
            JsonObject anim = e.getValue().getAsJsonObject();

            JsonObject animators = new JsonObject();
            if (anim.has("bones")) {
                for (Map.Entry<String, JsonElement> be : anim.getAsJsonObject("bones").entrySet()) {
                    String uuid = boneUuid.get(be.getKey());
                    if (uuid == null) continue;
                    JsonObject chans = be.getValue().getAsJsonObject();
                    JsonArray keyframes = new JsonArray();
                    for (String channel : new String[]{"rotation", "position", "scale"}) {
                        if (!chans.has(channel)) continue;
                        JsonElement ch = chans.get(channel);
                        if (ch.isJsonObject()) {
                            addKeyframes(keyframes, channel, ch.getAsJsonObject());
                        } else {
                            JsonObject single = new JsonObject();
                            single.add("0.0", ch);
                            addKeyframes(keyframes, channel, single);
                        }
                    }
                    if (keyframes.size() > 0) {
                        JsonObject a = new JsonObject();
                        a.addProperty("name", be.getKey());
                        a.addProperty("type", "bone");
                        a.add("keyframes", keyframes);
                        animators.add(uuid, a);
                    }
                }
            }
            if (animators.size() == 0) continue;

            JsonObject a = new JsonObject();
            a.addProperty("uuid", UUID.randomUUID().toString());
            a.addProperty("name", shortName);
            a.addProperty("loop", anim.has("loop") && anim.get("loop").getAsBoolean() ? "loop" : "once");
            a.addProperty("override", false);
            a.addProperty("length", anim.has("animation_length") ? anim.get("animation_length").getAsDouble() : 0);
            a.addProperty("snapping", 24);
            a.add("animators", animators);
            out.add(a);
        }
        return out;
    }

    private void addKeyframes(JsonArray out, String channel, JsonObject kfDict) {
        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(kfDict.entrySet());
        entries.sort((a, b) -> Double.compare(parseTime(a.getKey()), parseTime(b.getKey())));
        for (Map.Entry<String, JsonElement> kf : entries) {
            double time;
            try {
                time = Double.parseDouble(kf.getKey());
            } catch (NumberFormatException ex) {
                continue;
            }
            String[] pts = point(kf.getValue());
            if (channel.equals("rotation")) {
                for (int i = 0; i < 3; i++) pts[i] = sign(pts[i], rotSign[i]);
            }
            JsonObject dp = new JsonObject();
            dp.addProperty("x", pts[0]);
            dp.addProperty("y", pts[1]);
            dp.addProperty("z", pts[2]);
            JsonArray dps = new JsonArray();
            dps.add(dp);

            JsonObject frame = new JsonObject();
            frame.addProperty("channel", channel);
            frame.add("data_points", dps);
            frame.addProperty("uuid", UUID.randomUUID().toString());
            frame.addProperty("time", time);
            frame.addProperty("color", -1);
            frame.addProperty("interpolation", "linear");
            out.add(frame);
        }
    }

    private double parseTime(String s) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
    }

    /** Normalise a bedrock keyframe value to [x, y, z] as strings (numbers or molang). */
    private String[] point(JsonElement val) {
        if (val.isJsonObject()) {
            JsonObject o = val.getAsJsonObject();
            JsonElement inner = o.has("post") ? o.get("post") : o.get("pre");
            if (inner != null) return point(inner);
            return new String[]{"0", "0", "0"};
        }
        if (val.isJsonArray()) {
            JsonArray a = val.getAsJsonArray();
            String[] r = {"0", "0", "0"};
            for (int i = 0; i < 3 && i < a.size(); i++) r[i] = a.get(i).getAsString();
            return r;
        }
        String s = val.getAsString();
        return new String[]{s, s, s};
    }

    private String sign(String v, int s) {
        if (s == 1) return v;
        try {
            double d = Double.parseDouble(v);
            return String.valueOf(-d);
        } catch (NumberFormatException e) {
            // molang: BetterModel's parser can't negate a parenthesis ("-(...)"),
            // so multiply by -1 instead - mathematically identical, parses fine.
            return "-1*(" + v + ")";
        }
    }

    // ---------- helpers ----------

    private double[] arr3(JsonObject o, String key) {
        double[] r = {0, 0, 0};
        if (o.has(key) && o.get(key).isJsonArray()) {
            JsonArray a = o.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < a.size(); i++) r[i] = a.get(i).getAsDouble();
        }
        return r;
    }

    private double[] rot(double[] r) {
        return new double[]{r[0] * rotSign[0], r[1] * rotSign[1], r[2] * rotSign[2]};
    }

    private JsonArray vec(double[] v) {
        JsonArray a = new JsonArray();
        for (double d : v) a.add(new JsonPrimitive(d));
        return a;
    }

    private void writeReadme(File dir) {
        File readme = new File(dir, "README.txt");
        try (FileWriter w = new FileWriter(readme)) {
            w.write("Drop Bedrock model files here and restart the server (or /bm reload after).\n\n"
                    + "For each pokemon put three same-named files (any subfolder is fine):\n"
                    + "  <species>.geo.json        (Bedrock geometry - the shape)\n"
                    + "  <species>.png             (the texture)\n"
                    + "  <species>.animation.json  (optional - animations)\n\n"
                    + "Example: pikachu.geo.json + pikachu.png + pikachu.animation.json\n\n"
                    + "On start, PokeCraft converts them to .bbmodel in\n"
                    + "plugins/BetterModel/models/ and reloads BetterModel.\n"
                    + "Name the files after the species id so they bind automatically.\n"
                    + "Requires BetterModel installed. Note: custom 3D models render on the\n"
                    + "Java client; Bedrock/mobile players see the mapped vanilla mob instead.\n"
                    + "If a model looks rotated wrong, set models.import-rotation-sign in config.yml.\n");
        } catch (Exception ignored) {
        }
    }
}
