package com.smartwithdraw.listener;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.balance.BalanceProvider;
import com.smartwithdraw.balance.BalanceProviderRegistry;
import com.smartwithdraw.currency.TaxConfig;
import com.smartwithdraw.logging.TransactionLogger;
import com.smartwithdraw.security.NoteInfo;
import com.smartwithdraw.security.NoteValidator;
import com.smartwithdraw.util.CooldownManager;
import com.smartwithdraw.util.Lang;
import com.smartwithdraw.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class NoteRedeemListener implements Listener {

    private final Map<UUID, Long> lastProcessedTick = new HashMap<>();

    @EventHandler
    public void onRedeem(PlayerInteractEvent event) {

        if (!SmartWithdraw.getInstance().getConfig()
                .getBoolean("notes.right-click-deposit", true)) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) return;

        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null) return;

        Optional<NoteInfo> infoOpt = NoteValidator.getInfo(item);
        if (infoOpt.isEmpty()) return;

        NoteInfo info   = infoOpt.get();
        Player   player = event.getPlayer();

        long currentTick = SmartWithdraw.getInstance().getServer().getCurrentTick();
        Long lastTick = lastProcessedTick.get(player.getUniqueId());
        if (lastTick != null && lastTick == currentTick) {
            event.setCancelled(true);
            return;
        }
        lastProcessedTick.put(player.getUniqueId(), currentTick);

        if (NoteValidator.isExpired(info)) {
            NoteExpiryListener.scanAndDestroy(player);
            event.setCancelled(true);
            return;
        }

        if (!info.currency().isWorldAllowed(player.getWorld().getName())) {
            Lang.send(player, "world-not-allowed",
                    Map.of("currency", info.currency().name()));
            event.setCancelled(true);
            return;
        }

        if (!info.currency().enabled()) return;

        int cooldownSeconds = SmartWithdraw.getInstance().getConfig()
                .getInt("limits.deposit-cooldown-seconds", 0);
        long remaining = CooldownManager.remainingDepositSeconds(player, cooldownSeconds);

        if (remaining > 0) {
            Lang.send(player, "on-cooldown", Map.of("seconds", String.valueOf(remaining)));
            event.setCancelled(true);
            return;
        }

        BalanceProvider provider =
                BalanceProviderRegistry.get(info.currency().backend());

        if (provider == null || !provider.isAvailable()) {
            Lang.send(player, "backend-unavailable",
                    Map.of("currency", info.currency().id()));
            event.setCancelled(true);
            return;
        }

        long total = player.isSneaking() && item.getAmount() > 1
                ? (long) info.value() * item.getAmount()
                : info.value();

        TaxConfig tax     = info.currency().tax();
        long taxAmount    = tax.applyOnDeposit() ? tax.calculateTax(total) : 0;
        long creditAmount = Math.max(0, total - taxAmount);

        boolean deposited = provider.deposit(player, creditAmount);

        if (!deposited) {
            Lang.send(player, "backend-unavailable",
                    Map.of("currency", info.currency().id()));
            event.setCancelled(true);
            return;
        }

        if (player.isSneaking() && item.getAmount() > 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }

        CooldownManager.markDeposit(player);
        TransactionLogger.log("DEPOSIT", player, info.currency().id(), creditAmount);
        SoundUtil.play(player, "deposit");

        if (taxAmount > 0) {
            TransactionLogger.log("TAX_DEPOSIT", player,
                    info.currency().id(), taxAmount);
            Lang.send(player, "deposit-success-taxed", Map.of(
                    "amount", info.currency().format(creditAmount),
                    "tax",    info.currency().format(taxAmount)
            ));
        } else {
            Lang.send(player, "deposit-success",
                    Map.of("amount", info.currency().format(creditAmount)));
        }

        event.setCancelled(true);
    }
}
