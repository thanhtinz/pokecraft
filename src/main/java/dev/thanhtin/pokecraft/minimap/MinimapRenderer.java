package dev.thanhtin.pokecraft.minimap;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/**
 * Draws cursors for nearby wild pokemon and players on top of the vanilla
 * terrain map. Filled maps render on Bedrock through Geyser, so this is the
 * cross-platform way to give players a working minimap.
 */
public class MinimapRenderer extends MapRenderer {
    private final PokeCraftPlugin plugin;
    private long lastRender;

    public MinimapRenderer(PokeCraftPlugin plugin) {
        super(true); // contextual: render() gets the viewing player
        this.plugin = plugin;
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        long now = System.currentTimeMillis();
        if (now - lastRender < 400) return; // throttle
        lastRender = now;

        MapCursorCollection cursors = new MapCursorCollection();
        int scale = 1 << view.getScale().getValue(); // blocks per pixel
        int centerX = view.getCenterX();
        int centerZ = view.getCenterZ();
        int radius = plugin.getConfig().getInt("minimap.radius", 100);

        // self, at the centre, pointing where the player looks
        cursors.addCursor(cursor(0, 0, yawToDir(player.getLocation().getYaw()),
                MapCursor.Type.WHITE_POINTER));

        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            int cx = pixel(e.getLocation().getBlockX() - centerX, scale);
            int cz = pixel(e.getLocation().getBlockZ() - centerZ, scale);
            if (cx > 200 || cz > 200) continue; // off the map (sentinel)
            if (plugin.entities().isWild(e)) {
                cursors.addCursor(cursor(cx, cz, (byte) 8, MapCursor.Type.RED_MARKER));
            } else if (e instanceof Player) {
                cursors.addCursor(cursor(cx, cz, (byte) 8, MapCursor.Type.BLUE_POINTER));
            }
        }
        canvas.setCursors(cursors);
    }

    private MapCursor cursor(int x, int y, byte dir, MapCursor.Type type) {
        return new MapCursor((byte) x, (byte) y, dir, type, true);
    }

    /** World-block delta -> map cursor coordinate (half-pixels), or 999 if off-map. */
    private int pixel(int worldDelta, int blocksPerPixel) {
        int px = (worldDelta / Math.max(1, blocksPerPixel)) * 2;
        return (px < -128 || px > 127) ? 999 : px;
    }

    private byte yawToDir(float yaw) {
        int dir = Math.round(yaw / 22.5f) & 0x0F;
        return (byte) dir;
    }
}
