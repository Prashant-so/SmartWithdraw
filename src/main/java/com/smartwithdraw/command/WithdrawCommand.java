package com.smartwithdraw.command;

import com.smartwithdraw.currency.DenominationCalculator;
import com.smartwithdraw.currency.NoteFactory;
import com.smartwithdraw.economy.EconomyManager;
import com.smartwithdraw.util.InventoryUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class WithdrawCommand implements CommandExecutor {


@Override
public boolean onCommand(CommandSender sender,
                         Command command,
                         String label,
                         String[] args) {

    if (!(sender instanceof Player player)) {
        return true;
    }

    if (args.length != 1) {
        player.sendMessage("§cUsage: /withdraw <amount>");
        return true;
    }

    try {

        int amount = Integer.parseInt(args[0]);

        if (amount <= 0) {
            player.sendMessage("§cAmount must be greater than 0.");
            return true;
        }

        if (!EconomyManager.has(player, amount)) {
            player.sendMessage("§cYou do not have enough money.");
            return true;
        }

        EconomyManager.withdraw(player, amount);

        Map<Integer, Integer> notes =
                DenominationCalculator.calculate(amount);

        for (Map.Entry<Integer, Integer> entry : notes.entrySet()) {

            int value = entry.getKey();
            int count = entry.getValue();

            for (int i = 0; i < count; i++) {

                InventoryUtils.give(
                        player,
                        NoteFactory.createNote(value)
                );
            }
        }

        player.sendMessage(
                "§6§lSmartWithdraw §8» §aWithdrawn ₹" + amount
        );

    } catch (NumberFormatException ex) {

        player.sendMessage(
                "§cPlease enter a valid number."
        );
    }

    return true;
}


}
