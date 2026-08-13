package id.speedplayer;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SpeedPlayerPlugin extends JavaPlugin {

    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, Long> lastTimes = new HashMap<>();
    private final Map<UUID, Boolean> enabled = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    private BukkitTask updateTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        boolean showOnJoin = getConfig().getBoolean("show-on-join", true);

        for (Player player : Bukkit.getOnlinePlayers()) {
            enabled.put(player.getUniqueId(), showOnJoin);

            if (showOnJoin) {
                createBar(player);
            }
        }

        long interval = Math.max(
                1L,
                getConfig().getLong("update-ticks", 2L)
        );

        updateTask = Bukkit.getScheduler().runTaskTimer(
                this,
                this::updatePlayers,
                interval,
                interval
        );

        getLogger().info("SpeedPlayer enabled.");
    }

    @Override
    public void onDisable() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            BossBar bar = bars.get(player.getUniqueId());

            if (bar != null) {
                player.hideBossBar(bar);
            }
        }

        bars.clear();
        enabled.clear();
        lastLocations.clear();
        lastTimes.clear();
    }

    private void updatePlayers() {
        long now = System.nanoTime();

        for (Player player : Bukkit.getOnlinePlayers()) {

            UUID id = player.getUniqueId();

            if (!enabled.getOrDefault(
                    id,
                    getConfig().getBoolean("show-on-join", true)
            )) {
                continue;
            }

            if (!bars.containsKey(id)) {
                createBar(player);
            }

            Location current = player.getLocation();

            Location previous = lastLocations.get(id);
            Long previousTime = lastTimes.get(id);

            double speed = 0.0;

            if (previous != null
                    && previousTime != null
                    && sameWorld(previous, current)
                    && !shouldIgnore(player)) {

                double dx = current.getX() - previous.getX();
                double dy = current.getY() - previous.getY();
                double dz = current.getZ() - previous.getZ();

                double distance = Math.sqrt(
                        dx * dx +
                        dy * dy +
                        dz * dz
                );

                double seconds =
                        (now - previousTime) / 1_000_000_000.0;

                if (seconds > 0.0 && seconds < 2.0) {
                    speed = distance / seconds;
                }
            }

            lastLocations.put(id, current.clone());
            lastTimes.put(id, now);

            updateBar(player, speed);
        }
    }

    private boolean shouldIgnore(Player player) {

        if (getConfig().getBoolean("ignore-vehicles", true)
                && player.isInsideVehicle()) {
            return true;
        }

        return getConfig().getBoolean("ignore-spectators", false)
                && player.getGameMode() == GameMode.SPECTATOR;
    }

    private boolean sameWorld(Location first, Location second) {
        return first.getWorld() != null
                && first.getWorld().equals(second.getWorld());
    }

    private void createBar(Player player) {

        UUID id = player.getUniqueId();

        BossBar oldBar = bars.get(id);

        if (oldBar != null) {
            player.hideBossBar(oldBar);
        }

        BossBar bar = BossBar.bossBar(
                Component.text(
                        "⚡ SPEED: 0.00 blocks/s",
                        NamedTextColor.GREEN
                ),
                0.0f,
                BossBar.Color.GREEN,
                BossBar.Overlay.PROGRESS
        );

        player.showBossBar(bar);

        bars.put(id, bar);
    }

    private void updateBar(Player player, double speed) {

        BossBar bar = bars.get(player.getUniqueId());

        if (bar == null) {
            return;
        }

        double maxDisplay = Math.max(
                0.1,
                getConfig().getDouble(
                        "max-display-speed",
                        10.0
                )
        );

        float progress = (float) Math.min(
                1.0,
                speed / maxDisplay
        );

        double fastThreshold =
                getConfig().getDouble(
                        "fast-threshold",
                        6.0
                );

        double veryFastThreshold =
                getConfig().getDouble(
                        "very-fast-threshold",
                        10.0
                );

        BossBar.Color color;
        NamedTextColor textColor;

        if (speed >= veryFastThreshold) {

            color = BossBar.Color.RED;
            textColor = NamedTextColor.RED;

        } else if (speed >= fastThreshold) {

            color = BossBar.Color.YELLOW;
            textColor = NamedTextColor.YELLOW;

        } else {

            color = BossBar.Color.GREEN;
            textColor = NamedTextColor.GREEN;
        }

        bar.name(
                Component.text(
                        String.format(
                                "⚡ SPEED: %.2f blocks/s",
                                speed
                        ),
                        textColor
                )
        );

        bar.progress(progress);
        bar.color(color);
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    "Only players can use this command."
            );
            return true;
        }

        UUID id = player.getUniqueId();

        boolean current = enabled.getOrDefault(
                id,
                getConfig().getBoolean(
                        "show-on-join",
                        true
                )
        );

        if (args.length == 0) {

            current = !current;

        } else if (args[0].equalsIgnoreCase("on")) {

            current = true;

        } else if (args[0].equalsIgnoreCase("off")) {

            current = false;

        } else {

            player.sendMessage(
                    Component.text(
                            "Gunakan: /speed [on|off]",
                            NamedTextColor.YELLOW
                    )
            );

            return true;
        }

        enabled.put(id, current);

        if (current) {

            createBar(player);

            lastLocations.remove(id);
            lastTimes.remove(id);

            player.sendMessage(
                    Component.text(
                            "Speed display diaktifkan.",
                            NamedTextColor.GREEN
                    )
            );

        } else {

            BossBar bar = bars.remove(id);

            if (bar != null) {
                player.hideBossBar(bar);
            }

            lastLocations.remove(id);
            lastTimes.remove(id);

            player.sendMessage(
                    Component.text(
                            "Speed display dimatikan.",
                            NamedTextColor.RED
                    )
            );
        }

        return true;
    }
}
