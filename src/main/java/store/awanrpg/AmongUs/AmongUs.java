package com.amongus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class AmongUsPlugin extends JavaPlugin implements Listener, CommandExecutor {

    public enum GameState { LOBBY, IN_GAME, MEETING }

    private GameState currentState = GameState.LOBBY;
    private final Set<UUID> impostors = new HashSet<>();
    private final Set<UUID> crewmates = new HashSet<>();
    
    private final Map<UUID, UUID> votes = new HashMap<>();
    private final Set<UUID> hasVoted = new HashSet<>();
    private final String GUI_TITLE = ChatColor.RED + "Emergency Meeting - Vote!";

    private boolean isLightsSabotaged = false;

    private final Map<UUID, Integer> playerTasksRemaining = new HashMap<>();
    private final int TASKS_PER_CREWMATE = 3;
    private int totalTasksRequired = 0;
    private int totalTasksCompleted = 0;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("amongus")).setExecutor(this);
        Objects.requireNonNull(getCommand("sabotage")).setExecutor(this);
        getLogger().info("AmongUsPlugin berhasil diaktifkan!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Command ini hanya bisa dijalankan oleh pemain!");
            return true;
        }

        Player player = (Player) sender;

        if (label.equalsIgnoreCase("amongus")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("start")) {
                startGame();
                return true;
            }
            player.sendMessage(ChatColor.RED + "Gunakan: /amongus start");
            return true;
        }

        if (label.equalsIgnoreCase("sabotage")) {
            if (currentState != GameState.IN_GAME) {
                player.sendMessage(ChatColor.RED + "Sabotage hanya bisa dilakukan saat permainan berlangsung!");
                return true;
            }

            if (!impostors.contains(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Hanya Impostor yang bisa melakukan Sabotage!");
                return true;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("lights")) {
                triggerLightsSabotage(player);
                return true;
            }

            player.sendMessage(ChatColor.RED + "Gunakan: /sabotage lights");
            return true;
        }

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
        votes.clear();
        hasVoted.clear();
        playerTasksRemaining.clear();
        isLightsSabotaged = false;
        totalTasksCompleted = 0;

        Collections.shuffle(players);
        Player impostorPlayer = players.get(0);
        impostors.add(impostorPlayer.getUniqueId());

        for (Player p : players) {
            p.removeAllCustomEffects();
            if (!p.getUniqueId().equals(impostorPlayer.getUniqueId())) {
                crewmates.add(p.getUniqueId());
                playerTasksRemaining.put(p.getUniqueId(), TASKS_PER_CREWMATE);
                p.sendTitle(ChatColor.GREEN + "CREWMATE", ChatColor.WHITE + "Selesaikan " + TASKS_PER_CREWMATE + " Task di Crafting Table!", 10, 70, 20);
            }
        }

        totalTasksRequired = crewmates.size() * TASKS_PER_CREWMATE;

        impostorPlayer.sendTitle(ChatColor.RED + "IMPOSTOR", ChatColor.GRAY + "Eliminasi semua crewmate!", 10, 70, 20);
        Bukkit.broadcastMessage(ChatColor.GOLD + "[AmongUs] Permainan dimulai! Total Task Crewmate: " + totalTasksRequired);
    }

    private void triggerLightsSabotage(Player impostor) {
        if (isLightsSabotaged) {
            impostor.sendMessage(ChatColor.RED + "Lampu sudah dalam keadaan mati/disabotase!");
            return;
        }

        isLightsSabotaged = true;
        Bukkit.broadcastMessage(ChatColor.RED + "=================================");
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "⚠ SABOTAGE: Lampu dipadamkan!");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Crewmate harus klik Lever untuk menyalakan lampu kembali.");
        Bukkit.broadcastMessage(ChatColor.RED + "=================================");

        for (UUID uuid : crewmates) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 1, false, false));
                p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Material clickedType = event.getClickedBlock().getType();

            if (currentState == GameState.IN_GAME && clickedType == Material.IRON_TRAPDOOR) {
                if (impostors.contains(player.getUniqueId())) {
                    Location loc = player.getLocation();
                    loc.setY(loc.getY() - 2);
                    player.teleport(loc);
                    player.sendMessage(ChatColor.RED + "[Vent] Kamu menggunakan vent!");
                } else {
                    player.sendMessage(ChatColor.RED + "Hanya Impostor yang bisa menggunakan Vent!");
                }
            }

            if (currentState == GameState.IN_GAME && (clickedType == Material.OAK_BUTTON || clickedType == Material.POLISHED_BLACKSTONE_BUTTON)) {
                triggerEmergencyMeeting(player);
            }

            if (currentState == GameState.IN_GAME && clickedType == Material.LEVER && isLightsSabotaged) {
                fixLightsSabotage(player);
            }

            if (currentState == GameState.IN_GAME && clickedType == Material.CRAFTING_TABLE) {
                event.setCancelled(true);
                completeTaskForPlayer(player);
            }
        }
    }

    private void completeTaskForPlayer(Player player) {
        if (impostors.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Impostor tidak bisa menyelesaikan Task!");
            return;
        }

        int remaining = playerTasksRemaining.getOrDefault(player.getUniqueId(), 0);
        if (remaining <= 0) {
            player.sendMessage(ChatColor.GREEN + "Semua task kamu sudah selesai!");
            return;
        }

        remaining--;
        totalTasksCompleted++;
        playerTasksRemaining.put(player.getUniqueId(), remaining);

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        player.sendMessage(ChatColor.GREEN + "[Task] Kamu menyelesaikan 1 Task! (Sisa task kamu: " + remaining + ")");
        Bukkit.broadcastMessage(ChatColor.AQUA + "[Task Progress] " + totalTasksCompleted + "/" + totalTasksRequired + " total task selesai.");

        checkWinCondition();
    }

    private void fixLightsSabotage(Player fixer) {
        isLightsSabotaged = false;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.DARKNESS);
            p.playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 1.0f, 1.0f);
        }

        Bukkit.broadcastMessage(ChatColor.GREEN + "[Sabotage] " + fixer.getName() + " telah memperbaiki lampu!");
    }

    private void triggerEmergencyMeeting(Player caller) {
        currentState = GameState.MEETING;
        votes.clear();
        hasVoted.clear();

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.DARKNESS);
        }

        Bukkit.broadcastMessage(ChatColor.RED + "=================================");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "[EMERGENCY MEETING] dipanggil oleh " + ChatColor.BOLD + caller.getName());
        Bukkit.broadcastMessage(ChatColor.RED + "=================================");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            p.sendTitle(ChatColor.RED + "EMERGENCY MEETING", ChatColor.YELLOW + "Pilih pemain di GUI!", 10, 60, 20);
            openVotingGUI(p);
        }

        Bukkit.getScheduler().runTaskLater(this, this::tallyVotesAndEject, 30 * 20L);
    }

    private void openVotingGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);

        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 18) break;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName(ChatColor.GREEN + target.getName());
                meta.setLore(Collections.singletonList(ChatColor.GRAY + "Klik untuk memvoting " + target.getName()));
                head.setItemMeta(meta);
            }
            gui.setItem(slot++, head);
        }

        ItemStack skipItem = new ItemStack(Material.BARRIER);
        ItemMeta skipMeta = skipItem.getItemMeta();
        if (skipMeta != null) {
            skipMeta.setDisplayName(ChatColor.RED + "SKIP VOTE");
            skipMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Klik untuk melewati vote"));
            skipItem.setItemMeta(skipMeta);
        }
        gui.setItem(22, skipItem);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player voter = (Player) event.getWhoClicked();

        if (currentState != GameState.MEETING) return;
        if (hasVoted.contains(voter.getUniqueId())) {
            voter.sendMessage(ChatColor.RED + "Kamu sudah menggunakan hak pilihmu!");
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        if (clicked.getType() == Material.BARRIER) {
            hasVoted.add(voter.getUniqueId());
            voter.sendMessage(ChatColor.YELLOW + "Kamu telah memilih untuk SKIP vote.");
            voter.closeInventory();
            checkAllVoted();
            return;
        }

        if (clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                UUID targetUUID = meta.getOwningPlayer().getUniqueId();
                String targetName = meta.getOwningPlayer().getName();

                votes.put(voter.getUniqueId(), targetUUID);
                hasVoted.add(voter.getUniqueId());

                voter.sendMessage(ChatColor.GREEN + "Kamu memvoting: " + targetName);
                voter.closeInventory();
                checkAllVoted();
            }
        }
    }

    private void checkAllVoted() {
        if (hasVoted.size() >= Bukkit.getOnlinePlayers().size()) {
            tallyVotesAndEject();
        }
    }

    private void tallyVotesAndEject() {
        if (currentState != GameState.MEETING) return;

        currentState = GameState.IN_GAME;

        Map<UUID, Integer> voteCounts = new HashMap<>();
        for (UUID votedTarget : votes.values()) {
            voteCounts.put(votedTarget, voteCounts.getOrDefault(votedTarget, 0) + 1);
        }

        UUID mostVotedUUID = null;
        int maxVotes = 0;
        boolean isTie = false;

        for (Map.Entry<UUID, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                mostVotedUUID = entry.getKey();
                isTie = false;
            } else if (entry.getValue() == maxVotes) {
                isTie = true;
            }
        }

        if (isTie || mostVotedUUID == null) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[Meeting] Hasil seimbang atau mayoritas skip! Tidak ada yang dikeluarkan.");
        } else {
            Player ejectedPlayer = Bukkit.getPlayer(mostVotedUUID);
            if (ejectedPlayer != null) {
                boolean wasImpostor = impostors.contains(ejectedPlayer.getUniqueId());
                Bukkit.broadcastMessage(ChatColor.RED + ejectedPlayer.getName() + " telah dikeluarkan!");
                
                if (wasImpostor) {
                    Bukkit.broadcastMessage(ChatColor.RED + ejectedPlayer.getName() + " adalah Impostor!");
                    impostors.remove(ejectedPlayer.getUniqueId());
                } else {
                    Bukkit.broadcastMessage(ChatColor.GRAY + ejectedPlayer.getName() + " BUKAN Impostor.");
                    crewmates.remove(ejectedPlayer.getUniqueId());
                }

                ejectedPlayer.setHealth(0);
            }
        }

        if (isLightsSabotaged) {
            for (UUID uuid : crewmates) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 1, false, false));
                }
            }
        }

        checkWinCondition();
    }

    private void checkWinCondition() {
        if (impostors.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "=================================");
            Bukkit.broadcastMessage(ChatColor.GREEN + "CREWMATES WIN! Semua Impostor telah tereliminasi/di-eject.");
            Bukkit.broadcastMessage(ChatColor.GREEN + "=================================");
            endGame();
        } else if (totalTasksRequired > 0 && totalTasksCompleted >= totalTasksRequired) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "=================================");
            Bukkit.broadcastMessage(ChatColor.GREEN + "CREWMATES WIN! Semua Task telah diselesaikan.");
            Bukkit.broadcastMessage(ChatColor.GREEN + "=================================");
            endGame();
        } else if (impostors.size() >= crewmates.size()) {
            Bukkit.broadcastMessage(ChatColor.RED + "=================================");
            Bukkit.broadcastMessage(ChatColor.RED + "IMPOSTORS WIN! Jumlah Impostor menyamai Crewmates.");
            Bukkit.broadcastMessage(ChatColor.RED + "=================================");
            endGame();
        }
    }

    private void endGame() {
        currentState = GameState.LOBBY;
        isLightsSabotaged = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.removeAllCustomEffects();
        }
    }
}
rivate void openVotingGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);

        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 18) break;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName(ChatColor.GREEN + target.getName());
                meta.setLore(Collections.singletonList(ChatColor.GRAY + "Klik untuk memvoting " + target.getName()));
                head.setItemMeta(meta);
            }
            gui.setItem(slot++, head);
        }

        ItemStack skipItem = new ItemStack(Material.BARRIER);
        ItemMeta skipMeta = skipItem.getItemMeta();
        if (skipMeta != null) {
            skipMeta.setDisplayName(ChatColor.RED + "SKIP VOTE");
            skipMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Klik untuk melewati vote"));
            skipItem.setItemMeta(skipMeta);
        }
        gui.setItem(22, skipItem);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player voter = (Player) event.getWhoClicked();

        if (currentState != GameState.MEETING) return;
        if (hasVoted.contains(voter.getUniqueId())) {
            voter.sendMessage(ChatColor.RED + "Kamu sudah menggunakan hak pilihmu!");
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        if (clicked.getType() == Material.BARRIER) {
            hasVoted.add(voter.getUniqueId());
            voter.sendMessage(ChatColor.YELLOW + "Kamu telah memilih untuk SKIP vote.");
            voter.closeInventory();
            checkAllVoted();
            return;
        }

        if (clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                UUID targetUUID = meta.getOwningPlayer().getUniqueId();
                String targetName = meta.getOwningPlayer().getName();

                votes.put(voter.getUniqueId(), targetUUID);
                hasVoted.add(voter.getUniqueId());

                voter.sendMessage(ChatColor.GREEN + "Kamu memvoting: " + targetName);
                voter.closeInventory();
                checkAllVoted();
            }
        }
    }

    private void checkAllVoted() {
        if (hasVoted.size() >= Bukkit.getOnlinePlayers().size()) {
            tallyVotesAndEject();
        }
    }

    private void tallyVotesAndEject() {
        if (currentState != GameState.MEETING) return;

        currentState = GameState.IN_GAME;

        Map<UUID, Integer> voteCounts = new HashMap<>();
        for (UUID votedTarget : votes.values()) {
            voteCounts.put(votedTarget, voteCounts.getOrDefault(votedTarget, 0) + 1);
        }

        UUID mostVotedUUID = null;
        int maxVotes = 0;
        boolean isTie = false;

        for (Map.Entry<UUID, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                mostVotedUUID = entry.getKey();
                isTie = false;
            } else if (entry.getValue() == maxVotes) {
                isTie = true;
            }
        }

        if (isTie || mostVotedUUID == null) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[Meeting] Hasil seimbang atau mayoritas skip! Tidak ada yang dikeluarkan.");
        } else {
            Player ejectedPlayer = Bukkit.getPlayer(mostVotedUUID);
            if (ejectedPlayer != null) {
                boolean wasImpostor = impostors.contains(ejectedPlayer.getUniqueId());
                Bukkit.broadcastMessage(ChatColor.RED + ejectedPlayer.getName() + " telah dikeluarkan!");
                
                if (wasImpostor) {
                    Bukkit.broadcastMessage(ChatColor.RED + ejectedPlayer.getName() + " adalah Impostor!");
                    impostors.remove(ejectedPlayer.getUniqueId());
                } else {
                    Bukkit.broadcastMessage(ChatColor.GRAY + ejectedPlayer.getName() + " BUKAN Impostor.");
                    crewmates.remove(ejectedPlayer.getUniqueId());
                }

                ejectedPlayer.setHealth(0);
            }
        }

        if (isLightsSabotaged) {
            for (UUID uuid : crewmates) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 1, false, false));
                }
            }
        }

        checkWinCondition();
    }

    private void checkWinCondition() {
        if (impostors.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "=================================");
            Bukkit.broadcastMessage(ChatColor.GREEN + "CREWMATES WIN! Semua Impostor telah tereliminasi/di-eject.");
            Bukkit.broadcastMessage(ChatColor.GREEN + "=================================");
            endGame();
        } else if (totalTasksRequired > 0 && totalTasksCompleted >= totalTasksRequired) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "=================================");
            Bukkit.broadcastMessage(ChatColor.GREEN + "CREWMATES WIN! Semua Task telah diselesaikan.");
            Bukkit.broadcastMessage(ChatColor.GREEN + "=================================");
            endGame();
        } else if (impostors.size() >= crewmates.size()) {
            Bukkit.broadcastMessage(ChatColor.RED + "=================================");
            Bukkit.broadcastMessage(ChatColor.RED + "IMPOSTORS WIN! Jumlah Impostor menyamai Crewmates.");
            Bukkit.broadcastMessage(ChatColor.RED + "=================================");
            endGame();
        }
    }

    private void endGame() {
        currentState = GameState.LOBBY;
        isLightsSabotaged = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.removeAllCustomEffects();
        }
    }
}
LD + caller.getName());
        Bukkit.broadcastMessage(ChatColor.RED + "=================================");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            p.sendTitle(ChatColor.RED + "EMERGENCY MEETING", ChatColor.YELLOW + "Pilih pemain di GUI!", 10, 60, 20);
            openVotingGUI(p);
        }

        Bukkit.getScheduler().runTaskLater(this, this::tallyVotesAndEject, 30 * 20L);
    }

    private void openVotingGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);

        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 18) break;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName(ChatColor.GREEN + target.getName());
                meta.setLore(Collections.singletonList(ChatColor.GRAY + "Klik untuk memvoting " + target.getName()));
                head.setItemMeta(meta);
            }
            gui.setItem(slot++, head);
        }

        ItemStack skipItem = new ItemStack(Material.BARRIER);
        ItemMeta skipMeta = skipItem.getItemMeta();
        if (skipMeta != null) {
            skipMeta.setDisplayName(ChatColor.RED + "SKIP VOTE");
            skipMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Klik untuk melewati vote"));
            skipItem.setItemMeta(skipMeta);
        }
        gui.setItem(22, skipItem);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player voter = (Player) event.getWhoClicked();

        if (currentState != GameState.MEETING) return;
        if (hasVoted.contains(voter.getUniqueId())) {
            voter.sendMessage(ChatColor.RED + "Kamu sudah menggunakan hak pilihmu!");
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        if (clicked.getType() == Material.BARRIER) {
            hasVoted.add(voter.getUniqueId());
            voter.sendMessage(ChatColor.YELLOW + "Kamu telah memilih untuk SKIP vote.");
            voter.closeInventory();
            checkAllVoted();
            return;
        }

        if (clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                UUID targetUUID = meta.getOwningPlayer().getUniqueId();
                String targetName = meta.getOwningPlayer().getName();

                votes.put(voter.getUniqueId(), targetUUID);
                hasVoted.add(voter.getUniqueId());

                voter.sendMessage(ChatColor.GREEN + "Kamu memvoting: " + targetName);
                voter.closeInventory();
                checkAllVoted();
            }
        }
    }

    private void checkAllVoted() {
        if (hasVoted.size() >= Bukkit.getOnlinePlayers().size()) {
            tallyVotesAndEject();
        }
    }

    private void tallyVotesAndEject() {
        if (currentState != GameState.MEETING) return;

        currentState = GameState.IN_GAME;

        Map<UUID, Integer> voteCounts = new HashMap<>();
        for (UUID votedTarget : votes.values()) {
            voteCounts.put(votedTarget, voteCounts.getOrDefault(votedTarget, 0) + 1);
        }

        UUID mostVotedUUID = null;
        int maxVotes = 0;
        boolean isTie = false;

        for (Map.Entry<UUID, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                mostVotedUUID = entry.getKey();
                isTie = false;
            } else if (entry.getValue() == maxVotes) {
                isTie = true;
            }
        }

        if (isTie || mostVotedUUID == null) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[Meeting] Hasil seimbang atau mayoritas skip! Tidak ada yang dikeluarkan.");
        } else {
            Player ejectedPlayer = Bukkit.getPlayer(mostVotedUUID);
            if (ejectedPlayer != null) {
                boolean wasImpostor = impostors.contains(ejectedPlayer.getUniqueId());
                Bukkit.broadcastMessage(ChatColor.RED + ejectedPlayer.getName() + " telah dikeluarkan!");
                
                if (wasImpostor) {
                    Bukkit.broadcastMessage(ChatColor.RED + ejectedPlayer.getName() + " adalah Impostor!");
                    impostors.remove(ejectedPlayer.getUniqueId());
                } else {
                    Bukkit.broadcastMessage(ChatColor.GRAY + ejectedPlayer.getName() + " BUKAN Impostor.");
                    crewmates.remove(ejectedPlayer.getUniqueId());
                }

                ejectedPlayer.setHealth(0);
            }
        }

        if (isLightsSabotaged) {
            for (UUID uuid : crewmates) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 1, false, false));
                }
            }
        }

        checkWinCondition();
    }

    // --- CEK KONDISI KEMENANGAN ---
    private void checkWinCondition() {
        if (impostors.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "=================================");
            Bukkit.broadcastMessage(ChatColor.GREEN + "CREWMATES WIN! Semua Impostor telah tereliminasi/di-eject.");
            Bukkit.broadcastMessage(ChatColor.GREEN + "=================================");
            endGame();
        } else if (totalTasksRequired > 0 && totalTasksCompleted >= totalTasksRequired) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "=================================");
            Bukkit.broadcastMessage(ChatColor.GREEN + "CREWMATES WIN! Semua Task telah diselesaikan.");
            Bukkit.broadcastMessage(ChatColor.GREEN + "=================================");
            endGame();
        } else if (impostors.size() >= crewmates.size()) {
            Bukkit.broadcastMessage(ChatColor.RED + "=================================");
            Bukkit.broadcastMessage(ChatColor.RED + "IMPOSTORS WIN! Jumlah Impostor menyamai Crewmates.");
            Bukkit.broadcastMessage(ChatColor.RED + "=================================");
            endGame();
        }
    }

    private void endGame() {
        currentState = GameState.LOBBY;
        isLightsSabotaged = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.removeAllCustomEffects();
        }
    }
}
etItem(22, skipItem);

        player.openInventory(gui);
    }

    // --- INVENTORY CLICK EVENT (VOTING SYSTEM) ---
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player voter = (Player) event.getWhoClicked();

        if (currentState != GameState.MEETING) return;
        if (hasVoted.contains(voter.getUniqueId())) {
            voter.sendMessage(ChatColor.RED + "Kamu sudah menggunakan hak pilihmu!");
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        if (clicked.getType() == Material.BARRIER) {
            hasVoted.add(voter.getUniqueId());
            voter.sendMessage(ChatColor.YELLOW + "Kamu telah memilih untuk SKIP vote.");
            voter.closeInventory();
            checkAllVoted();
            return;
        }

        if (clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                UUID targetUUID = meta.getOwningPlayer().getUniqueId();
                String targetName = meta.getOwningPlayer().getName();

                votes.put(voter.getUniqueId(), targetUUID);
                hasVoted.add(voter.getUniqueId());

                voter.sendMessage(ChatColor.GREEN + "Kamu memvoting: " + targetName);
                voter.closeInventory();
                checkAllVoted();
            }
        }
    }

    private void checkAllVoted() {
        if (hasVoted.size() >= Bukkit.getOnlinePlayers().size()) {
            tallyVotesAndEject();
        }
    }

    // --- PERHITUNGAN SUARA & EJECT ---
    private void tallyVotesAndEject() {
        if (currentState != GameState.MEETING) return;

        currentState = GameState.IN_GAME;

        Map<UUID, Integer> voteCounts = new HashMap<>();
        for (UUID votedTarget : votes.values()) {
            voteCounts.put(votedTarget, voteCounts.getOrDefault(votedTarget, 0) + 1);
        }

        UUID mostVotedUUID = null;
        int maxVotes = 0;
        boolean isTie = false;

        for (Map.Entry<UUID, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                mostVotedUUID = entry.getKey();
                isTie = false;
            } else if (entry.getValue() == maxVotes) {
                isTie = true;
            }
        }

        if (isTie || mostVotedUUID == null) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[Meeting] Hasil seimbang atau mayoritas skip! Tidak ada yang dikeluarkan.");
        } else {
            Player ejectedPlayer = Bukkit.getPlayer(mostVotedUUID);
            if (ejectedPlayer != null) {
                boolean wasImpostor = impostors.contains(ejectedPlayer.getUniqueId());
                Bukkit.broadcastMessage(ChatColor.RED + ejectedPlayer.getName() + " telah dikeluarkan!");
                
                if (wasImpostor) {
                    Bukkit.broadcastMessage(ChatColor.RED + ejectedPlayer.getName() + " adalah Impostor!");
                    impostors.remove(ejectedPlayer.getUniqueId());
                } else {
                    Bukkit.broadcastMessage(ChatColor.GRAY + ejectedPlayer.getName() + " BUKAN Impostor.");
                    crewmates.remove(ejectedPlayer.getUniqueId());
                }

                ejectedPlayer.setHealth(0);
            }
        }

        // Kembalikan efek mati lampu jika sabotase belum diperbaiki saat meeting selesai
        if (isLightsSabotaged) {
            for (UUID uuid : crewmates) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 1, false, false));
                }
            }
        }

        checkWinCondition();
    }

    // --- CEK KONDISI KEMENANGAN ---
    private void checkWinCondition() {
        if (impostors.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "=================================");
            Bukkit.broadcastMessage(ChatColor.GREEN + "CREWMATES WIN! Semua Impostor berhasil dikeluarkan.");
            Bukkit.broadcastMessage(ChatColor.GREEN + "=================================");
            endGame();
        } else if (impostors.size() >= crewmates.size()) {
            Bukkit.broadcastMessage(ChatColor.RED + "=================================");
            Bukkit.broadcastMessage(ChatColor.RED + "IMPOSTORS WIN! Jumlah Impostor menyamai Crewmates.");
            Bukkit.broadcastMessage(ChatColor.RED + "=================================");
            endGame();
        }
    }

    private void endGame() {
        currentState = GameState.LOBBY;
        isLightsSabotaged = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.removeAllCustomEffects();
        }
    }
}
etUniqueId());
                }

                // Efek eject
                ejectedPlayer.setHealth(0); // Membunuh pemain yang di-eject
            }
        }

        checkWinCondition();
    }

    private void checkWinCondition() {
        if (impostors.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "=================================");
            Bukkit.broadcastMessage(ChatColor.GREEN + "CREWMATES WIN! Semua Impostor berhasil dikeluarkan.");
            Bukkit.broadcastMessage(ChatColor.GREEN + "=================================");
            currentState = GameState.LOBBY;
        } else if (impostors.size() >= crewmates.size()) {
            Bukkit.broadcastMessage(ChatColor.RED + "=================================");
            Bukkit.broadcastMessage(ChatColor.RED + "IMPOSTORS WIN! Jumlah Impostor menyamai Crewmates.");
            Bukkit.broadcastMessage(ChatColor.RED + "=================================");
            currentState = GameState.LOBBY;
        }
    }
}
