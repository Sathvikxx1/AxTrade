package com.artillexstudios.axtrade.listeners;

import com.artillexstudios.axtrade.request.Request;
import com.artillexstudios.axtrade.request.Requests;
import com.artillexstudios.axtrade.trade.Trade;
import com.artillexstudios.axtrade.trade.Trades;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.artillexstudios.axtrade.AxTrade.CONFIG;
import static com.artillexstudios.axtrade.AxTrade.MESSAGEUTILS;

public class TradeListeners implements Listener {

    public static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "AxTrade Async Ticking Thread (x1)"));
    public static final Map<Player, Location> lastLocation = new HashMap<>();

    public static void tick() {
        scheduler.scheduleAtFixedRate(() -> {
            Bukkit.getOnlinePlayers().forEach(player -> {
                Location from = lastLocation.get(player);
                Location to = player.getLocation();

                if (from != null && to != null && from.distanceSquared(to) > 0) {
                    Trade trade = Trades.getTrade(player);
                    if (trade != null && CONFIG.getBoolean("abort.move", true) && System.currentTimeMillis() - trade.getPrepTime() >= 1_000L) {
                        trade.abort();
                    }
                }
                lastLocation.put(player, to);
            });
        }, 0, 1L, TimeUnit.SECONDS);
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        handleQuitTrade(event);
        handleQuitRequest(event);
        lastLocation.remove(event.getPlayer());
    }

    public void handleQuitTrade(@NotNull PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final Trade trade = Trades.getTrade(player);
        if (trade == null) return;
        trade.abort();
    }

    public void handleQuitRequest(@NotNull PlayerQuitEvent event) {
        final Iterator<Request> iterator = Requests.getRequests().iterator();
        while (iterator.hasNext()) {
            Request request = iterator.next();
            if (request.getSender().equals(event.getPlayer())) {
                iterator.remove();
                continue;
            }
            if (request.getReceiver().equals(event.getPlayer())) {
                iterator.remove();
                if (!request.isActive()) continue;
                MESSAGEUTILS.sendLang(request.getSender(), "request.expired", Map.of("%player%", request.getReceiver().getName()));
            }
        }
    }

    @EventHandler
    public void onDrop(@NotNull PlayerDropItemEvent event) {
        cancelIfTrading(event.getPlayer(), event);
    }

    @EventHandler
    public void onPickup(@NotNull EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        cancelIfTrading(player, event);
    }

//    @EventHandler
//    public void onMove(@NotNull PlayerMoveEvent event) {
//        if (event.getTo() == null) return;
//        final Trade trade = Trades.getTrade(event.getPlayer());
//        if (trade == null) return;
//        if (!CONFIG.getBoolean("abort.move", true)) return;
//        if (System.currentTimeMillis() - trade.getPrepTime() < 1_000L) return;
//        if (event.getFrom().distanceSquared(event.getTo()) == 0) return;
//        trade.abort();
//    }

    @EventHandler
    public void onInteract(@NotNull PlayerInteractEvent event) {
        final Trade trade = Trades.getTrade(event.getPlayer());
        if (trade == null) return;
        if (!CONFIG.getBoolean("abort.interact", true)) return;
        if (System.currentTimeMillis() - trade.getPrepTime() < 1_000L) return;
        event.setCancelled(true);
        trade.abort();
    }

    @EventHandler
    public void onCommand(@NotNull PlayerCommandPreprocessEvent event) {
        final Trade trade = Trades.getTrade(event.getPlayer());
        if (trade == null) return;
        if (!CONFIG.getBoolean("abort.command", true)) return;
        event.setCancelled(true);
        trade.abort();
    }

    private void cancelIfTrading(Player player, Cancellable event) {
        Trade trade = Trades.getTrade(player);
        if (trade == null) return;
        event.setCancelled(true);
    }
}
