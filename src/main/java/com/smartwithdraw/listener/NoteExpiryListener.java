package com.smartwithdraw.listener;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.security.NoteInfo;
import com.smartwithdraw.security.NoteValidator;
import com.smartwithdraw.util.Lang;
import com.smartwithdraw.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles two expiry detection paths:
 * 1. On player join — scans inventory immediately
 * 2. Periodic scan — scheduled in SmartWithdraw.java every N seconds
 */
public class NoteExpiryListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scanAndDestroy(event.getPlayer());
    }

    /**
     * Called both from the join event and from the periodic scheduler.
     */
    public static void scanAndDestroy(Player player) {

        List<ItemStack> toRemove = new ArrayList<>();

        for (ItemStack item : player.getInventory().getContents()) {

            Optional<NoteInfo> info = NoteValidator.getInfo(item);

            if (info.isEmpty()) {
                continue;
            }

            NoteInfo note = info.get();

            if (!NoteValidator.isExpired(note)) {
                continue;
            }

            toRemove.add(item);
        }

        if (toRemove.isEmpty()) {
            return;
        }

        for (ItemStack item : toRemove) {
            player.getInventory().remove(item);
            Optional<NoteInfo> info = NoteValidator.getInfo(item);

            if (info.isEmpty()) {
                continue;
            }

            NoteInfo note = info.get();

            SoundUtil.play(player, "expire");

            Lang.send(player, "note-expired", Map.of(
                    "amount",   note.currency().format(note.value()),
                    "currency", note.currency().name(),
                    "type",     SmartWithdraw.getInstance().getConfig()
                                    .getString("notes.type-label", "Satchel")
            ));
        }

        player.updateInventory();
    }
}
