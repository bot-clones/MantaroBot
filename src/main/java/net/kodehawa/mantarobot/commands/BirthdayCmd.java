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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.eventbus.Subscribe;
import net.dv8tion.jda.api.entities.Member;
import net.kodehawa.mantarobot.MantaroBot;
import net.kodehawa.mantarobot.commands.utils.birthday.BirthdayCacher;
import net.kodehawa.mantarobot.core.CommandRegistry;
import net.kodehawa.mantarobot.core.modules.Module;
import net.kodehawa.mantarobot.core.modules.commands.SubCommand;
import net.kodehawa.mantarobot.core.modules.commands.TreeCommand;
import net.kodehawa.mantarobot.core.modules.commands.base.Command;
import net.kodehawa.mantarobot.core.modules.commands.base.CommandCategory;
import net.kodehawa.mantarobot.core.modules.commands.base.Context;
import net.kodehawa.mantarobot.core.modules.commands.help.HelpContent;
import net.kodehawa.mantarobot.core.modules.commands.i18n.I18nContext;
import net.kodehawa.mantarobot.db.entities.DBUser;
import net.kodehawa.mantarobot.utils.StringUtils;
import net.kodehawa.mantarobot.utils.Utils;
import net.kodehawa.mantarobot.utils.commands.DiscordUtils;
import net.kodehawa.mantarobot.utils.commands.EmoteReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Module
public class BirthdayCmd {
    private static final Logger log = LoggerFactory.getLogger(BirthdayCmd.class);

    // This will get invalidated as a whole every 23 hours, we don't really *need* to expire entries here
    // because BirthdayCacher will make it so it refreshes at the same period as the BirthdayCacher gets refreshed.
    // Therefore the birthday list here can be kept up-to-date.
    private static final Cache<Long, ConcurrentHashMap<Long, BirthdayCacher.BirthdayData>> guildBirthdayCache = CacheBuilder.newBuilder()
            .maximumSize(2500)
            .build();

    @Subscribe
    public void birthday(CommandRegistry registry) {
        TreeCommand birthdayCommand = registry.register("birthday", new TreeCommand(CommandCategory.UTILS) {
            @Override
            public Command defaultTrigger(Context context, String mainCommand, String commandName) {
                return new SubCommand() {
                    @Override
                    protected void call(Context ctx, I18nContext languageContext, String content) {
                        if (content.isEmpty()) {
                            ctx.sendLocalized("commands.birthday.no_content", EmoteReference.ERROR);
                            return;
                        }

                        // Twice. Yep.
                        var parseFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy");
                        var displayFormat = DateTimeFormatter.ofPattern("dd-MM");
                        MonthDay birthdayDate;
                        String date;
                        var extra = "";

                        try {
                            String birthday;
                            birthday = content.replace("/", "-");
                            var parts = new ArrayList<>(Arrays.asList(birthday.split("-")));

                            if (Integer.parseInt(parts.get(0)) > 31 || Integer.parseInt(parts.get(1)) > 12) {
                                ctx.sendLocalized("commands.birthday.error_date", EmoteReference.ERROR);
                                return;
                            }

                            if (parts.size() > 2) {
                                ctx.sendLocalized("commands.birthday.new_format", EmoteReference.ERROR);
                                return;
                            }

                            //Add a year so it parses and saves using the old format. Yes, this is also cursed.
                            parts.add("2037");
                            date = String.join("-", parts);
                            birthdayDate = MonthDay.parse(birthday, displayFormat);
                        } catch (Exception e) {
                            ctx.sendStrippedLocalized("commands.birthday.error_date", EmoteReference.ERROR);
                            return;
                        }

                        final var display = displayFormat.format(birthdayDate);
                        // This whole leap year stuff is cursed when you work with dates using raw strings tbh.
                        // Only I could come up with such an idea like this on 2016. Now I regret it with pain... peko.
                        var leap = display.equals("29-02");
                        if (leap) {
                            extra += "\n" + languageContext.get("commands.birthday.leap");
                            date = date.replace("2037", "2036"); // Cursed workaround since 2036 is a leap.
                        }

                        final var birthdayFormat = parseFormat.format(parseFormat.parse(date));

                        //Actually save it to the user's profile.
                        DBUser dbUser = ctx.getDBUser();
                        dbUser.getData().setBirthday(birthdayFormat);
                        dbUser.saveUpdating();

                        ctx.sendLocalized("commands.birthday.added_birthdate", EmoteReference.CORRECT, display, extra);
                    }
                };
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("Sets your birthday date. Only useful if the server has enabled this functionality")
                        .setUsage("`~>birthday <date>`")
                        .addParameter("date", "A date in dd-mm format (13-02 for example). Check subcommands for more options.")
                        .build();
            }
        });

        birthdayCommand.addSubCommand("allowserver", new SubCommand() {
            public String description() {
                return "Allows the server where you send this command to announce your birthday.";
            }

            @Override
            protected void call(Context ctx, I18nContext languageContext, String content) {
                var dbGuild = ctx.getDBGuild();
                var guildData = dbGuild.getData();

                if (guildData.getAllowedBirthdays().contains(ctx.getAuthor().getId())) {
                    ctx.sendLocalized("commands.birthday.already_allowed", EmoteReference.ERROR);
                    return;
                }

                guildData.getAllowedBirthdays().add(ctx.getAuthor().getId());
                dbGuild.save();

                var cached = guildBirthdayCache.getIfPresent(ctx.getGuild().getIdLong());
                var cachedBirthday = ctx.getBot().getBirthdayCacher().getCachedBirthdays().get(ctx.getUser().getIdLong());
                if (cached != null && cachedBirthday != null) {
                    cached.put(ctx.getUser().getIdLong(), cachedBirthday);
                }

                ctx.sendLocalized("commands.birthday.allowed_server", EmoteReference.CORRECT);
            }
        });

        birthdayCommand.addSubCommand("denyserver", new SubCommand() {
            public String description() {
                return "Denies the server where you send this command to announce your birthday.";
            }

            @Override
            protected void call(Context ctx, I18nContext languageContext, String content) {
                var dbGuild = ctx.getDBGuild();
                var guildData = dbGuild.getData();

                if (!guildData.getAllowedBirthdays().contains(ctx.getAuthor().getId())) {
                    ctx.sendLocalized("commands.birthday.already_denied", EmoteReference.CORRECT);
                    return;
                }

                guildData.getAllowedBirthdays().remove(ctx.getAuthor().getId());
                dbGuild.save();

                var cached = guildBirthdayCache.getIfPresent(ctx.getGuild().getIdLong());
                if (cached != null) {
                    cached.remove(ctx.getUser().getIdLong());
                }

                ctx.sendLocalized("commands.birthday.denied", EmoteReference.CORRECT);
            }
        });

        birthdayCommand.addSubCommand("remove", new SubCommand() {
            @Override
            public String description() {
                return "Removes your set birthday date.";
            }

            @Override
            protected void call(Context ctx, I18nContext languageContext, String content) {
                var user = ctx.getDBUser();
                user.getData().setBirthday(null);
                user.save();

                ctx.sendLocalized("commands.birthday.reset", EmoteReference.CORRECT);
            }
        });

        birthdayCommand.addSubCommand("list", new SubCommand() {
            @Override
            public String description() {
                return "Gives all of the birthdays for this server.";
            }

            @Override
            protected void call(Context ctx, I18nContext languageContext, String content) {
                BirthdayCacher cacher = MantaroBot.getInstance().getBirthdayCacher();

                try {
                    if (cacher != null) {
                        var globalBirthdays = cacher.getCachedBirthdays();
                        if (globalBirthdays.isEmpty()) {
                            ctx.sendLocalized("commands.birthday.no_global_birthdays", EmoteReference.SAD);
                            return;
                        }

                        var guild = ctx.getGuild();
                        var data = ctx.getDBGuild().getData();
                        var ids = data.getAllowedBirthdays().stream().map(Long::parseUnsignedLong).collect(Collectors.toList());

                        if (ids.isEmpty()) {
                            ctx.sendLocalized("commands.birthday.no_guild_birthdays", EmoteReference.ERROR);
                            return;
                        }

                        var guildCurrentBirthdays = getBirthdayMap(ctx.getGuild().getIdLong(), ids);
                        if (guildCurrentBirthdays.isEmpty()) {
                            ctx.sendLocalized("commands.birthday.no_guild_birthdays", EmoteReference.ERROR);
                            return;
                        }

                        var birthdays = guildCurrentBirthdays.entrySet().stream()
                                .sorted(Comparator.comparingLong(i -> i.getValue().day))
                                .filter(birthday -> ids.contains(birthday.getKey()))
                                .limit(100)
                                .collect(Collectors.toList());


                        var bdIds = birthdays.stream().map(Map.Entry::getKey).collect(Collectors.toList());
                        guild.retrieveMembersByIds(bdIds).onSuccess(members ->
                                sendBirthdayList(ctx, members, guildCurrentBirthdays, null, false)
                        );
                    } else {
                        ctx.sendLocalized("commands.birthday.cache_not_running", EmoteReference.SAD);
                    }
                } catch (Exception e) {
                    ctx.sendLocalized("commands.birthday.error", EmoteReference.SAD);
                    log.error("Error on birthday list display!", e);
                }
            }
        });

        birthdayCommand.addSubCommand("month", new SubCommand() {
            @Override
            public String description() {
                return "Checks the current birthday date for the specified month. Example: `~>birthday month 1`";
            }

            @Override
            protected void call(Context ctx, I18nContext languageContext, String content) {
                var args = ctx.getArguments();
                var cacher = MantaroBot.getInstance().getBirthdayCacher();
                var calendar = Calendar.getInstance();
                var month = calendar.get(Calendar.MONTH);

                var msg = "";
                if (args.length == 1) {
                    msg = args[0];
                }

                if (!msg.isEmpty()) {
                    try {
                        month = Integer.parseInt(msg);
                        if (month < 1 || month > 12) {
                            ctx.sendLocalized("commands.birthday.invalid_month", EmoteReference.ERROR);
                            return;
                        }

                        // Substract here so we can do the check properly up there.
                        month = month - 1;
                    } catch (NumberFormatException e) {
                        ctx.sendLocalized("commands.birthday.invalid_month", EmoteReference.ERROR);
                        return;
                    }
                }

                calendar.set(calendar.get(Calendar.YEAR), month, Calendar.MONDAY);

                try {
                    if (cacher != null) {
                        final var cachedBirthdays = cacher.getCachedBirthdays();
                        if (cachedBirthdays.isEmpty()) {
                            ctx.sendLocalized("commands.birthday.no_global_birthdays", EmoteReference.SAD);
                            return;
                        }

                        var data = ctx.getDBGuild().getData();
                        var ids = data.getAllowedBirthdays().stream().map(Long::parseUnsignedLong).collect(Collectors.toList());
                        var guildCurrentBirthdays = getBirthdayMap(ctx.getGuild().getIdLong(), ids);

                        if (ids.isEmpty()) {
                            ctx.sendLocalized("commands.birthday.no_guild_birthdays", EmoteReference.ERROR);
                            return;
                        }

                        var calendarMonth = calendar.get(Calendar.MONTH) + 1;
                        var birthdays = guildCurrentBirthdays.entrySet().stream()
                                .filter(bds -> bds.getValue().month == calendarMonth)
                                .sorted(Comparator.comparingLong(i -> i.getValue().day))
                                .limit(100)
                                .collect(Collectors.toList());

                        if (birthdays.isEmpty()) {
                            ctx.sendLocalized("commands.birthday.no_guild_month_birthdays", EmoteReference.ERROR, month + 1, EmoteReference.BLUE_SMALL_MARKER);
                            return;
                        }

                        var bdIds = birthdays.stream().map(Map.Entry::getKey).collect(Collectors.toList());
                        ctx.getGuild().retrieveMembersByIds(bdIds).onSuccess(members ->
                                sendBirthdayList(ctx, members, guildCurrentBirthdays, calendar, true)
                        );
                    } else {
                        ctx.sendLocalized("commands.birthday.cache_not_running", EmoteReference.SAD);
                    }
                } catch (Exception e) {
                    ctx.sendLocalized("commands.birthday.error", EmoteReference.SAD);
                    log.error("Error on birthday month display!", e);
                }
            }
        });
    }

    private void sendBirthdayList(Context ctx, List<Member> members, Map<Long, BirthdayCacher.BirthdayData> guildCurrentBirthdays,
                                  Calendar calendar, boolean month) {
        StringBuilder builder = new StringBuilder();
        var languageContext = ctx.getLanguageContext();
        var guild = ctx.getGuild();
        var memberSort = members.stream()
                .sorted(Comparator.comparingInt(i -> {
                    var bd = guildCurrentBirthdays.get(i.getIdLong());
                    // So I don't forget later: this is equivalent to day + (month * 31)
                    // And this is so we get a stable sort of day/month
                    return (int) (bd.day + (bd.month << 5));
                }))
                .collect(Collectors.toList());

        for (Member member : memberSort) {
            var birthday = guildCurrentBirthdays.get(member.getIdLong());
            builder.append("+ %-20s : %s ".formatted(StringUtils.limit(member.getEffectiveName(), 20), birthday.getDay() + "-" + birthday.getMonth()));
            builder.append("\n");
        }

        var parts = DiscordUtils.divideString(1000, '\n', builder);
        var hasReactionPerms = ctx.hasReactionPerms();

        List<String> messages = new LinkedList<>();
        for (String part : parts) {
            var help = languageContext.get("general.arrow_react");
            if (!hasReactionPerms) {
                help = languageContext.get("general.text_menu");
            }

            if (month && calendar != null) {
                messages.add(
                        languageContext.get("commands.birthday.header").formatted(
                                ctx.getGuild().getName(),
                                Utils.capitalize(calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH))
                        ) + "```diff\n%s```".formatted(part)
                );
            } else {
                messages.add(languageContext.get("commands.birthday.full_header")
                        .formatted(guild.getName(), (parts.size() > 1 ? help : "") + "```diff\n%s```".formatted(part))
                );
            }
        }

        if (parts.isEmpty()) {
            ctx.sendLocalized("commands.birthday.no_guild_birthdays", EmoteReference.ERROR);
            return;
        }

        if (hasReactionPerms) {
            DiscordUtils.list(ctx.getEvent(), 45, false, messages);
        } else {
            DiscordUtils.listText(ctx.getEvent(), 45, false, messages);
        }
    }

    public static Cache<Long, ConcurrentHashMap<Long, BirthdayCacher.BirthdayData>> getGuildBirthdayCache() {
        return guildBirthdayCache;
    }

    private ConcurrentHashMap<Long, BirthdayCacher.BirthdayData> getBirthdayMap(long guildId, List<Long> allowed) {
        ConcurrentHashMap<Long, BirthdayCacher.BirthdayData> guildCurrentBirthdays = new ConcurrentHashMap<>();
        final var cachedBirthdays = MantaroBot.getInstance().getBirthdayCacher().getCachedBirthdays();

        var cached = guildBirthdayCache.getIfPresent(guildId);
        if (cached != null && cached.size() >= 1) {
            guildCurrentBirthdays = cached;
        } else {
            for (var birthdays : cachedBirthdays.entrySet()) {
                if (allowed.contains(birthdays.getKey())) {
                    guildCurrentBirthdays.put(birthdays.getKey(), birthdays.getValue());
                }
            }

            // Populate guild cache
            if (guildCurrentBirthdays.size() >= 1) {
                guildBirthdayCache.put(guildId, guildCurrentBirthdays);
            }
        }

        return guildCurrentBirthdays;
    }
}
