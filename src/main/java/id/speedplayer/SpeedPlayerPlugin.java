package id.speedplayer;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

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
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SpeedPlayerPlugin extends JavaPlugin {

    /*
     * Posisi terakhir player / kendaraan.
     */
    private final Map<UUID, Location> lastLocations =
            new HashMap<>();

    /*
     * Waktu terakhir update.
     */
    private final Map<UUID, Long> lastTimes =
            new HashMap<>();

    /*
     * Status speed display.
     */
    private final Map<UUID, Boolean> enabled =
            new HashMap<>();

    /*
     * Kendaraan/mob terakhir yang dinaiki.
     */
    private final Map<UUID, UUID> lastVehicles =
            new HashMap<>();

    /*
     * Scoreboard masing-masing player.
     */
    private final Map<UUID, Scoreboard> scoreboards =
            new HashMap<>();

    private BukkitTask updateTask;


    // =========================================================
    // ENABLE
    // =========================================================

    @Override
    public void onEnable() {

        saveDefaultConfig();

        boolean showOnJoin =
                getConfig().getBoolean(
                        "show-on-join",
                        true
                );

        /*
         * Buat scoreboard untuk player
         * yang sudah online ketika plugin reload.
         */
        for (Player player :
                Bukkit.getOnlinePlayers()) {

            enabled.put(
                    player.getUniqueId(),
                    showOnJoin
            );

            if (showOnJoin) {
                createScoreboard(player);
            }
        }

        /*
         * Interval update.
         *
         * Default:
         * 2 ticks = 0.1 detik
         */
        long interval =
                Math.max(
                        1L,
                        getConfig().getLong(
                                "update-ticks",
                                2L
                        )
                );

        updateTask =
                Bukkit.getScheduler().runTaskTimer(
                        this,
                        this::updatePlayers,
                        interval,
                        interval
                );

        getLogger().info(
                "SpeedPlayer enabled."
        );
    }


    // =========================================================
    // DISABLE
    // =========================================================

    @Override
    public void onDisable() {

        if (updateTask != null) {
            updateTask.cancel();
        }

        /*
         * Kembalikan scoreboard semua player
         * ke scoreboard utama.
         */
        for (Player player :
                Bukkit.getOnlinePlayers()) {

            removeScoreboard(player);
        }

        scoreboards.clear();
        enabled.clear();
        lastLocations.clear();
        lastTimes.clear();
        lastVehicles.clear();
    }


    // =========================================================
    // UPDATE PLAYER
    // =========================================================

    private void updatePlayers() {

        long now =
                System.nanoTime();

        for (Player player :
                Bukkit.getOnlinePlayers()) {

            UUID id =
                    player.getUniqueId();

            /*
             * Cek apakah display aktif.
             */
            if (!enabled.getOrDefault(
                    id,
                    getConfig().getBoolean(
                            "show-on-join",
                            true
                    )
            )) {
                continue;
            }

            /*
             * Pastikan scoreboard tersedia.
             */
            if (!scoreboards.containsKey(id)) {
                createScoreboard(player);
            }


            // -------------------------------------------------
            // SPECTATOR
            // -------------------------------------------------

            if (getConfig().getBoolean(
                    "ignore-spectators",
                    false
            )
                    && player.getGameMode()
                    == GameMode.SPECTATOR) {

                updateScoreboard(
                        player,
                        0.0
                );

                continue;
            }


            // -------------------------------------------------
            // ENTITY YANG DIHITUNG
            // -------------------------------------------------

            Entity trackedEntity =
                    getTrackedEntity(player);

            Location current =
                    trackedEntity.getLocation();


            // -------------------------------------------------
            // CEK VEHICLE
            // -------------------------------------------------

            UUID currentVehicleId =
                    null;

            if (player.isInsideVehicle()
                    && player.getVehicle() != null) {

                currentVehicleId =
                        player.getVehicle()
                                .getUniqueId();
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


            // -------------------------------------------------
            // HITUNG SPEED
            // -------------------------------------------------

            double speed = 0.0;

            Location previous =
                    lastLocations.get(id);

            Long previousTime =
                    lastTimes.get(id);


            if (!vehicleChanged
                    && previous != null
                    && previousTime != null
                    && sameWorld(
                            previous,
                            current
                    )) {

                double dx =
                        current.getX()
                                - previous.getX();

                double dy =
                        current.getY()
                                - previous.getY();

                double dz =
                        current.getZ()
                                - previous.getZ();

                /*
                 * Jarak perpindahan.
                 */
                double distance =
                        Math.sqrt(
                                dx * dx
                                        + dy * dy
                                        + dz * dz
                        );

                /*
                 * Waktu dalam detik.
                 */
                double seconds =
                        (
                                now
                                        - previousTime
                        )
                                / 1_000_000_000.0;

                /*
                 * Hitung blocks per second.
                 */
                if (seconds > 0.0
                        && seconds < 2.0) {

                    speed =
                            distance / seconds;
                }
            }


            // -------------------------------------------------
            // SIMPAN DATA
            // -------------------------------------------------

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


            // -------------------------------------------------
            // UPDATE SIDEBAR
            // -------------------------------------------------

            updateScoreboard(
                    player,
                    speed
            );
        }
    }


    // =========================================================
    // TRACK ENTITY
    // =========================================================

    private Entity getTrackedEntity(
            Player player
    ) {

        /*
         * Jika player sedang menaiki entity,
         * gunakan entity tersebut.
         *
         * Contoh:
         *
         * Player -> Horse
         * Player -> Pig
         * Player -> Camel
         * Player -> Boat
         */
        if (player.isInsideVehicle()
                && player.getVehicle() != null) {

            return player.getVehicle();
        }

        /*
         * Jika tidak menaiki apa-apa,
         * gunakan player.
         */
        return player;
    }


    // =========================================================
    // SAME WORLD
    // =========================================================

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


    // =========================================================
    // CREATE SCOREBOARD
    // =========================================================

    private void createScoreboard(
            Player player
    ) {

        UUID id =
                player.getUniqueId();


        /*
         * Hapus scoreboard lama.
         */
        removeScoreboard(player);


        ScoreboardManager manager =
                Bukkit.getScoreboardManager();

        if (manager == null) {
            return;
        }


        /*
         * Buat scoreboard baru.
         */
        Scoreboard scoreboard =
                manager.getNewScoreboard();


        // -----------------------------------------------------
        // OBJECTIVE
        // -----------------------------------------------------

        Objective objective =
                scoreboard.registerNewObjective(
                        "speedplayer",
                        "dummy",
                        Component.text(
                                "⚡ SPEED",
                                NamedTextColor.AQUA
                        )
                );


        /*
         * Sidebar kanan.
         */
        objective.setDisplaySlot(
                DisplaySlot.SIDEBAR
        );


        /*
         * HILANGKAN ANGKA SCORE
         *
         * Tanpa ini akan muncul:
         *
         * Speed       2
         * Vehicle     1
         */
        objective.numberFormat(
                NumberFormat.blank()
        );


        // -----------------------------------------------------
        // SPEED SCORE
        // -----------------------------------------------------

        Score speedScore =
                objective.getScore(
                        "speed_value"
                );

        speedScore.setScore(2);


        // -----------------------------------------------------
        // VEHICLE SCORE
        // -----------------------------------------------------

        Score vehicleScore =
                objective.getScore(
                        "vehicle_value"
                );

        vehicleScore.setScore(1);


        // -----------------------------------------------------
        // SIMPAN
        // -----------------------------------------------------

        scoreboards.put(
                id,
                scoreboard
        );


        /*
         * Pasang scoreboard.
         */
        player.setScoreboard(
                scoreboard
        );


        /*
         * Tampilkan nilai awal.
         */
        updateScoreboard(
                player,
                0.0
        );
    }


    // =========================================================
    // UPDATE SCOREBOARD
    // =========================================================

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


        Objective objective =
                scoreboard.getObjective(
                        "speedplayer"
                );

        if (objective == null) {
            return;
        }


        // -----------------------------------------------------
        // THRESHOLD
        // -----------------------------------------------------

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


        NamedTextColor speedColor;


        /*
         * MERAH
         *
         * Sangat cepat.
         */
        if (speed >= veryFastThreshold) {

            speedColor =
                    NamedTextColor.RED;


        /*
         * KUNING
         *
         * Cepat.
         */
        } else if (speed >= fastThreshold) {

            speedColor =
                    NamedTextColor.YELLOW;


        /*
         * HIJAU
         *
         * Normal.
         */
        } else {

            speedColor =
                    NamedTextColor.GREEN;
        }


        // -----------------------------------------------------
        // SPEED TEXT
        // -----------------------------------------------------

        Score speedScore =
                objective.getScore(
                        "speed_value"
                );


        speedScore.customName(
                Component.text(
                        String.format(
                                "⚡ %.2f blocks/s",
                                speed
                        ),
                        speedColor
                )
        );


        // -----------------------------------------------------
        // VEHICLE TEXT
        // -----------------------------------------------------

        Score vehicleScore =
                objective.getScore(
                        "vehicle_value"
                );


        if (player.isInsideVehicle()
                && player.getVehicle() != null) {

            String vehicleName =
                    getVehicleName(
                            player.getVehicle()
                    );


            vehicleScore.customName(
                    Component.text(
                            vehicleName,
                            NamedTextColor.GRAY
                    )
            );


        } else {

            vehicleScore.customName(
                    Component.text(
                            "Walking",
                            NamedTextColor.GRAY
                    )
            );
        }
    }


    // =========================================================
    // ENTITY NAME
    // =========================================================

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


            /*
             * Huruf pertama kapital.
             */
            result.append(
                    Character.toUpperCase(
                            word.charAt(0)
                    )
            );


            /*
             * Sisanya tetap lowercase.
             */
            if (word.length() > 1) {

                result.append(
                        word.substring(1)
                );
            }


            result.append(" ");
        }


        return result
                .toString()
                .trim();
    }


    // =========================================================
    // REMOVE SCOREBOARD
    // =========================================================

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

                /*
                 * Kembalikan scoreboard normal.
                 */
                player.setScoreboard(
                        manager.getMainScoreboard()
                );
            }
        }
    }


    // =========================================================
    // COMMAND
    // =========================================================

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        /*
         * Hanya player.
         */
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


        // -----------------------------------------------------
        // /speed
        // -----------------------------------------------------

        if (args.length == 0) {

            current = !current;


        // -----------------------------------------------------
        // /speed on
        // -----------------------------------------------------

        } else if (
                args[0].equalsIgnoreCase(
                        "on"
                )
        ) {

            current = true;


        // -----------------------------------------------------
        // /speed off
        // -----------------------------------------------------

        } else if (
                args[0].equalsIgnoreCase(
                        "off"
                )
        ) {

            current = false;


        // -----------------------------------------------------
        // INVALID
        // -----------------------------------------------------

        } else {

            player.sendMessage(
                    Component.text(
                            "Gunakan: /speed [on|off]",
                            NamedTextColor.YELLOW
                    )
            );

            return true;
        }


        /*
         * Simpan status.
         */
        enabled.put(
                id,
                current
        );


        // -----------------------------------------------------
        // ON
        // -----------------------------------------------------

        if (current) {

            createScoreboard(player);


            /*
             * Reset perhitungan.
             */
            lastLocations.remove(id);
            lastTimes.remove(id);
            lastVehicles.remove(id);


            player.sendMessage(
                    Component.text(
                            "Speed display diaktifkan.",
                            NamedTextColor.GREEN
                    )
            );


        // -----------------------------------------------------
        // OFF
        // -----------------------------------------------------

        } else {

            removeScoreboard(player);


            /*
             * Reset data.
             */
            lastLocations.remove(id);
            lastTimes.remove(id);
            lastVehicles.remove(id);


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
