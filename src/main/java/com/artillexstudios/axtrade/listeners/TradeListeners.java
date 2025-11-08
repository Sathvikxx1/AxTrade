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

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.artillexstudios.axtrade.AxTrade.CONFIG;
import static com.artillexstudios.axtrade.AxTrade.MESSAGEUTILS;

public class TradeListeners implements Listener {

    public static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(
            1,
            r -> new Thread(r, "AxTrade-Ticking-Thread"));

    private static final Map<UUID, Location> LAST_LOCATIONS = new ConcurrentHashMap<>();

    public static void tick() {
        if (!CONFIG.getBoolean("abort.move", true)) return;

        SCHEDULER.scheduleAtFixedRate(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Trade trade = Trades.getTrade(player);
                if (trade == null) continue;
                if (System.currentTimeMillis() - trade.getPrepTime() < 1_000L) continue;

                Location current = player.getLocation();
                Location last = LAST_LOCATIONS.get(player.getUniqueId());

                if (last != null && last.distanceSquared(current) != 0) {
                    trade.abort();
                }

                LAST_LOCATIONS.put(player.getUniqueId(), current.clone());
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public static void stop() {
        SCHEDULER.shutdownNow();
        LAST_LOCATIONS.clear();
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        handleQuitTrade(event);
        handleQuitRequest(event);
        LAST_LOCATIONS.remove(event.getPlayer().getUniqueId());
    }

    public void handleQuitTrade(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Trade trade = Trades.getTrade(player);
        if (trade == null) return;
        trade.abort();
    }

    public void handleQuitRequest(@NotNull PlayerQuitEvent event) {
        Iterator<Request> iterator = Requests.getRequests().iterator();
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

    /*   @EventHandler
       public void onMove(@NotNull PlayerMoveEvent event) {
           if (event.getTo() == null) return;
           Trade trade = Trades.getTrade(event.getPlayer());
           if (trade == null) return;
           if (!CONFIG.getBoolean("abort.move", true)) return;
           if (System.currentTimeMillis() - trade.getPrepTime() < 1_000L) return;
           if (event.getFrom().distanceSquared(event.getTo()) == 0) return;
           trade.abort();
       }
   */

    @EventHandler
    public void onInteract(@NotNull PlayerInteractEvent event) {
        Trade trade = Trades.getTrade(event.getPlayer());
        if (trade == null) return;
        if (!CONFIG.getBoolean("abort.interact", true)) return;
        if (System.currentTimeMillis() - trade.getPrepTime() < 1_000L) return;
        event.setCancelled(true);
        trade.abort();
    }

    @EventHandler
    public void onCommand(@NotNull PlayerCommandPreprocessEvent event) {
        Trade trade = Trades.getTrade(event.getPlayer());
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
