package com.amongus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class AmongUsPlugin extends JavaPlugin implements Listener, CommandExecutor {

    public enum GameState { LOBBY, IN_GAME, MEETING }

    private GameState currentState = GameState.LOBBY;
    private final Set<UUID> impostors = new HashSet<>();
    private final Set<UUID> crewmates = new HashSet<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("amongus")).setExecutor(this);
        getLogger().info("AmongUsPlugin berhasil diaktifkan!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("start")) {
            startGame();
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Gunakan: /amongus start");
        return true;
    }

    private void startGame() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.size() < 2) {
            Bukkit.broadcastMessage(ChatColor.RED + "[AmongUs] Butuh minimal 2 pemain untuk memulai!");
            return;
        }

        currentState = GameState.IN_GAME;
        impostors.clear();
        crewmates.clear();

        // Pilih 1 Impostor secara acak
        Collections.shuffle(players);
        Player impostorPlayer = players.get(0);
        impostors.add(impostorPlayer.getUniqueId());

        for (Player p : players) {
            if (!p.getUniqueId().equals(impostorPlayer.getUniqueId())) {
                crewmates.add(p.getUniqueId());
                p.sendTitle(ChatColor.GREEN + "CREWMATE", ChatColor.WHITE + "Selesaikan semua task!", 10, 70, 20);
            }
        }

        impostorPlayer.sendTitle(ChatColor.RED + "IMPOSTOR", ChatColor.GRAY + "Eliminasi semua crewmate!", 10, 70, 20);
        Bukkit.broadcastMessage(ChatColor.GOLD + "[AmongUs] Permainan dimulai!");
    }

    @EventHandler
    public void onVentInteract(PlayerInteractEvent event) {
        if (currentState != GameState.IN_GAME) return;

        Player player = event.getPlayer();

        // Fitur Vent khusus Impostor saat klik kanan Iron Trapdoor
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            if (event.getClickedBlock().getType() == Material.IRON_TRAPDOOR) {
                if (impostors.contains(player.getUniqueId())) {
                    Location loc = player.getLocation();
                    loc.setY(loc.getY() - 2); // Teleportasi sederhana ke bawah vent
                    player.teleport(loc);
                    player.sendMessage(ChatColor.RED + "[Vent] Kamu menggunakan vent!");
                } else {
                    player.sendMessage(ChatColor.RED + "Hanya Impostor yang bisa menggunakan Vent!");
                }
            }
        }
    }
          }
