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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class NoteSplitListener implements Listener {

    private final Map<UUID, Long> lastProcessedTick = new HashMap<>();

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

        long currentTick = SmartWithdraw.getInstance().getServer().getCurrentTick();
        Long lastTick = lastProcessedTick.get(player.getUniqueId());
        if (lastTick != null && lastTick == currentTick) {
            event.setCancelled(true);
            return;
        }
        lastProcessedTick.put(player.getUniqueId(), currentTick);

        ItemStack item = player.getInventory().getItemInMainHand();

        Optional<NoteInfo> info = NoteValidator.getInfo(item);
        if (info.isEmpty()) return;

        NoteInfo  note     = info.get();
        Currency  currency = note.currency();

        if (NoteValidator.isExpired(note)) {
            NoteExpiryListener.scanAndDestroy(player);
            event.setCancelled(true);
            return;
        }

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

        List<ItemStack> newNotes = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : breakdown.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                ItemStack newNote = NoteFactory.createNote(currency, entry.getKey());
                if (newNote == null) {
                    Lang.send(player, "cannot-split");
                    event.setCancelled(true);
                    return;
                }
                newNotes.add(newNote);
            }
        }

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        for (ItemStack newNote : newNotes) {
            InventoryUtils.give(player, newNote);
        }

        SoundUtil.play(player, "split");
        Lang.send(player, "split-success",
                Map.of("amount", currency.format(note.value())));
        event.setCancelled(true);
    }
}
