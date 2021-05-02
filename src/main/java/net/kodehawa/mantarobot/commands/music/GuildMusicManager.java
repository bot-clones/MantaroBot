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

package net.kodehawa.mantarobot.commands.music;

import lavalink.client.io.jda.JdaLink;
import net.kodehawa.mantarobot.MantaroBot;
import net.kodehawa.mantarobot.commands.music.requester.TrackScheduler;
import net.kodehawa.mantarobot.utils.commands.EmoteReference;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GuildMusicManager {
    private final String guildId;

    private final TrackScheduler trackScheduler;
    private boolean isAwaitingDeath;

    private ScheduledFuture<?> leaveTask = null;

    public GuildMusicManager(String guildId) {
        this.guildId = guildId;

        var lavaLink = MantaroBot.getInstance().getLavaLink().getLink(guildId);
        trackScheduler = new TrackScheduler(lavaLink, guildId);

        lavaLink.getPlayer().addListener(trackScheduler);
    }

    private void leave() {
        var guild = trackScheduler.getGuild();

        if (guild == null) {
            getLavaLink().destroy();
            return;
        }

        isAwaitingDeath = false;

        final var requestedTextChannel = trackScheduler.getRequestedTextChannel();
        final var voiceState = guild.getSelfMember().getVoiceState();

        if (requestedTextChannel != null && voiceState != null && voiceState.getChannel() != null) {
            requestedTextChannel.sendMessageFormat(
                    trackScheduler.getLanguage().get("commands.music_general.listener.leave"),
                    EmoteReference.SAD, voiceState.getChannel().getName()
            ).queue();
        }

        //This should destroy it.
        trackScheduler.stop();
    }

    public void scheduleLeave() {
        if (leaveTask != null) {
            return;
        }

        leaveTask = MantaroBot.getInstance().getExecutorService().schedule(this::leave, 2, TimeUnit.MINUTES);
    }

    public void cancelLeave() {
        if (leaveTask == null) {
            return;
        }

        leaveTask.cancel(true);
        leaveTask = null;
    }

    public JdaLink getLavaLink() {
        return MantaroBot.getInstance().getLavaLink().getLink(guildId);
    }

    public TrackScheduler getTrackScheduler() {
        return this.trackScheduler;
    }

    public void destroy() {
        getLavaLink().getPlayer().removeListener(trackScheduler);
        getLavaLink().resetPlayer();
        getLavaLink().destroy();
    }

    public boolean isAwaitingDeath() {
        return this.isAwaitingDeath;
    }

    public void setAwaitingDeath(boolean isAwaitingDeath) {
        this.isAwaitingDeath = isAwaitingDeath;
    }
}
