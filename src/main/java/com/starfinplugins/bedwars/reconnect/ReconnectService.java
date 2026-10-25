package com.starfinplugins.bedwars.reconnect;

import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.ConnectionState;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.event.ReconnectLifecycleEvent;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.service.ManagedService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** All state transitions happen on the Bukkit primary thread. */
public final class ReconnectService implements ManagedService, Listener {
    private final Plugin plugin;
    private final MatchEngineService matches;
    private final ReconnectSettings settings;
    private final ReconnectRepository repository;
    private final Map<UUID, ReconnectSession> sessions = new HashMap<UUID, ReconnectSession>();
    private final Map<UUID, Long> combatUntil = new HashMap<UUID, Long>();
    private final Map<UUID, Inventory> menus = new HashMap<UUID, Inventory>();
    private BukkitTask expirationTask;

    public ReconnectService(Plugin plugin, MatchEngineService matches, ReconnectSettings settings) {
        this.plugin = plugin;
        this.matches = matches;
        this.settings = settings;
        this.repository = new ReconnectRepository(plugin.getDataFolder(), plugin.getLogger());
    }
    @Override public String getName() { return "reconnect"; }
    @Override public void start() {
        try {
            int stale = repository.countStaleSessions();
            if (stale > 0) plugin.getLogger().warning(stale
                + " sessao(oes) de reconnect de instancia anterior invalidadas: partidas nao sobrevivem ao reinicio.");
        } catch (java.io.IOException error) {
            plugin.getLogger().warning("Metadados antigos de reconnect ignorados: " + error.getMessage());
        }
        repository.save(sessions.values());
        Bukkit.getPluginManager().registerEvents(this, plugin);
        expirationTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { expire(System.currentTimeMillis()); }
        }, 20L, 20L);
    }
    @Override public void stop() {
        if (expirationTask != null) expirationTask.cancel();
        expirationTask = null;
        sessions.clear();
        repository.save(sessions.values());
        repository.close();
        combatUntil.clear();
        menus.clear();
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    public ReconnectSession get(UUID playerId) { return sessions.get(playerId); }
    public boolean hasPending(UUID playerId) {
        ReconnectSession session = get(playerId);
        GameMatch match = session == null ? null : matches.getMatchForPlayer(playerId);
        return session != null && canRecover(match, match == null ? null : match.getParticipant(playerId))
            && session.getState() == ReconnectSession.State.DISCONNECTED
            && session.isWithinDeadline(System.currentTimeMillis());
    }

    public boolean disconnected(Player player, GameMatch match, GameParticipant participant) {
        if (!settings.isEnabled() || !canRecover(match, participant)) return false;
        ReconnectSession existing = sessions.get(player.getUniqueId());
        if (existing != null) return existing.getState() == ReconnectSession.State.DISCONNECTED
            && existing.isWithinDeadline(System.currentTimeMillis());
        long now = System.currentTimeMillis();
        ParticipantState life = participant.getLifeState();
        if (settings.isCombatDeath() && combatUntil.getOrDefault(player.getUniqueId(), 0L) > now
                && life == ParticipantState.CONNECTED) {
            life = ParticipantState.RESPAWNING;
        }
        if (life == ParticipantState.CONNECTED && player.getLocation().getY() < 5.0
                && player.getFallDistance() > 3.0F) life = ParticipantState.RESPAWNING;
        if (life == ParticipantState.RESPAWNING
                && participant.getLifeState() == ParticipantState.CONNECTED) {
            matches.processDisconnectDeath(player, match, participant);
        }
        ReconnectSession session = new ReconnectSession(player.getUniqueId(), match.getId(),
            match.getArena().getId(), participant.getTeamId(), now,
            now + settings.getGraceSeconds() * 1000L, life, new PlayerStateSnapshot(player));
        sessions.put(player.getUniqueId(), session);
        repository.save(sessions.values());
        match.setParticipantState(player.getUniqueId(), ParticipantState.DISCONNECTED);
        fire(ReconnectLifecycleEvent.Type.CREATED, session, null);
        plugin.getLogger().info("Reconnect criado: jogador=" + player.getUniqueId()
            + " partida=" + match.getId() + " prazo=" + session.getExpiresAt());
        return true;
    }

    public boolean reconnect(Player player) {
        ReconnectSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(ChatColor.RED + "Nenhuma partida pendente de reconexao.");
            return false;
        }
        fire(ReconnectLifecycleEvent.Type.ATTEMPTED, session, null);
        long now = System.currentTimeMillis();
        if (!session.isWithinDeadline(now)) {
            expire(now);
            fire(ReconnectLifecycleEvent.Type.REJECTED, session, "expired");
            player.sendMessage(ChatColor.RED + "Seu prazo de reconexao terminou.");
            return false;
        }
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (match == null || match.getState() != MatchState.IN_GAME
                || !match.getId().equals(session.getMatchId())
                || matches.getWorld(match.getId()) == null
                || match.getParticipant(player.getUniqueId()) == null
                || !canRecover(match, match.getParticipant(player.getUniqueId()))
                || !session.getTeamId().equals(match.getParticipant(player.getUniqueId()).getTeamId())) {
            session.invalidate();
            sessions.remove(player.getUniqueId());
            repository.save(sessions.values());
            fire(ReconnectLifecycleEvent.Type.REJECTED, session, "match-unavailable");
            matches.expireReconnect(session);
            player.sendMessage(ChatColor.RED + "A partida original nao esta mais disponivel.");
            return false;
        }
        if (!session.begin(now)) {
            fire(ReconnectLifecycleEvent.Type.REJECTED, session, "already-processing");
            return false;
        }
        match.getParticipant(player.getUniqueId()).setConnectionState(ConnectionState.RECONNECTING);
        try {
            if (!matches.restoreReconnect(player, session)) {
                session.retry();
                match.getParticipant(player.getUniqueId()).setConnectionState(ConnectionState.DISCONNECTED);
                fire(ReconnectLifecycleEvent.Type.RESTORE_FAILED, session, "teleport-or-validation");
                player.sendMessage(ChatColor.RED + "Nao foi possivel retornar. Tente novamente.");
                return false;
            }
            session.complete();
            sessions.remove(player.getUniqueId());
            repository.save(sessions.values());
            fire(ReconnectLifecycleEvent.Type.COMPLETED, session, null);
            plugin.getLogger().info("Reconnect concluido: jogador=" + player.getUniqueId()
                + " partida=" + match.getId());
            return true;
        } catch (RuntimeException error) {
            session.retry();
            match.getParticipant(player.getUniqueId()).setConnectionState(ConnectionState.DISCONNECTED);
            fire(ReconnectLifecycleEvent.Type.RESTORE_FAILED, session, "exception");
            plugin.getLogger().warning("Reconnect falhou para " + player.getUniqueId()
                + ": " + error.getMessage());
            player.sendMessage(ChatColor.RED + "Falha ao restaurar a partida. Tente novamente.");
            return false;
        }
    }

    public String status(UUID playerId) {
        ReconnectSession session = sessions.get(playerId);
        if (session == null) return "Nenhuma sessao de reconnect ativa.";
        long now = System.currentTimeMillis();
        long remaining = Math.max(0L, session.getExpiresAt() - now);
        GameMatch match = matches.getMatchForPlayer(playerId);
        String eligibility = now > session.getExpiresAt() ? "prazo encerrado"
            : match == null || match.getState() != MatchState.IN_GAME
                || !match.getId().equals(session.getMatchId())
                || !canRecover(match, match.getParticipant(playerId)) ? "partida indisponivel"
                : session.getState() == ReconnectSession.State.DISCONNECTED ? "disponivel"
                : "em processamento";
        return "Arena: " + session.getArenaId() + " | Equipe: " + session.getTeamId()
            + " | Tempo: " + (remaining / 1000L) + "s | Estado: " + eligibility;
    }

    public int sessionCount() { return sessions.size(); }

    public boolean invalidate(UUID playerId) {
        ReconnectSession session = sessions.remove(playerId);
        if (session == null) return false;
        session.invalidate();
        repository.save(sessions.values());
        fire(ReconnectLifecycleEvent.Type.INVALIDATED, session, "administrator");
        matches.expireReconnect(session);
        plugin.getLogger().warning("Reconnect invalidado administrativamente: jogador="
            + playerId + " partida=" + session.getMatchId());
        return true;
    }

    private void openMenu(Player player) {
        Inventory menu = Bukkit.createInventory(player, 9, "Partida em andamento");
        menu.setItem(2, named(Material.COMPASS, ChatColor.GREEN + "Retornar a partida"));
        menu.setItem(4, named(Material.WATCH, ChatColor.YELLOW + status(player.getUniqueId())));
        menu.setItem(6, named(Material.BARRIER, ChatColor.RED + "Voltar"));
        menus.put(player.getUniqueId(), menu);
        player.openInventory(menu);
    }

    private ItemStack named(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory menu = menus.get(player.getUniqueId());
        if (menu == null || event.getView().getTopInventory() != menu) return;
        event.setCancelled(true);
        if (event.getRawSlot() == 2) {
            player.closeInventory();
            reconnect(player);
        } else if (event.getRawSlot() == 4) {
            player.sendMessage(ChatColor.YELLOW + status(player.getUniqueId()));
        } else if (event.getRawSlot() == 6) player.closeInventory();
    }

    @EventHandler public void onMenuClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        UUID playerId = event.getPlayer().getUniqueId();
        if (menus.get(playerId) == event.getInventory()) menus.remove(playerId);
    }

    private void expire(long now) {
        for (ReconnectSession session : new ArrayList<ReconnectSession>(sessions.values())) {
            if (session.getState() == ReconnectSession.State.DISCONNECTED
                    && !session.isWithinDeadline(now)) {
                session.expire();
                sessions.remove(session.getPlayerId());
                repository.save(sessions.values());
                fire(ReconnectLifecycleEvent.Type.EXPIRED, session, null);
                matches.expireReconnect(session);
                plugin.getLogger().info("Reconnect expirou: jogador=" + session.getPlayerId()
                    + " partida=" + session.getMatchId());
            }
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (get(player.getUniqueId()) == null) return;
        if (!hasPending(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() { expire(System.currentTimeMillis()); }
            });
            return;
        }
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                if (!player.isOnline() || !hasPending(player.getUniqueId())) return;
                matches.parkPendingReconnect(player);
                if (settings.isAutomatic()) reconnect(player);
                else {
                    player.sendMessage(ChatColor.GREEN + "Partida encontrada. " + status(player.getUniqueId())
                        + ChatColor.YELLOW + " Use /reconnect para retornar.");
                    openMenu(player);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0.0) return;
        Player victim = event.getEntity() instanceof Player ? (Player) event.getEntity() : null;
        Player attacker = event.getDamager() instanceof Player ? (Player) event.getDamager() : null;
        if (attacker == null && event.getDamager() instanceof Projectile) {
            ProjectileSource source = ((Projectile) event.getDamager()).getShooter();
            if (source instanceof Player) attacker = (Player) source;
        }
        if (victim == null || attacker == null || victim == attacker) return;
        GameMatch match = matches.getMatchForPlayer(victim.getUniqueId());
        if (match == null || match != matches.getMatchForPlayer(attacker.getUniqueId())
                || match.getState() != MatchState.IN_GAME) return;
        GameParticipant victimParticipant = match.getParticipant(victim.getUniqueId());
        GameParticipant attackerParticipant = match.getParticipant(attacker.getUniqueId());
        if (victimParticipant == null || attackerParticipant == null
                || victimParticipant.getTeamId().equals(attackerParticipant.getTeamId())) return;
        long until = System.currentTimeMillis() + settings.getCombatSeconds() * 1000L;
        combatUntil.put(victim.getUniqueId(), until);
        combatUntil.put(attacker.getUniqueId(), until);
    }

    @EventHandler public void onLifecycle(MatchLifecycleEvent event) {
        if (event.getType() == MatchLifecycleEvent.Type.BED_DESTROYED) {
            for (ReconnectSession session : new ArrayList<ReconnectSession>(sessions.values())) {
                if (!session.getMatchId().equals(event.getMatch().getId())
                        || !session.getTeamId().equals(event.getTeamId())) continue;
                sessions.remove(session.getPlayerId());
                session.invalidate();
                fire(ReconnectLifecycleEvent.Type.INVALIDATED, session, "bed-destroyed");
                matches.expireReconnect(session);
            }
            repository.save(sessions.values());
        } else if (event.getType() == MatchLifecycleEvent.Type.ENDED) {
            for (GameParticipant participant : event.getMatch().getParticipantList()) {
                ReconnectSession session = sessions.remove(participant.getPlayerId());
                if (session != null) {
                    session.invalidate();
                    fire(ReconnectLifecycleEvent.Type.INVALIDATED, session, "match-ended");
                }
            }
            repository.save(sessions.values());
        } else if (event.getType() == MatchLifecycleEvent.Type.PLAYER_LEFT
                && event.getParticipant() != null) {
            ReconnectSession session = sessions.remove(event.getParticipant().getPlayerId());
            if (session != null) {
                session.invalidate();
                fire(ReconnectLifecycleEvent.Type.INVALIDATED, session, "player-left");
            }
            repository.save(sessions.values());
        }
    }

    static boolean canRecover(GameMatch match, GameParticipant participant) {
        return match != null && match.getState() == MatchState.IN_GAME
            && participant != null && participant.getState() != ParticipantState.ELIMINATED
            && participant.getState() != ParticipantState.ABANDONED
            && match.getTeam(participant.getTeamId()) != null
            && match.getTeam(participant.getTeamId()).isBedAlive();
    }

    private void fire(ReconnectLifecycleEvent.Type type, ReconnectSession session, String reason) {
        Bukkit.getPluginManager().callEvent(new ReconnectLifecycleEvent(type,
            session.getPlayerId(), session.getMatchId(), reason));
    }
}
