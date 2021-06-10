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
import net.kodehawa.mantarobot.commands.currency.TextChannelGround;
import net.kodehawa.mantarobot.commands.currency.item.ItemReference;
import net.kodehawa.mantarobot.commands.currency.profile.Badge;
import net.kodehawa.mantarobot.core.CommandRegistry;
import net.kodehawa.mantarobot.core.modules.Module;
import net.kodehawa.mantarobot.core.modules.commands.SimpleCommand;
import net.kodehawa.mantarobot.core.modules.commands.base.CommandCategory;
import net.kodehawa.mantarobot.core.modules.commands.base.Context;
import net.kodehawa.mantarobot.core.modules.commands.help.HelpContent;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.utils.Utils;
import net.kodehawa.mantarobot.utils.commands.EmoteReference;
import net.kodehawa.mantarobot.utils.commands.RPGDice;
import net.kodehawa.mantarobot.utils.commands.ratelimit.IncreasingRateLimiter;
import net.kodehawa.mantarobot.utils.commands.ratelimit.RatelimitUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Module
public class FunCmds {
    private final Random r = new Random();

    @Subscribe
    public void coinflip(CommandRegistry cr) {
        cr.register("coinflip", new SimpleCommand(CommandCategory.FUN) {
            @Override
            protected void call(Context ctx, String content, String[] args) {
                int times;
                if (args.length == 0 || content.length() == 0) times = 1;
                else {
                    try {
                        times = Integer.parseInt(args[0]);
                        if (times > 1000) {
                            ctx.sendLocalized("commands.coinflip.over_limit", EmoteReference.ERROR);
                            return;
                        }
                    } catch (NumberFormatException nfe) {
                        ctx.sendLocalized("commands.coinflip.no_repetitions", EmoteReference.ERROR);
                        return;
                    }
                }

                final int[] heads = {0};
                final int[] tails = {0};

                doTimes(times, () -> {
                    if (r.nextBoolean()) heads[0]++;
                    else tails[0]++;
                });

                ctx.sendLocalized("commands.coinflip.success", EmoteReference.PENNY, times, heads[0], tails[0]);
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("Flips a coin with a defined number of repetitions.")
                        .setUsage("`~>coinflip <times>` - Flips a coin x number of times")
                        .addParameter("times", "Amount of times you want to flip the coin.")
                        .build();
            }
        });
    }

    @Subscribe
    public void ratewaifu(CommandRegistry cr) {
        cr.register("ratewaifu", new SimpleCommand(CommandCategory.FUN) {
            @Override
            protected void call(Context ctx, String content, String[] args) {
                if (args.length == 0) {
                    ctx.sendLocalized("commands.ratewaifu.nothing_specified", EmoteReference.ERROR);
                    return;
                }

                var waifuRate = content.replaceAll("\\s+", " ")
                        .replaceAll("<@!?(\\d+)>", "<@$1>").chars().sum() % 101;

                //hehe~
                if (content.equalsIgnoreCase("mantaro")) {
                    waifuRate = 100;
                }

                ctx.sendStrippedLocalized("commands.ratewaifu.success", EmoteReference.THINKING, waifuRate);
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("Just rates your waifu from zero to 100. Results may vary.")
                        .setUsage("`~>ratewaifu <@user>` - Rates your waifu.")
                        .addParameter("@user",
                                "The waifu to rate (results may vary, not dependant on profile waifu score)")
                        .build();
            }
        });

        cr.registerAlias("ratewaifu", "rw");
        cr.registerAlias("ratewaifu", "rate");

    }

    @Subscribe
    public void roll(CommandRegistry registry) {
        final IncreasingRateLimiter rateLimiter = new IncreasingRateLimiter.Builder()
                .limit(1)
                .spamTolerance(2)
                .cooldown(5, TimeUnit.SECONDS)
                .maxCooldown(1, TimeUnit.MINUTES)
                .randomIncrement(true)
                .pool(MantaroData.getDefaultJedisPool())
                .prefix("roll")
                .build();

        registry.register("roll", new SimpleCommand(CommandCategory.FUN) {
            @Override
            protected void call(Context ctx, String content, String[] args) {
                if (!RatelimitUtils.ratelimit(rateLimiter, ctx)) {
                    return;
                }

                var opts = ctx.getOptionalArguments();
                int size = 6, amount = 1;

                var d20 = RPGDice.parse(content);
                if (d20 != null) {
                    size = d20.getFaces();
                    amount = d20.getRolls();
                } else { // Verbose format
                    if (opts.containsKey("size")) {
                        try {
                            size = Integer.parseInt(opts.get("size"));
                        } catch (Exception ignored) { }
                    }

                    if (opts.containsKey("amount")) {
                        try {
                            amount = Integer.parseInt(opts.get("amount"));
                        } catch (Exception ignored) { }
                    } else if (opts.containsKey(null)) { //Backwards Compatibility
                        try {
                            amount = Integer.parseInt(opts.get(null));
                        } catch (Exception ignored) { }
                    }
                }

                if (amount >= 100) {
                    amount = 100;
                }

                var result = diceRoll(size, amount);
                if (size == 6 && result == 6) {
                    var player = MantaroData.db().getPlayer(ctx.getAuthor());
                    player.getData().addBadgeIfAbsent(Badge.LUCK_BEHIND);
                    player.saveUpdating();
                }

                ctx.sendLocalized("commands.roll.success",
                        EmoteReference.DICE,
                        result,
                        amount == 1 ? "!" : "\nDoing **%d** rolls.".formatted(amount)
                );

                TextChannelGround.of(ctx.getChannel()).dropItemWithChance(ItemReference.LOADED_DICE, 5);
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription(
                                """
                                Roll a any-sided dice a 1 or more times.
                                By default, this command will roll a 6-sized dice 1 time.
                                """
                        )
                        .setUsage(
                              """
                              `~>roll [times] [-amount <number>] [-size <number>]`: Rolls a dice of the specified size the specified times.
                              D20 Format: For this, 1d20 would be `~>roll -size 20 -amount 1` or just `1d20` (aka DND format)
                              """
                        )
                        .addParameter("-amount", "The amount you want (example: -amount 20)")
                        .addParameter("-size", "The size of the dice (example: -size 7)")
                        .addParameter("times", "The amount of times to roll the dice. Can also be D20 format.")
                        .build();
            }
        });
    }

    @Subscribe
    public void love(CommandRegistry registry) {
        final SecureRandom random = new SecureRandom();
        registry.register("love", new SimpleCommand(CommandCategory.FUN) {
            @Override
            protected void call(Context ctx, String content, String[] args) {
                var mentioned = ctx.getMentionedUsers();
                String result;

                if (mentioned.size() < 1) {
                    ctx.sendLocalized("commands.love.no_mention", EmoteReference.ERROR);
                    return;
                }

                long[] ids = new long[2];
                List<String> listDisplay = new ArrayList<>();
                String toDisplay;

                listDisplay.add("\uD83D\uDC97  %s#%s"
                        .formatted(mentioned.get(0).getName(), mentioned.get(0).getDiscriminator())
                );

                listDisplay.add("\uD83D\uDC97  %s#%s"
                        .formatted(ctx.getAuthor().getName(), ctx.getAuthor().getDiscriminator())
                );

                toDisplay = String.join("\n", listDisplay);

                if (mentioned.size() > 1) {
                    ids[0] = mentioned.get(0).getIdLong();
                    ids[1] = mentioned.get(1).getIdLong();
                    toDisplay = mentioned.stream()
                            .map(user -> "\uD83D\uDC97 %s".formatted(user.getAsTag()))
                            .collect(Collectors.joining("\n"));
                } else {
                    ids[0] = ctx.getAuthor().getIdLong();
                    ids[1] = mentioned.get(0).getIdLong();
                }

                var percentage = (ids[0] == ids[1] ? 101 : random.nextInt(101)); // last value is exclusive, so 101.
                var languageContext = ctx.getLanguageContext();

                final var marriage = ctx.getMarriage(ctx.getDBUser().getData());
                if (marriage != null && mentioned.size() == 1) {
                    final var other = marriage.getOtherPlayer(ctx.getAuthor().getId());
                    if (other.equals(mentioned.get(0).getId())) {
                        percentage = 100;
                    }
                }

                if (percentage < 45) {
                    result = languageContext.get("commands.love.not_ideal");
                } else if (percentage < 75) {
                    result = languageContext.get("commands.love.decent");
                } else if (percentage < 100) {
                    result = languageContext.get("commands.love.nice");
                } else {
                    result = languageContext.get("commands.love.perfect");
                    if (percentage == 101) {
                        result = languageContext.get("commands.love.yourself_note");
                    }
                }

                var loveEmbed = new EmbedBuilder()
                        .setAuthor("\u2764 " + languageContext.get("commands.love.header") + " \u2764", null,
                                ctx.getAuthor().getEffectiveAvatarUrl())
                        .setThumbnail(ctx.getAuthor().getEffectiveAvatarUrl())
                        .setDescription("\n**" + toDisplay + "**\n\n" +
                                percentage + "% **\\|\\|**  " +
                                Utils.bar(percentage, 30) + "  **\\|\\|** \n\n" +
                                "**" + languageContext.get("commands.love.result") + "** " + result
                        ).setColor(ctx.getMember().getColor())
                        .build();

                ctx.send(loveEmbed);
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription(
                                """
                                Calculates the love between 2 discord users. Results may vary.
                                You can either mention one user (matches with yourself) or two (matches 2 users)
                                """
                        )
                        .setUsage("`~>love <@user>`")
                        .addParameter("@user", "The user to check against.")
                        .build();
            }
        });
    }

    private long diceRoll(int size, int amount) {
        long sum = 0;
        for (int i = 0; i < amount; i++) {
            sum += r.nextInt(size) + 1;
        }

        return sum;
    }
}
