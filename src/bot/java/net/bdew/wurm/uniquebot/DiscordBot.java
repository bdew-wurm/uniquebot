package net.bdew.wurm.uniquebot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.StatusChangeEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DiscordBot extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger("DiscordBot");
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
        logger.info("Status: {}", event.getNewStatus());
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

            logger.info("Found me={} guild={} channel={}", myUserId, guildId, channelId);

            refreshReport(false).join();

            logger.info("Initial report posted successfully");

            if (updateTask != null) updateTask.cancel();
            updateTask = new TimerTask() {
                @Override
                public void run() {
                    refreshReport(false).join();
                }
            };

            guild.upsertCommand("found", "Update the location of a unique")
                    .addOption(OptionType.INTEGER, "number", "Number of the unique on the list", true)
                    .addOption(OptionType.STRING, "location", "Current location", true)
                    .queue(command -> logger.info("Found command registered"));

            clearOldMessages().thenRun(() -> timer.schedule(updateTask, 30000, 30000));
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        Message message = event.getMessage();
        MessageChannel channel = event.getChannel();

        if (message.getType() == MessageType.CHANNEL_PINNED_ADD && event.getAuthor().isBot() && channel.getId().equals(channelId)) {
            logger.info("Deleting pin message {}", event.getMessageId());
            event.getMessage().delete().queue();
        }
    }

    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("found")) {
            event.deferReply().queue();

            int number = Objects.requireNonNull(event.getOption("number")).getAsInt();
            String location = Objects.requireNonNull(event.getOption("location")).getAsString();

            KnownUnique ent = numberMap.get(number);

            if (ent == null) {
                reject(event.getHook(), "❌ Invalid unique number in command");
                return;
            }

            if (location.isEmpty()) {
                reject(event.getHook(), "❌ Invalid location in command");
                return;
            }

            Member member = event.getMember();

            if (member == null) {
                logger.warn("Can't find member in command interaction: {}", event);
                reject(event.getHook(), "❌ Something went wrong, sorry");
                return;
            }

            String oldLocation = ent.location == null ? "???" : ent.location;
            ent.location = location;
            ent.reporter = member.getEffectiveName();

            db.updateLocation(ent)
                    .thenCompose((v) ->
                            event.getHook().sendMessage(String.format(
                                    "✅ Changing **%s** on **%s** from **\"%s\"** to **\"%s\"**",
                                    ent.name, ent.server, oldLocation, location
                            )).mentionRepliedUser(false).submit()
                    )
                    .thenCompose((v) -> refreshReport(true))
                    .join();
        }
    }

    private void reject(InteractionHook hook, String reason) {
        hook.sendMessage(String.format("<@%s>: %s", hook.getInteraction().getUser().getId(), reason))
                .delay(20, TimeUnit.SECONDS)
                .flatMap(Message::delete)
                .queue();
    }

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        Member member = event.getMember();
        if (event.getMessageId().equals(lastReportId) && !event.getUserId().equals(myUserId) && "U+1F504".equalsIgnoreCase(event.getReaction().getEmoji().asUnicode().getAsCodepoints()) && member != null) {
            logger.info("Report refresh requested by {}", member.getEffectiveName());
            refreshReport(true).join();
        }
    }

    public void start() {
        jda = JDABuilder.create(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS)
                .disableCache(CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS, CacheFlag.EMOJI, CacheFlag.STICKER)
                .addEventListeners(this)
                .build();
    }

    private Optional<TextChannel> getChannel() {
        if (jda == null || guildId == null || channelId == null) return Optional.empty();
        return Optional.ofNullable(jda.getGuildById(guildId))
                .flatMap(g -> Optional.ofNullable(g.getTextChannelById(channelId)));
    }

    private CompletableFuture<Void> clearOldMessages() {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        getChannel().ifPresent(channel -> {
            channel.getHistoryBefore(lastReportId, 100).queue((history) -> {
                List<Message> messages = history.getRetrievedHistory();
                logger.info("Checking {} old messages", messages.size());
                messages.forEach(message -> {
                    if (message.getAuthor().getId().equals(myUserId) && !message.getId().equals(lastReportId) && !message.getEmbeds().isEmpty()) {
                        message.delete().queue((res) -> logger.info("Deleted old report message {}", message.getId()));
                    } else if (message.getAuthor().getId().equals(myUserId) && message.getType() == MessageType.CHANNEL_PINNED_ADD) {
                        message.delete().queue((res) -> logger.info("Deleted old pin message {}", message.getId()));
                    }
                });
                promise.complete(null);
            });
        });
        return promise;
    }

    private CompletableFuture<Void> refreshReport(boolean forcePost) {
        return updater.getUniqueList().thenCompose(report -> {
            logger.info("Refreshed report added={} removed={} force={}", report.added.size(), report.removed.size(), forcePost);
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
                "\t/found <number> <location>\n" +
                "Last update:");

        return channel.sendMessageEmbeds(msg.build()).submit()
                .thenCompose((res) -> {
                    res.pin().queue();
                    logger.info("Posted new report {}", res.getId());
                    numberMap = newMap;
                    String oldRep = lastReportId;
                    lastReportId = res.getId();
                    if (oldRep != null) {
                        return channel.deleteMessageById(oldRep).submit().thenAccept((res2) ->
                                logger.info("Deleted old report {}", oldRep)
                        );
                    } else return CompletableFuture.completedFuture(null);
                }).thenAccept(x ->
                        jda.getPresence().setPresence(OnlineStatus.ONLINE,
                                Activity.watching(String.format("%d uniques", counter.get())))
                );
    }
}
