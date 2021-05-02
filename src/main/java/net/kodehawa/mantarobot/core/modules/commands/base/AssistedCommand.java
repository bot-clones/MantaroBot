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

package net.kodehawa.mantarobot.core.modules.commands.base;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.kodehawa.mantarobot.options.core.Option;
import net.kodehawa.mantarobot.utils.Utils;

/**
 * "Assisted" version of the {@link Command} interface, providing some "common ground" for all Commands based on it.
 */
public interface AssistedCommand extends Command {

    default EmbedBuilder baseEmbed(Context ctx, String name) {
        return baseEmbed(ctx.getEvent(), name);
    }

    default EmbedBuilder baseEmbed(Context ctx, String name, String image) {
        return baseEmbed(ctx.getEvent(), name, image);
    }

    default EmbedBuilder baseEmbed(GuildMessageReceivedEvent event, String name) {
        return baseEmbed(event, name, event.getAuthor().getEffectiveAvatarUrl());
    }

    default EmbedBuilder baseEmbed(GuildMessageReceivedEvent event, String name, String image) {
        return new EmbedBuilder()
                .setAuthor(name, null, image)
                .setColor(event.getMember().getColor())
                .setFooter("Requested by: %s".formatted(event.getMember().getEffectiveName()),
                        event.getGuild().getIconUrl()
                );
    }

    default void doTimes(int times, Runnable runnable) {
        for (int i = 0; i < times; i++) runnable.run();
    }

    @Override
    default Command addOption(String call, Option option) {
        Option.addOption(call, option);
        return this;
    }
}
