package com.smartwithdraw.logging;

import com.smartwithdraw.SmartWithdraw;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class TransactionLogger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TransactionLogger() {
    }

    public static void log(String type, Player player, String currencyId, long amount) {

        SmartWithdraw plugin = SmartWithdraw.getInstance();

        if (!plugin.getConfig().getBoolean("logging.enabled", true)) {
            return;
        }

        String fileName = plugin.getConfig().getString("logging.file", "transactions.log");
        File file = new File(plugin.getDataFolder(), fileName);

        String line = String.format(
                "[%s] %s | %s | %s | %d%n",
                LocalDateTime.now().format(TIMESTAMP_FORMAT),
                type,
                player.getName(),
                currencyId,
                amount
        );

        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(line);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write transaction log: " + e.getMessage());
        }
    }
}
