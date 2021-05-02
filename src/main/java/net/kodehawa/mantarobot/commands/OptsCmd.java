/*
 * Copyright (C) 2016-2021 David Rubio Escares / Kodehawa
 *
 *  Mantaro is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  Mantaro is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Mantaro. If not, see http://www.gnu.org/licenses/
 */

package net.kodehawa.mantarobot.commands;

import com.google.common.eventbus.Subscribe;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.kodehawa.mantarobot.commands.currency.profile.Badge;
import net.kodehawa.mantarobot.core.CommandRegistry;
import net.kodehawa.mantarobot.core.modules.Module;
import net.kodehawa.mantarobot.core.modules.commands.SimpleCommand;
import net.kodehawa.mantarobot.core.modules.commands.base.CommandCategory;
import net.kodehawa.mantarobot.core.modules.commands.base.CommandPermission;
import net.kodehawa.mantarobot.core.modules.commands.base.Context;
import net.kodehawa.mantarobot.core.modules.commands.help.HelpContent;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.db.entities.DBGuild;
import net.kodehawa.mantarobot.options.core.Option;
import net.kodehawa.mantarobot.options.core.OptionType;
import net.kodehawa.mantarobot.utils.StringUtils;
import net.kodehawa.mantarobot.utils.Utils;
import net.kodehawa.mantarobot.utils.commands.DiscordUtils;
import net.kodehawa.mantarobot.utils.commands.EmoteReference;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static net.kodehawa.mantarobot.utils.Utils.mapConfigObjects;

@Module
public class OptsCmd {
    public static SimpleCommand optsCmd;

    public static SimpleCommand getOpts() {
        return optsCmd;
    }

    @Subscribe
    public void register(CommandRegistry registry) {
        registry.register("opts", optsCmd = new SimpleCommand(CommandCategory.MODERATION, CommandPermission.ADMIN) {
            @Override
            protected void call(Context ctx, String content, String[] args) {
                if (args.length == 0) {
                    ctx.sendLocalized("options.error_general", EmoteReference.WARNING);
                    return;
                }

                var languageContext = ctx.getLanguageContext();

                if (args.length == 1 && args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("ls")) {
                    var builder = new StringBuilder();

                    for (var opt : Option.getAvaliableOptions()) {
                        builder.append(opt).append("\n");
                    }

                    var dividedMessages = DiscordUtils.divideString(builder);
                    List<String> messages = new LinkedList<>();
                    for (var msgs : dividedMessages) {
                        messages.add(String.format(languageContext.get("commands.opts.list.header"),
                                ctx.hasReactionPerms() ? languageContext.get("general.text_menu") + " " :
                                        languageContext.get("general.arrow_react"), String.format("```prolog\n%s```", msgs))
                        );
                    }

                    if (ctx.hasReactionPerms()) {
                        DiscordUtils.list(ctx.getEvent(), 45, false, messages);
                    } else {
                        DiscordUtils.listText(ctx.getEvent(), 45, false, messages);
                    }

                    return;
                }

                if (args.length < 2) {
                    ctx.sendLocalized("options.error_general", EmoteReference.WARNING);
                    return;
                }

                var name = new StringBuilder();

                if (args[0].equalsIgnoreCase("help")) {
                    for (int i = 1; i < args.length; i++) {
                        var s = args[i];
                        if (name.length() > 0) {
                            name.append(":");
                        }

                        name.append(s);
                        var option = Option.getOptionMap().get(name.toString());

                        if (option != null) {
                            try {
                                var builder = new EmbedBuilder()
                                        .setAuthor(option.getOptionName(), null, ctx.getAuthor().getEffectiveAvatarUrl())
                                        .setDescription(option.getDescription())
                                        .setThumbnail("https://i.imgur.com/lFTJSE4.png")
                                        .addField(EmoteReference.PENCIL.toHeaderString() + "Type", option.getType().toString(), false);

                                ctx.send(builder.build());
                            } catch (IndexOutOfBoundsException ignored) { }
                            return;
                        }
                    }

                    ctx.sendLocalized("commands.opts.option_not_found", EmoteReference.ERROR);
                    return;
                }

                for (int i = 0; i < args.length; i++) {
                    var str = args[i];
                    if (name.length() > 0) {
                        name.append(":");
                    }

                    name.append(str);
                    var lookup = name.toString().replace("\n", "");
                    var option = Option.getOptionMap().get(lookup);

                    if (option != null) {
                        var callable = option.getEventConsumer();
                        try {
                            String[] a;
                            if (++i < args.length) {
                                a = Arrays.copyOfRange(args, i, args.length);
                            } else {
                                a = StringUtils.EMPTY_ARRAY;
                            }

                            callable.accept(ctx, a);
                            var player = MantaroData.db().getPlayer(ctx.getAuthor());
                            if (player.getData().addBadgeIfAbsent(Badge.DID_THIS_WORK)) {
                                player.saveUpdating();
                            }
                        } catch (IndexOutOfBoundsException ignored) { }
                        return;
                    }
                }

                ctx.sendLocalized("options.error_general", EmoteReference.WARNING);
            }


            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("This command allows you to change Mantaro settings for this server.\n" +
                                "All values set are local and NOT global, meaning that they will only effect this server. " +
                                "No, you can't give away currency or give yourself coins or anything like that.")
                        .setUsage("Check https://github.com/Mantaro/MantaroBot/wiki/Configuration for a guide on how to use opts. Welcome to the jungle.")
                        .build();
            }
        }).addOption("check:data", new Option("Data check.",
                "Checks the data values you have set on this server. **THIS IS NOT USER-FRIENDLY**. " +
                            "If you wanna send this to the support server, use -print at the end.", OptionType.GENERAL
                ).setAction((ctx, args) -> {
                    var dbGuild = ctx.getDBGuild();
                    var guildData = dbGuild.getData();
                    var lang = ctx.getLanguageContext();

                    // Map as follows: name, value
                    // This filters out unused configs.
                    var fieldMap = mapConfigObjects(guildData);
                    if (fieldMap == null) {
                        ctx.sendLocalized("options.check_data.retrieve_failure", EmoteReference.ERROR);
                        return;
                    }

                    final var guild = ctx.getGuild();
                    var opts = StringUtils.parseArguments(args);
                    if (opts.containsKey("print") || opts.containsKey("paste")) {
                        var builder = new StringBuilder();
                        for (var entry : fieldMap.entrySet()) {
                            builder.append("* ").append(entry.getKey()).append(": ").append(entry.getValue().getRight()).append("\n");
                        }

                        ctx.sendFormat("Send this: %s", Utils.paste(builder.toString()));
                        return;
                    }

                    var embedBuilder = new EmbedBuilder();
                    embedBuilder.setAuthor("Option Debug", null, ctx.getAuthor().getEffectiveAvatarUrl())
                            .setDescription(
                                    String.format(lang.get("options.check_data.header") + lang.get("options.check_data.terminology"),
                                    guild.getName())
                            )
                            .setThumbnail(guild.getIconUrl())
                            .setFooter(lang.get("options.check_data.footer"), null);

                    List<MessageEmbed.Field> fields = new LinkedList<>();
                    for (var e : fieldMap.entrySet()) {
                        fields.add(new MessageEmbed.Field(EmoteReference.BLUE_SMALL_MARKER + e.getKey() + ":\n" + e.getValue().getLeft() + "",
                                e.getValue() == null ? lang.get("options.check_data.null_set") : String.valueOf(e.getValue().getRight()),
                                false)
                        );
                    }

                    var splitFields = DiscordUtils.divideFields(6, fields);
                    if (ctx.hasReactionPerms()) {
                        DiscordUtils.list(ctx.getEvent(), 200, false, embedBuilder, splitFields);
                    }
                    else {
                        DiscordUtils.listText(ctx.getEvent(), 200, false, embedBuilder, splitFields);
                    }
                }).setShortDescription("Checks the data values you have set on this server.")
        ).addOption("reset:all", new Option("Options reset.",
                "Resets all options set on this server.", OptionType.GENERAL).setAction((ctx) -> {
                    //Temporary stuff.
                    var dbGuild = ctx.getDBGuild();
                    // New object?
                    var temp = ctx.getDBGuild().getData();

                    //The persistent data we wish to maintain.
                    var premiumKey = temp.getPremiumKey();
                    var quoteLastId = temp.getQuoteLastId();
                    var ranPolls = temp.getQuoteLastId();
                    var gameTimeoutExpectedAt = temp.getGameTimeoutExpectedAt();
                    var cases = temp.getCases();
                    var allowedBirthdays = temp.getAllowedBirthdays();
                    var notified = temp.isNotifiedFromBirthdayChange();
                    var greetReceived = temp.hasReceivedGreet();

                    //Assign everything all over again
                    var newDbGuild = DBGuild.of(dbGuild.getId(), dbGuild.getPremiumUntil());
                    var newTmp = newDbGuild.getData();

                    newTmp.setGameTimeoutExpectedAt(gameTimeoutExpectedAt);
                    newTmp.setRanPolls(ranPolls);
                    newTmp.setCases(cases);
                    newTmp.setPremiumKey(premiumKey);
                    newTmp.setQuoteLastId(quoteLastId);
                    newTmp.setAllowedBirthdays(allowedBirthdays);
                    newTmp.setNotifiedFromBirthdayChange(notified);
                    newTmp.setHasReceivedGreet(greetReceived);

                    newDbGuild.saveAsync();

                    ctx.sendLocalized("options.reset_all.success", EmoteReference.CORRECT);
                })
        );
    }
}
