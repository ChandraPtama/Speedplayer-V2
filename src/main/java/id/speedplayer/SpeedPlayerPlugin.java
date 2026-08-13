package id.speedplayer;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SpeedPlayerPlugin extends JavaPlugin {

    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, Long> lastTimes = new HashMap<>();
    private final Map<UUID, Boolean> enabled = new HashMap<>();
    private final Map<UUID, UUID> lastVehicles = new HashMap<>();

    private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();

    private BukkitTask updateTask;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        boolean showOnJoin =
                getConfig().getBoolean("show-on-join", true);

        for (Player player : Bukkit.getOnlinePlayers()) {

            enabled.put(
                    player.getUniqueId(),
                    showOnJoin
            );

            if (showOnJoin) {
                createScoreboard(player);
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
            removeScoreboard(player);
        }

        scoreboards.clear();
        enabled.clear();
        lastLocations.clear();
        lastTimes.clear();
        lastVehicles.clear();
    }

    private void updatePlayers() {

        long now = System.nanoTime();

        for (Player player : Bukkit.getOnlinePlayers()) {

            UUID id = player.getUniqueId();

            if (!enabled.getOrDefault(
                    id,
                    getConfig().getBoolean(
                            "show-on-join",
                            true
                    )
            )) {
                continue;
            }

            if (!scoreboards.containsKey(id)) {
                createScoreboard(player);
            }

            if (getConfig().getBoolean(
                    "ignore-spectators",
                    false
            ) && player.getGameMode() == GameMode.SPECTATOR) {

                updateScoreboard(player, 0.0);
                continue;
            }

            Entity trackedEntity =
                    getTrackedEntity(player);

            Location current =
                    trackedEntity.getLocation();

            UUID currentVehicleId = null;

            if (player.isInsideVehicle()
                    && player.getVehicle() != null) {

                currentVehicleId =
                        player.getVehicle().getUniqueId();
            }

            UUID previousVehicleId =
                    lastVehicles.get(id);

            boolean vehicleChanged;

            if (currentVehicleId == null) {

                vehicleChanged =
                        previousVehicleId != null;

            } else {

                vehicleChanged =
                        !currentVehicleId.equals(
                                previousVehicleId
                        );
            }

            double speed = 0.0;

            Location previous =
                    lastLocations.get(id);

            Long previousTime =
                    lastTimes.get(id);

            if (!vehicleChanged
                    && previous != null
                    && previousTime != null
                    && sameWorld(previous, current)) {

                double dx =
                        current.getX()
                                - previous.getX();

                double dy =
                        current.getY()
                                - previous.getY();

                double dz =
                        current.getZ()
                                - previous.getZ();

                double distance =
                        Math.sqrt(
                                dx * dx
                                        + dy * dy
                                        + dz * dz
                        );

                double seconds =
                        (now - previousTime)
                                / 1_000_000_000.0;

                if (seconds > 0.0
                        && seconds < 2.0) {

                    speed =
                            distance / seconds;
                }
            }

            lastLocations.put(
                    id,
                    current.clone()
            );

            lastTimes.put(
                    id,
                    now
            );

            if (currentVehicleId != null) {

                lastVehicles.put(
                        id,
                        currentVehicleId
                );

            } else {

                lastVehicles.remove(id);
            }

            updateScoreboard(
                    player,
                    speed
            );
        }
    }

    private Entity getTrackedEntity(Player player) {

        if (player.isInsideVehicle()
                && player.getVehicle() != null) {

            return player.getVehicle();
        }

        return player;
    }

    private boolean sameWorld(
            Location first,
            Location second
    ) {

        return first.getWorld() != null
                && second.getWorld() != null
                && first.getWorld().equals(
                        second.getWorld()
                );
    }

    private void createScoreboard(Player player) {

        UUID id =
                player.getUniqueId();

        removeScoreboard(player);

        ScoreboardManager manager =
                Bukkit.getScoreboardManager();

        if (manager == null) {
            return;
        }

        Scoreboard scoreboard =
                manager.getNewScoreboard();

        Objective objective =
                scoreboard.registerNewObjective(
                        "speedplayer",
                        "dummy",
                        "⚡ SPEED"
                );

        objective.setDisplaySlot(
                DisplaySlot.SIDEBAR
        );

        /*
         * Baris kosong digunakan sebagai
         * spacer agar tampilan lebih rapi.
         */
        Team speedTeam =
                scoreboard.registerNewTeam(
                        "speed"
                );

        speedTeam.addEntry(
                "speed_value"
        );

        objective.getScore(
                "speed_value"
        ).setScore(2);

        Team vehicleTeam =
                scoreboard.registerNewTeam(
                        "vehicle"
                );

        vehicleTeam.addEntry(
                "vehicle_value"
        );

        objective.getScore(
                "vehicle_value"
        ).setScore(1);

        /*
         * Simpan scoreboard.
         */
        scoreboards.put(
                id,
                scoreboard
        );

        player.setScoreboard(
                scoreboard
        );

        updateScoreboard(
                player,
                0.0
        );
    }

    private void updateScoreboard(
            Player player,
            double speed
    ) {

        Scoreboard scoreboard =
                scoreboards.get(
                        player.getUniqueId()
                );

        if (scoreboard == null) {
            return;
        }

        Team speedTeam =
                scoreboard.getTeam(
                        "speed"
                );

        Team vehicleTeam =
                scoreboard.getTeam(
                        "vehicle"
                );

        if (speedTeam == null
                || vehicleTeam == null) {
            return;
        }

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

        String prefix;

        if (speed >= veryFastThreshold) {

            prefix = "§c⚡ ";

        } else if (speed >= fastThreshold) {

            prefix = "§e⚡ ";

        } else {

            prefix = "§a⚡ ";
        }

        /*
         * Tampilkan speed.
         */
        speedTeam.prefix(
                prefix
                        + String.format(
                                "%.2f",
                                speed
                        )
                        + " §7blocks/s"
        );

        /*
         * Tampilkan jenis kendaraan.
         */
        if (player.isInsideVehicle()
                && player.getVehicle() != null) {

            String vehicleName =
                    getVehicleName(
                            player.getVehicle()
                    );

            vehicleTeam.prefix(
                    "§7" + vehicleName
            );

        } else {

            vehicleTeam.prefix(
                    "§7Walking"
            );
        }
    }

    private String getVehicleName(
            Entity entity
    ) {

        String name =
                entity.getType()
                        .name()
                        .toLowerCase()
                        .replace(
                                "_",
                                " "
                        );

        StringBuilder result =
                new StringBuilder();

        for (String word :
                name.split(" ")) {

            if (word.isEmpty()) {
                continue;
            }

            result.append(
                    Character.toUpperCase(
                            word.charAt(0)
                    )
            );

            if (word.length() > 1) {
                result.append(
                        word.substring(1)
                );
            }

            result.append(" ");
        }

        return result.toString().trim();
    }

    private void removeScoreboard(
            Player player
    ) {

        UUID id =
                player.getUniqueId();

        Scoreboard scoreboard =
                scoreboards.remove(id);

        if (scoreboard != null) {

            ScoreboardManager manager =
                    Bukkit.getScoreboardManager();

            if (manager != null) {

                player.setScoreboard(
                        manager.getMainScoreboard()
                );
            }
        }
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

        UUID id =
                player.getUniqueId();

        boolean current =
                enabled.getOrDefault(
                        id,
                        getConfig().getBoolean(
                                "show-on-join",
                                true
                        )
                );

        if (args.length == 0) {

            current = !current;

        } else if (
                args[0].equalsIgnoreCase("on")
        ) {

            current = true;

        } else if (
                args[0].equalsIgnoreCase("off")
        ) {

            current = false;

        } else {

            player.sendMessage(
                    "§eGunakan: /speed [on|off]"
            );

            return true;
        }

        enabled.put(
                id,
                current
        );

        if (current) {

            createScoreboard(player);

            lastLocations.remove(id);
            lastTimes.remove(id);
            lastVehicles.remove(id);

            player.sendMessage(
                    "§aSpeed display diaktifkan."
            );

        } else {

            removeScoreboard(player);

            lastLocations.remove(id);
            lastTimes.remove(id);
            lastVehicles.remove(id);

            player.sendMessage(
                    "§cSpeed display dimatikan."
            );
        }

        return true;
    }
}
