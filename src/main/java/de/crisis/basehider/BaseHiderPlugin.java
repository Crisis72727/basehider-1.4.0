package de.crisis.basehider;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BaseHiderPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, Location> bases = new HashMap<>();
    private final Map<UUID, Zone> zones = new HashMap<>();
    private final Map<UUID, String> originalTabNames = new HashMap<>();

    private enum Zone { OUTSIDE, YELLOW, BLUE, HIDDEN }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadBases();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("setbase") != null) getCommand("setbase").setExecutor(this);
        if (getCommand("removebase") != null) getCommand("removebase").setExecutor(this);
        getLogger().info("BaseHider 1.4.0 aktiviert.");

        for (Player player : Bukkit.getOnlinePlayers()) {
            Bukkit.getScheduler().runTask(this, () -> updatePlayer(player, false));
        }
    }

    private void loadBases() {
        ConfigurationSection section = getConfig().getConfigurationSection("bases");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String path = "bases." + key;
                String worldName = getConfig().getString(path + ".world");
                if (worldName == null || Bukkit.getWorld(worldName) == null) continue;

                bases.put(uuid, new Location(Bukkit.getWorld(worldName),
                        getConfig().getInt(path + ".x"),
                        getConfig().getInt(path + ".y"),
                        getConfig().getInt(path + ".z")));
            } catch (IllegalArgumentException exception) {
                getLogger().warning("Ungültige Base-UUID: " + key);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl kann nur von einem Spieler benutzt werden.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (command.getName().equalsIgnoreCase("setbase")) {
            Location location = player.getLocation();
            String path = "bases." + uuid;
            getConfig().set(path + ".world", location.getWorld().getName());
            getConfig().set(path + ".x", location.getBlockX());
            getConfig().set(path + ".y", location.getBlockY());
            getConfig().set(path + ".z", location.getBlockZ());
            saveConfig();
            bases.put(uuid, location.clone());
            zones.remove(uuid);
            showPlayer(player);
            player.sendMessage(ChatColor.GREEN + "Deine Base wurde gesetzt.");
            updatePlayer(player, true);
            return true;
        }

        if (command.getName().equalsIgnoreCase("removebase")) {
            if (!bases.containsKey(uuid)) {
                player.sendMessage(ChatColor.YELLOW + "Du hast keine Base gesetzt.");
                return true;
            }
            getConfig().set("bases." + uuid, null);
            saveConfig();
            bases.remove(uuid);
            zones.remove(uuid);
            showPlayer(player);
            player.sendMessage(ChatColor.GREEN + "Deine Base wurde entfernt.");
            return true;
        }
        return false;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX()
                && from.getBlockZ() == to.getBlockZ())) return;
        updatePlayer(event.getPlayer(), false);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Bukkit.getScheduler().runTask(this, () -> updatePlayer(event.getPlayer(), false));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> updatePlayer(joining, false));
        Bukkit.getScheduler().runTask(this, () -> refreshVisibilityFor(joining));
    }

    private void updatePlayer(Player player, boolean baseWasJustSet) {
        Zone oldZone = zones.getOrDefault(player.getUniqueId(), Zone.OUTSIDE);
        Zone newZone = calculateZone(player);
        handleTransition(player, oldZone, newZone, baseWasJustSet);
        zones.put(player.getUniqueId(), newZone);
    }

    private Zone calculateZone(Player player) {
        Location base = bases.get(player.getUniqueId());
        Location location = player.getLocation();
        if (base == null || base.getWorld() == null
                || !base.getWorld().equals(location.getWorld())) {
            return Zone.OUTSIDE;
        }

        int dx = Math.abs(location.getBlockX() - base.getBlockX());
        int dz = Math.abs(location.getBlockZ() - base.getBlockZ());
        int distance = Math.max(dx, dz);

        if (distance <= 20) return Zone.HIDDEN;
        if (distance < 25) return Zone.BLUE;
        if (distance <= 30) return Zone.YELLOW;
        return Zone.OUTSIDE;
    }

    private void handleTransition(Player player, Zone oldZone, Zone newZone, boolean baseWasJustSet) {
        if (oldZone == newZone && !baseWasJustSet) return;

        if (baseWasJustSet) {
            if (newZone == Zone.HIDDEN) {
                hidePlayer(player);
                player.sendMessage(ChatColor.GREEN + "Du bist jetzt unsichtbar.");
            }
            return;
        }

        if (newZone == Zone.HIDDEN && oldZone != Zone.HIDDEN) {
            hidePlayer(player);
            player.sendMessage(ChatColor.GREEN + "Du bist jetzt unsichtbar.");
            return;
        }

        if ((oldZone == Zone.HIDDEN || oldZone == Zone.BLUE || oldZone == Zone.YELLOW)
                && newZone == Zone.OUTSIDE) {
            showPlayer(player);
            player.sendMessage(ChatColor.RED + "Du bist jetzt sichtbar.");
        }
    }

    private void refreshVisibilityFor(Player joining) {
        for (Map.Entry<UUID, Zone> entry : zones.entrySet()) {
            if (!isHidden(entry.getValue())) continue;
            Player hidden = Bukkit.getPlayer(entry.getKey());
            if (hidden != null && hidden.isOnline() && !hidden.equals(joining)) {
                joining.hidePlayer(this, hidden);
            }
        }
    }

    private boolean isHidden(Zone zone) {
        return zone == Zone.HIDDEN || zone == Zone.BLUE;
    }

    private void hidePlayer(Player target) {
        originalTabNames.putIfAbsent(target.getUniqueId(),
                target.getPlayerListName() == null ? target.getName() : target.getPlayerListName());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(target)) viewer.hidePlayer(this, target);
        }
        target.setPlayerListName(ChatColor.DARK_GRAY + target.getName());
    }

    private void showPlayer(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(target)) viewer.showPlayer(this, target);
        }
        String oldName = originalTabNames.remove(target.getUniqueId());
        target.setPlayerListName(oldName == null ? target.getName() : oldName);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        zones.remove(uuid);
        originalTabNames.remove(uuid);
    }
}
