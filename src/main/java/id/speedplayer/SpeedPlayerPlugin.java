package id.speedplayer;

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

    /*
     * Menyimpan kendaraan/mob yang sedang dinaiki.
     * Digunakan untuk mendeteksi saat player naik/turun kendaraan.
     */
    private final Map<UUID, UUID> lastVehicles = new HashMap<>();

    /*
     * Scoreboard masing-masing player.
     */
    private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();

    private BukkitTask updateTask;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        boolean showOnJoin =
                getConfig().getBoolean(
                        "show-on-join",
                        true
                );

        /*
         * Buat scoreboard untuk player yang
         * sudah online ketika plugin di-reload.
         */
        for (Player player : Bukkit.getOnlinePlayers()) {

            enabled.put(
                    player.getUniqueId(),
                    showOnJoin
            );

            if (showOnJoin) {
                createScoreboard(player);
            }
        }

        /*
         * Interval update speed.
         *
         * Default:
         * 2 ticks = 0.1 detik
         */
        long interval = Math.max(
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

    @Override
    public void onDisable() {

        if (updateTask != null) {
            updateTask.cancel();
        }

        /*
         * Kembalikan scoreboard player ke scoreboard utama.
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

    /**
     * Update speed semua player.
     */
    private void updatePlayers() {

        long now = System.nanoTime();

        for (Player player :
                Bukkit.getOnlinePlayers()) {

            UUID id =
                    player.getUniqueId();

            /*
             * Apakah speed display aktif?
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

            /*
             * Spectator.
             */
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

            /*
             * Entity yang dihitung.
             *
             * Jalan kaki:
             * Player
             *
             * Naik Horse:
             * Horse
             *
             * Naik Pig:
             * Pig
             *
             * Naik Camel:
             * Camel
             *
             * Naik Boat:
             * Boat
             *
             * dll.
             */
            Entity trackedEntity =
                    getTrackedEntity(player);

            Location current =
                    trackedEntity.getLocation();

            /*
             * ID kendaraan yang sedang dinaiki.
             */
            UUID currentVehicleId = null;

            if (player.isInsideVehicle()
                    && player.getVehicle() != null) {

                currentVehicleId =
                        player.getVehicle()
                                .getUniqueId();
            }

            UUID previousVehicleId =
                    lastVehicles.get(id);

            /*
             * Deteksi perubahan kendaraan.
             *
             * Contoh:
             *
             * Player -> Horse
             *
             * Horse -> Player
             */
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

            /*
             * Hitung speed hanya jika:
             *
             * - bukan baru naik/turun kendaraan
             * - lokasi sebelumnya tersedia
             * - waktu sebelumnya tersedia
             * - masih berada di world yang sama
             */
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
                 * Jarak perpindahan dalam blocks.
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
                 * Hindari pembagian dengan nol
                 * dan waktu yang terlalu lama.
                 */
                if (seconds > 0.0
                        && seconds < 2.0) {

                    speed =
                            distance / seconds;
                }
            }

            /*
             * Simpan posisi terbaru.
             */
            lastLocations.put(
                    id,
                    current.clone()
            );

            /*
             * Simpan waktu terbaru.
             */
            lastTimes.put(
                    id,
                    now
            );

            /*
             * Simpan kendaraan terbaru.
             */
            if (currentVehicleId != null) {

                lastVehicles.put(
                        id,
                        currentVehicleId
                );

            } else {

                lastVehicles.remove(id);
            }

            /*
             * Update tampilan scoreboard.
             */
            updateScoreboard(
                    player,
                    speed
            );
        }
    }

    /**
     * Menentukan entity yang digunakan untuk
     * menghitung speed.
     */
    private Entity getTrackedEntity(
            Player player
    ) {

        /*
         * Jika sedang menaiki sesuatu,
         * hitung pergerakan kendaraan/mob.
         */
        if (player.isInsideVehicle()
                && player.getVehicle() != null) {

            return player.getVehicle();
        }

        /*
         * Jika berjalan kaki,
         * hitung pergerakan player.
         */
        return player;
    }

    /**
     * Mengecek apakah dua lokasi berada
     * di world yang sama.
     */
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

    /**
     * Membuat scoreboard sidebar.
     */
    private void createScoreboard(
            Player player
    ) {

        UUID id =
                player.getUniqueId();

        /*
         * Hapus scoreboard lama jika ada.
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

        /*
         * Objective.
         */
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
         * Tampilkan di sisi kanan layar.
         */
        objective.setDisplaySlot(
                DisplaySlot.SIDEBAR
        );

        /*
         * Team untuk nilai speed.
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

        /*
         * Team untuk status kendaraan.
         */
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

        /*
         * Pasang scoreboard ke player.
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

    /**
     * Update teks sidebar.
     */
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

        /*
         * Threshold warna.
         */
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
         * Merah = sangat cepat
         */
        if (speed >= veryFastThreshold) {

            speedColor =
                    NamedTextColor.RED;

        /*
         * Kuning = cepat
         */
        } else if (speed >= fastThreshold) {

            speedColor =
                    NamedTextColor.YELLOW;

        /*
         * Hijau = normal
         */
        } else {

            speedColor =
                    NamedTextColor.GREEN;
        }

        /*
         * Tampilkan speed.
         *
         * Paper 26.2 menggunakan Component
         * untuk prefix Team.
         */
        speedTeam.prefix(
                Component.text(
                        String.format(
                                "⚡ %.2f blocks/s",
                                speed
                        ),
                        speedColor
                )
        );

        /*
         * Tampilkan kendaraan.
         */
        if (player.isInsideVehicle()
                && player.getVehicle() != null) {

            String vehicleName =
                    getVehicleName(
                            player.getVehicle()
                    );

            vehicleTeam.prefix(
                    Component.text(
                            vehicleName,
                            NamedTextColor.GRAY
                    )
            );

        } else {

            vehicleTeam.prefix(
                    Component.text(
                            "Walking",
                            NamedTextColor.GRAY
                    )
            );
        }
    }

    /**
     * Mengubah nama EntityType menjadi
     * nama yang lebih rapi.
     *
     * Contoh:
     *
     * DARK_OAK_BOAT
     * ->
     * Dark Oak Boat
     *
     * CAMEL
     * ->
     * Camel
     */
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

        return result
                .toString()
                .trim();
    }

    /**
     * Menghapus scoreboard dari player.
     */
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

    /**
     * Command:
     *
     * /speed
     * /speed on
     * /speed off
     */
    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        /*
         * Command hanya bisa digunakan player.
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

        /*
         * /speed
         *
         * Toggle.
         */
        if (args.length == 0) {

            current = !current;

        /*
         * /speed on
         */
        } else if (
                args[0].equalsIgnoreCase(
                        "on"
                )
        ) {

            current = true;

        /*
         * /speed off
         */
        } else if (
                args[0].equalsIgnoreCase(
                        "off"
                )
        ) {

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

        /*
         * Simpan status.
         */
        enabled.put(
                id,
                current
        );

        if (current) {

            /*
             * Aktifkan scoreboard.
             */
            createScoreboard(player);

            /*
             * Reset perhitungan speed.
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

        } else {

            /*
             * Matikan scoreboard.
             */
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
