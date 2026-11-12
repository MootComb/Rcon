package com.MootComb.Rcon;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements TabCompleter {

    private Logger logger;
    private FileConfiguration config;
    private String prefix;

    @Override
    public void onEnable() {
        this.logger = getLogger();
        saveDefaultConfig();
        this.config = getConfig();
        this.prefix = config.getString("messages.prefix", "rcon | ");
        getCommand("rcon").setTabCompleter(this);
        logger.info(msg("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        logger.info(msg("plugin.disabled"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return onCommand(sender, command, label, new String[]{"help"});
        }

        if (!sender.hasPermission("rcon.use")) {
            sender.sendMessage(format("permission-message"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "send":
                return handleSend(sender, args);
            case "reload":
                return handleReload(sender);
            case "help":
                sender.sendMessage(format("help"));
                return true;
            default:
                sender.sendMessage(format("commandUsage"));
                return true;
        }
    }

    private boolean handleSend(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rcon.send")) {
            sender.sendMessage(format("send.permission-message"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(format("commandSendUsage"));
            return true;
        }

        String serverName = args[1];
        String commandToSend = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        sendRconCommand(sender, serverName, commandToSend);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("rcon.reload")) {
            sender.sendMessage(format("reload.permission-message"));
            return true;
        }
        reloadConfig();
        this.config = getConfig();
        sender.sendMessage(format("settingsReloaded"));
        return true;
    }

    private void sendRconCommand(CommandSender sender, String serverName, String command) {
        if (!config.contains("servers." + serverName)) {
            sender.sendMessage(format("serverNotFound", serverName));
            return;
        }

        String address = config.getString("servers." + serverName + ".address");
        String password = config.getString("servers." + serverName + ".password");
        if (address == null || password == null) {
            sender.sendMessage(format("serverNotFound", serverName));
            return;
        }

        String[] parts = address.split(":");
        if (parts.length != 2) {
            sender.sendMessage(format("invalidPort", serverName));
            return;
        }

        String host = parts[0];
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(format("invalidPort", serverName));
            return;
        }

        final FileConfiguration cfg = this.config;
        final Logger log = this.logger;
        final RconClient[] holder = new RconClient[1];

        Runnable task = () -> {
            try {
                holder[0] = RconClient.open(host, port, password, cfg);
                String response = holder[0].sendCommand(command);
                sender.sendMessage(format("responseFromServer", serverName, response));
            } catch (AuthFailureException e) {
                log.warning("RCON auth failure on server " + serverName + ": " + e.getMessage());
                sender.sendMessage(format("rconAuthError", serverName));
            } catch (Exception e) {
                log.severe("Failed to send RCON command to server " + serverName + ": " + e.getMessage());
                sender.sendMessage(format("rconError", serverName));
            } finally {
                if (holder[0] != null) {
                    try {
                        holder[0].close();
                    } catch (Exception ignored) {
                    }
                }
            }
        };

        try {
            Class<?> asyncClass = Class.forName("org.bukkit.Bukkit");
            Object scheduler = asyncClass.getMethod("getAsyncScheduler").invoke(null);
            scheduler.getClass()
                    .getMethod("runNow", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class)
                    .invoke(scheduler, this, (java.util.function.Consumer<Object>) t -> task.run());
        } catch (Throwable t) {
            task.run();
        }
    }

    private String format(String key, String... args) {
        String message = config.getString("messages." + key, "Message not found: " + key)
                .replace("%prefix%", prefix);

        for (int i = 0; i < args.length; i++) {
            if (i == 1) {
                message = message.replace("%r", args[i]);
            } else {
                message = message.replaceFirst("%s", args[i]);
            }
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private String msg(String key) {
        return config.getString("messages." + key, key);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        Set<String> unique = new HashSet<>();

        if (args.length == 1) {
            unique.add("help");
            unique.add("reload");
            unique.add("send");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            ConfigurationSection servers = config.getConfigurationSection("servers");
            if (servers != null) {
                unique.addAll(servers.getKeys(false));
            }
        }

        List<String> completions = new ArrayList<>();
        String last = args[args.length - 1];
        for (String c : unique) {
            if (c.startsWith(last)) {
                completions.add(c);
            }
        }
        return completions;
    }

    public static class AuthFailureException extends RconClientException {
        public AuthFailureException(String message) {
            super(message);
        }
    }

    public static class RconClientException extends RuntimeException {
        public RconClientException(String message) {
            super(message);
        }

        public RconClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class RconClient implements Closeable {
        private static final int AUTHENTICATION_FAILURE_ID = -1;
        private static final int TYPE_COMMAND = 2;
        private static final int TYPE_AUTH = 3;

        private final SocketChannel socketChannel;
        private final AtomicInteger currentRequestId;
        private final FileConfiguration config;

        private RconClient(SocketChannel socketChannel, FileConfiguration config) {
            this.socketChannel = Objects.requireNonNull(socketChannel, "socketChannel");
            this.config = Objects.requireNonNull(config, "config");
            this.currentRequestId = new AtomicInteger(1);
        }

        public static RconClient open(String host, int port, String password, FileConfiguration config) {
            SocketChannel channel;
            try {
                channel = SocketChannel.open(new InetSocketAddress(host, port));
            } catch (IOException e) {
                throw new RconClientException(
                        msg(config, "errors.socketOpen", host, String.valueOf(port)), e);
            }

            RconClient client = new RconClient(channel, config);
            try {
                client.authenticate(password);
            } catch (Exception authException) {
                try {
                    client.close();
                } catch (Exception closingException) {
                    authException.addSuppressed(closingException);
                }
                throw authException;
            }
            return client;
        }

        public String sendCommand(String command) {
            return send(TYPE_COMMAND, command);
        }

        @Override
        public void close() {
            try {
                socketChannel.close();
            } catch (IOException e) {
                throw new RconClientException(msg(config, "errors.socketClose"), e);
            }
        }

        private void authenticate(String password) {
            send(TYPE_AUTH, password);
        }

        private String send(int type, String payload) {
            int requestId = currentRequestId.getAndIncrement();

            ByteBuffer buffer = toByteBuffer(requestId, type, payload);
            try {
                socketChannel.write(buffer);
            } catch (IOException e) {
                throw new RconClientException(
                        msg(config, "errors.write", String.valueOf(buffer.capacity())), e);
            }

            ByteBuffer responseBuffer = readResponse();
            int responseId = responseBuffer.getInt();

            if (responseId == AUTHENTICATION_FAILURE_ID) {
                throw new AuthFailureException(msg(config, "errors.authFailure"));
            }
            if (responseId != requestId) {
                throw new RconClientException(
                        msg(config, "errors.idMismatch", String.valueOf(requestId), String.valueOf(responseId)));
            }

            responseBuffer.getInt();

            byte[] bodyBytes = new byte[responseBuffer.remaining()];
            responseBuffer.get(bodyBytes);
            return new String(bodyBytes, StandardCharsets.UTF_8);
        }

        private ByteBuffer readResponse() {
            int size = readData(Integer.BYTES).getInt();
            ByteBuffer dataBuffer = readData(size - 2);
            ByteBuffer nullsBuffer = readData(2);

            byte null1 = nullsBuffer.get(0);
            byte null2 = nullsBuffer.get(1);
            if (null1 != 0 || null2 != 0) {
                throw new RconClientException(
                        msg(config, "errors.nullBytes", String.valueOf(null1), String.valueOf(null2)));
            }
            return dataBuffer;
        }

        private ByteBuffer readData(int size) {
            ByteBuffer buffer = ByteBuffer.allocate(size);
            int readCount;
            try {
                readCount = socketChannel.read(buffer);
            } catch (IOException e) {
                throw new RconClientException(
                        msg(config, "errors.read", String.valueOf(size)), e);
            }
            if (readCount != size) {
                throw new RconClientException(
                        msg(config, "errors.readMismatch", String.valueOf(size), String.valueOf(readCount)));
            }
            buffer.position(0);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            return buffer;
        }

        private static ByteBuffer toByteBuffer(int requestId, int type, String payload) {
            byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(12 + payloadBytes.length + 2);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            buffer.putInt(10 + payloadBytes.length);
            buffer.putInt(requestId);
            buffer.putInt(type);
            buffer.put(payloadBytes);
            buffer.put((byte) 0);
            buffer.put((byte) 0);

            buffer.position(0);
            return buffer;
        }

        private static String msg(FileConfiguration config, String key, String... args) {
            String template = config.getString("messages." + key, key);
            for (String arg : args) {
                template = template.replaceFirst("%s", Matcher.quoteReplacement(arg));
            }
            return template;
        }
    }
}
