package com.smartwithdraw.listener;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.balance.BalanceProvider;
import com.smartwithdraw.balance.BalanceProviderRegistry;
import com.smartwithdraw.logging.TransactionLogger;
import com.smartwithdraw.security.NoteInfo;
import com.smartwithdraw.security.NoteValidator;
import com.smartwithdraw.util.Lang;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

public class NoteRedeemListener implements Listener {

    @EventHandler
    public void onRedeem(PlayerInteractEvent event) {

        if (!SmartWithdraw.getInstance().getConfig()
                .getBoolean("notes.right-click-deposit", true)) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();

        if (item == null) {
            return;
        }

        Optional<NoteInfo> infoOpt = NoteValidator.getInfo(item);

        if (infoOpt.isEmpty()) {
            return;
        }

        NoteInfo info = infoOpt.get();

        if (!info.currency().enabled()) {
            return;
        }

        BalanceProvider provider = BalanceProviderRegistry.get(info.currency().backend());

        if (provider == null || !provider.isAvailable()) {
            Lang.send(event.getPlayer(), "backend-unavailable",
                    Map.of("currency", info.currency().id()));
            return;
        }

        Player player = event.getPlayer();

        long total;

        if (player.isSneaking() && item.getAmount() > 1) {

            total = (long) info.value() * item.getAmount();
            player.getInventory().setItemInMainHand(null);

        } else {

            total = info.value();

            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }

        provider.deposit(player, total);
        TransactionLogger.log("DEPOSIT", player, info.currency().id(), total);

        Lang.send(player, "deposit-success",
                Map.of("amount", info.currency().format(total)));

        event.setCancelled(true);
    }
}
