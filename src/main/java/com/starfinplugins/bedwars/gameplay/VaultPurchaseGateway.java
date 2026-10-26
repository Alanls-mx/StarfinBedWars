package com.starfinplugins.bedwars.gameplay;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.stats.MatchStatisticsService;
import com.starfinplugins.bedwars.stats.MySqlStatsRepository.PurchaseState;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Vault debit on Bukkit's thread; durable purchase state on the statistics worker. */
public final class VaultPurchaseGateway implements ProgressionPurchaseGateway {
    private final StarfinBedWarsPlugin plugin;
    private final MatchStatisticsService stats;

    public VaultPurchaseGateway(StarfinBedWarsPlugin plugin, MatchStatisticsService stats) {
        this.plugin = plugin;
        this.stats = stats;
    }

    @Override public void purchase(Request request, Consumer<Result> completed) {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("Compras Vault devem iniciar na thread Bukkit");
        Player player = Bukkit.getPlayer(request.playerId);
        RegisteredServiceProvider<Economy> registration =
            Bukkit.getServicesManager().getRegistration(Economy.class);
        if (!stats.isEnabled() || player == null || !player.isOnline()
                || registration == null || registration.getProvider() == null
                || !registration.getProvider().isEnabled()) {
            completed.accept(Result.UNAVAILABLE);
            return;
        }
        Economy economy = registration.getProvider();
        String provider = economy.getName();
        if (provider == null || provider.isEmpty() || provider.length() > 64) {
            completed.accept(Result.UNAVAILABLE);
            return;
        }
        stats.purchaseStep(request, provider, MatchStatisticsService.PurchaseAction.RESERVE, state -> {
            if (state == PurchaseState.CONFLICT) { completed.accept(Result.CONFLICT); return; }
            if (state != PurchaseState.RESERVED) {
                if (state == PurchaseState.UNKNOWN)
                    unresolved(request, "Reserva sem resultado MySQL confirmavel", null, completed);
                else completed.accept(state == PurchaseState.COMMITTED ? Result.COMMITTED
                    : Result.RECONCILIATION_REQUIRED);
                return;
            }
            Player current = Bukkit.getPlayer(request.playerId);
            RegisteredServiceProvider<Economy> active =
                Bukkit.getServicesManager().getRegistration(Economy.class);
            if (current == null || !current.isOnline() || active == null
                    || active.getProvider() != economy || !economy.isEnabled()) {
                abort(request, completed, Result.UNAVAILABLE);
                return;
            }
            if (request.price > 0) {
                try {
                    if (!economy.has(current, request.price)) {
                        abort(request, completed, Result.INSUFFICIENT_FUNDS);
                        return;
                    }
                    EconomyResponse response = economy.withdrawPlayer(current, request.price);
                    if (response == null) {
                        unresolved(request, "Vault retornou resposta nula ao debitar", null, completed);
                        return;
                    }
                    if (!response.transactionSuccess()) {
                        abort(request, completed, Result.FAILED);
                        return;
                    }
                } catch (RuntimeException error) {
                    unresolved(request, "Vault lancou excecao durante o debito", error, completed);
                    return;
                }
            }
            markCharged(request, economy, request.price > 0, completed);
        });
    }

    private void markCharged(Request request, Economy economy, boolean debited,
                             Consumer<Result> completed) {
        stats.purchaseStep(request, null, MatchStatisticsService.PurchaseAction.CHARGED, state -> {
            if (state == PurchaseState.CHARGED) { commit(request, economy, debited, completed); return; }
            if (state == PurchaseState.COMMITTED) { completed.accept(Result.COMMITTED); return; }
            stats.purchaseStep(request, null, MatchStatisticsService.PurchaseAction.STATUS, fresh -> {
                if (fresh == PurchaseState.CHARGED) commit(request, economy, debited, completed);
                else if (fresh == PurchaseState.COMMITTED) completed.accept(Result.COMMITTED);
                else if (fresh == PurchaseState.RESERVED) compensate(request, economy, debited, completed);
                else unresolved(request, "Debito sem estado MySQL confirmavel", null, completed);
            });
        });
    }

    private void commit(Request request, Economy economy, boolean debited,
                        Consumer<Result> completed) {
        stats.purchaseStep(request, null, MatchStatisticsService.PurchaseAction.COMMIT, state -> {
            if (state == PurchaseState.COMMITTED) { completed.accept(Result.COMMITTED); return; }
            if (state == PurchaseState.CHARGED || state == PurchaseState.CONFLICT) {
                compensate(request, economy, debited, completed);
                return;
            }
            stats.purchaseStep(request, null, MatchStatisticsService.PurchaseAction.STATUS, fresh -> {
                if (fresh == PurchaseState.COMMITTED) completed.accept(Result.COMMITTED);
                else if (fresh == PurchaseState.CHARGED) compensate(request, economy, debited, completed);
                else unresolved(request, "Commit da compra sem resultado confirmavel", null, completed);
            });
        });
    }

    private void compensate(Request request, Economy economy, boolean debited,
                            Consumer<Result> completed) {
        if (!debited) { abort(request, completed, Result.CONFLICT); return; }
        boolean returned = false;
        try {
            if (economy.isEnabled()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(request.playerId);
                EconomyResponse response = economy.depositPlayer(player, request.price);
                returned = response != null && response.transactionSuccess();
            }
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.SEVERE,
                "Falha no estorno Vault da compra " + request.transactionId, error);
        }
        MatchStatisticsService.PurchaseAction action = returned
            ? MatchStatisticsService.PurchaseAction.REFUND
            : MatchStatisticsService.PurchaseAction.REFUND_PENDING;
        stats.purchaseStep(request, null, action, state -> {
            if (state == PurchaseState.REFUNDED) completed.accept(Result.FAILED);
            else unresolved(request, "Estorno pendente ou sem registro MySQL", null, completed);
        });
    }

    private void abort(Request request, Consumer<Result> completed, Result result) {
        stats.purchaseStep(request, null, MatchStatisticsService.PurchaseAction.ABORT, state -> {
            if (state == PurchaseState.ABORTED) completed.accept(result);
            else unresolved(request, "Reserva sem encerramento confirmado", null, completed);
        });
    }

    private void unresolved(Request request, String reason, Exception error,
                            Consumer<Result> completed) {
        UUID id = request.transactionId;
        if (error == null) plugin.getLogger().severe(reason + ": " + id);
        else plugin.getLogger().log(Level.SEVERE, reason + ": " + id, error);
        completed.accept(Result.RECONCILIATION_REQUIRED);
    }
}
