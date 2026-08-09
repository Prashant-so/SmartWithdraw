package com.smartwithdraw.listener;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.currency.Currency;
import com.smartwithdraw.currency.DenominationCalculator;
import com.smartwithdraw.currency.NoteFactory;
import com.smartwithdraw.security.NoteInfo;
import com.smartwithdraw.security.NoteValidator;
import com.smartwithdraw.util.InventoryUtils;
import com.smartwithdraw.util.Lang;
import com.smartwithdraw.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

public class NoteSplitListener implements Listener {

    @EventHandler
    public void onSplit(PlayerInteractEvent event) {

        if (!SmartWithdraw.getInstance().getConfig()
                .getBoolean("notes.allow-splitting", true)) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) return;

        if (event.getAction() != Action.LEFT_CLICK_AIR
                && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();

        if (!player.isSneaking()) return;

        ItemStack item = player.getInventory().getItemInMainHand();

        Optional<NoteInfo> info = NoteValidator.getInfo(item);
        if (info.isEmpty()) return;

        NoteInfo  note     = info.get();
        Currency  currency = note.currency();

        // Expiry check
        if (NoteValidator.isExpired(note)) {
            NoteExpiryListener.scanAndDestroy(player);
            event.setCancelled(true);
            return;
        }

        // World check
        if (!currency.isWorldAllowed(player.getWorld().getName())) {
            Lang.send(player, "world-not-allowed",
                    Map.of("currency", currency.name()));
            event.setCancelled(true);
            return;
        }

        Map<Integer, Integer> breakdown =
                DenominationCalculator.splitDown(note.value(), currency);

        if (breakdown.isEmpty()) {
            Lang.send(player, "cannot-split");
            return;
        }

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        for (Map.Entry<Integer, Integer> entry : breakdown.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                InventoryUtils.give(player,
                        NoteFactory.createNote(currency, entry.getKey()));
            }
        }

        SoundUtil.play(player, "split");

        Lang.send(player, "split-success",
                Map.of("amount", currency.format(note.value())));

        event.setCancelled(true);
    }
}
