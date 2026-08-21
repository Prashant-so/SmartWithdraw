package com.smartwithdraw.logging;

import com.smartwithdraw.SmartWithdraw;
import org.bukkit.entity.Player;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class TransactionLogger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final BlockingQueue<String> QUEUE =
            new ArrayBlockingQueue<>(1024);

    private static BufferedWriter writer;
    private static File logFile;

    private TransactionLogger() {
    }

    public static void init() {

        SmartWithdraw plugin = SmartWithdraw.getInstance();

        if (!plugin.getConfig().getBoolean("logging.enabled", true)) return;

        String fileName = plugin.getConfig()
                .getString("logging.file", "transactions.log");

        logFile = new File(plugin.getDataFolder(), fileName);

        try {
            writer = new BufferedWriter(new FileWriter(logFile, true));
        } catch (IOException e) {
            plugin.getLogger()
                    .warning("Could not open transaction log: " + e.getMessage());
            return;
        }

        // Flush buffered log lines every 5 seconds on an async thread
        // so individual transactions don't each open/close the file
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, TransactionLogger::flush, 100L, 100L);
    }

    public static void log(String type, Player player,
                            String currencyId, long amount) {

        SmartWithdraw plugin = SmartWithdraw.getInstance();

        if (!plugin.getConfig().getBoolean("logging.enabled", true)) return;

        String txId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String line = String.format("[%s] [%s] %s | %s | %s | %d",
                LocalDateTime.now().format(TIMESTAMP_FORMAT),
                txId,
                type,
                player.getName(),
                currencyId,
                amount
        );

        boolean queued = QUEUE.offer(line);

        if (!queued) {
            // Queue full — flush immediately and try again
            flush();
            QUEUE.offer(line);
        }
    }

    public static void flush() {

        if (writer == null) return;

        List<String> lines = new ArrayList<>();
        QUEUE.drainTo(lines);

        if (lines.isEmpty()) return;

        try {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            SmartWithdraw.getInstance().getLogger()
                    .warning("Failed to write transaction log: " + e.getMessage());
            // Put lines back into queue so they aren't silently dropped
            for (String line : lines) {
                QUEUE.offer(line);
            }
        }
    }

    public static void close() {
        flush();
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                SmartWithdraw.getInstance().getLogger()
                        .warning("Failed to close transaction log: " + e.getMessage());
            }
        }
    }
}
