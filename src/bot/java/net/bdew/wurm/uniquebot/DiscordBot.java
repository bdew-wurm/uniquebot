package net.bdew.wurm.uniquebot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.StatusChangeEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.LoginException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DiscordBot extends ListenerAdapter {
    private static final Logger logger = LogManager.getLogger("DiscordBot");
    private final static Timer timer = new Timer();
    private final String serverName, channelName, token;
    private final GameServers servers;
    private final DB db;
    private final ListUpdater updater;
    private String guildId, channelId, myUserId;
    private JDA jda;
    private TimerTask updateTask;
    private String lastReportId;
    private Map<Integer, KnownUnique> numberMap = new HashMap<>();

    public DiscordBot(String serverName, String channelName, String token, GameServers servers, DB db) {
        this.serverName = serverName;
        this.channelName = channelName;
        this.token = token;
        this.servers = servers;
        this.db = db;
        this.updater = new ListUpdater(servers, db);
    }

    @Override
    public void onStatusChange(@NotNull StatusChangeEvent event) {
        logger.info("Status: " + event.getNewStatus());
        if (event.getNewStatus() == JDA.Status.CONNECTED) {
            Guild guild = jda.getGuilds().stream()
                    .filter(x -> x.getName().equals(serverName))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException(String.format("Server %s not found", serverName)));
            guildId = guild.getId();
            channelId = guild.getChannels().stream()
                    .filter(x -> x.getName().equals(channelName))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException(String.format("Channel %s not found", channelName)))
                    .getId();

            myUserId = jda.getSelfUser().getId();

            logger.info(String.format("Found me=%s guild=%s channel=%s", myUserId, guildId, channelId));

            refreshReport(false).join();

            logger.info("Initial report posted successfully");

            if (updateTask != null) updateTask.cancel();
            updateTask = new TimerTask() {
                @Override
                public void run() {
                    refreshReport(false).join();
                }
            };

            clearOldMessages().thenRun(() -> timer.schedule(updateTask, 30000, 30000));
        }
    }

    @Override
    public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        Message message = event.getMessage();
        String content = message.getContentRaw();
        MessageChannel channel = event.getChannel();

        if (message.getType() == MessageType.CHANNEL_PINNED_ADD && event.getAuthor().isBot() && channel.getId().equals(channelId)) {
            logger.info(String.format("Deleting pin message %s", event.getMessageId()));
            event.getMessage().delete().queue();
        }

        if (event.getAuthor().isBot()) return;
        if (channel.getId().equals(channelId)) {
            if (content.startsWith("!found")) {
                if (content.length() < 8) {
                    reject(message, "Invalid command");
                    return;
                }
                String left = content.substring(7);
                int sp = left.indexOf(" ");
                if (sp > 0) {
                    int num;
                    try {
                        num = Integer.parseInt(left.substring(0, sp).trim());
                    } catch (NumberFormatException e) {
                        reject(message, "Invalid unique number in command");
                        return;
                    }
                    KnownUnique ent = numberMap.get(num);
                    if (ent != null) {
                        String location = left.substring(sp).trim();
                        if (location.length() > 0) {
                            handleFound(message, ent, location);
                        } else {
                            reject(message, "Location not provided");
                        }
                    } else {
                        reject(message, "Invalid unique number in command");
                    }
                } else {
                    reject(message, "Invalid command");
                }
            }
        }
    }

    private void reject(Message msg, String reason) {
        msg.getChannel().sendMessage(String.format("<@%s>: %s", msg.getAuthor().getId(), reason))
                .delay(60, TimeUnit.SECONDS)
                .flatMap(Message::delete)
                .flatMap(x -> msg.delete())
                .queue();
    }

    @Override
    public void onGuildMessageReactionAdd(@NotNull GuildMessageReactionAddEvent event) {
        if (event.getMessageId().equals(lastReportId) && !event.getUserId().equals(myUserId) && "U+1F504".equalsIgnoreCase(event.getReactionEmote().getAsCodepoints())) {
            logger.info(String.format("Report refresh requested by %s", event.getMember().getEffectiveName()));
            refreshReport(true).join();
        }
    }

    public void start() {
        try {
            jda = JDABuilder.create(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS)
                    .disableCache(CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE, CacheFlag.EMOTE, CacheFlag.CLIENT_STATUS)
                    .addEventListeners(this)
                    .build();
        } catch (LoginException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<TextChannel> getChannel() {
        if (jda == null || guildId == null || channelId == null) return Optional.empty();
        return Optional.ofNullable(jda.getGuildById(guildId))
                .flatMap(g -> Optional.ofNullable(g.getTextChannelById(channelId)));
    }

    private void handleFound(Message msg, KnownUnique ent, String location) {
        ent.location = location;
        ent.reporter = msg.getAuthor().getName();
        db.updateLocation(ent)
                .thenCompose((v) -> refreshReport(true))
                .thenCompose((v) -> msg.addReaction("U+2705").submit())
                .join();
    }

    private CompletableFuture<Void> clearOldMessages() {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        getChannel().ifPresent(channel -> {
            MessageHistory hist = channel.getHistory();
            channel.getHistoryBefore(lastReportId, 100).queue((history) -> {
                List<Message> messages = history.getRetrievedHistory();
                logger.info(String.format("Checking %d old messages", messages.size()));
                messages.forEach(message -> {
                    if (message.getAuthor().getId().equals(myUserId) && !message.getId().equals(lastReportId) && !message.getEmbeds().isEmpty()) {
                        message.delete().queue((res) -> logger.info(String.format("Deleted old report message %s", message.getId())));
                    } else if (message.getAuthor().getId().equals(myUserId) && message.getType() == MessageType.CHANNEL_PINNED_ADD) {
                        message.delete().queue((res) -> logger.info(String.format("Deleted old pin message %s", message.getId())));
                    }
                });
                promise.complete(null);
            });
        });
        return promise;
    }

    private CompletableFuture<Void> refreshReport(boolean forcePost) {
        return updater.getUniqueList().thenCompose(report -> {
            logger.info(String.format("Refreshed report added=%d removed=%d force=%s", report.added.size(), report.removed.size(), forcePost));
            return getChannel().map(channel -> {
                report.removed.forEach(unique ->
                        channel.sendMessage(String.format(
                                "**%s** on **%s** is gone, removing from list.", unique.name, unique.server)).queue()
                );
                report.added.forEach(unique ->
                        channel.sendMessage(String.format(
                                "Adding new unique - **%s** on **%s**.", unique.name, unique.server)).queue()
                );
                if (forcePost || !report.added.isEmpty() || !report.removed.isEmpty() || lastReportId == null) {
                    return postList(channel, report.uniques);
                } else return CompletableFuture.<Void>completedFuture(null);
            }).orElseGet(() -> CompletableFuture.completedFuture(null));
        });
    }

    private CompletableFuture<Void> postList(TextChannel channel, Map<GameServer, List<KnownUnique>> data) {
        EmbedBuilder msg = new EmbedBuilder();
        msg.setTitle("Known uniques");
        msg.setTimestamp(Instant.now());
        msg.setThumbnail("https://i.imgur.com/B4GPbUm.png");
        AtomicInteger counter = new AtomicInteger();

        Map<Integer, KnownUnique> newMap = new HashMap<>();

        servers.servers.forEach(server -> {
            List<KnownUnique> list = data.get(server);
            if (list.isEmpty()) {
                msg.addField(server.name, "*No known uniques*", false);
            } else {
                msg.addField(server.name,
                        list.stream().map(u -> {
                            int num = counter.incrementAndGet();
                            newMap.put(num, u);
                            return String.format("**[%d]** %s - %s\n", num, u.name,
                                    u.location == null ? "???" :
                                            String.format("%s *(%s)*", u.location, u.reporter));
                        }).collect(Collectors.joining()), false);
            }
        });

        msg.setFooter("To update the list send a message like:\n" +
                "\t!found <number> <location>\n" +
                "Last update:");

        return channel.sendMessage(msg.build()).submit()
                .thenCompose((res) -> {
                    res.pin().queue();
                    logger.info(String.format("Posted new report %s", res.getId()));
                    numberMap = newMap;
                    String oldRep = lastReportId;
                    lastReportId = res.getId();
                    if (oldRep != null) {
                        return channel.deleteMessageById(oldRep).submit().thenAccept((res2) ->
                                logger.info(String.format("Deleted old report %s", oldRep))
                        );
                    } else return CompletableFuture.completedFuture(null);
                }).thenAccept(x ->
                        jda.getPresence().setPresence(OnlineStatus.ONLINE,
                                Activity.watching(String.format("%d uniques", counter.get())))
                );
    }
}
