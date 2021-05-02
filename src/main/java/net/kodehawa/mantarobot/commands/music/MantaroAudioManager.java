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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import com.sedmelluq.lava.extensions.youtuberotator.YoutubeIpRotatorSetup;
import com.sedmelluq.lava.extensions.youtuberotator.planner.AbstractRoutePlanner;
import com.sedmelluq.lava.extensions.youtuberotator.planner.RotatingNanoIpRoutePlanner;
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.IpBlock;
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.Ipv6Block;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.kodehawa.mantarobot.commands.music.requester.AudioLoader;
import net.kodehawa.mantarobot.commands.music.utils.AudioCmdUtils;
import net.kodehawa.mantarobot.core.modules.commands.i18n.I18nContext;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.utils.Lazy;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MantaroAudioManager {
    private static final Lazy<Executor> LOAD_EXECUTOR = new Lazy<>(() -> Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
                    .setNameFormat("AudioLoadThread-%d")
                    .setDaemon(true)
                    .build()
    ));

    private final Map<String, GuildMusicManager> musicManagers;
    private final AudioPlayerManager playerManager;

    @SuppressWarnings("rawtypes")
    public MantaroAudioManager() {
        this.musicManagers = new ConcurrentHashMap<>();
        this.playerManager = new DefaultAudioPlayerManager();

        //Youtube is special because rotation stuff.
        var youtubeAudioSourceManager = new YoutubeAudioSourceManager(true);

        //IPv6 rotation config start
        var config = MantaroData.config().get();
        if (!config.getIpv6Block().isEmpty()) {
            AbstractRoutePlanner planner;
            var block = config.getIpv6Block();
            List<IpBlock> blocks = Collections.singletonList(new Ipv6Block(block));

            //Damn you, YouTube.
            if (config.getExcludeAddress().isEmpty()) {
                planner = new RotatingNanoIpRoutePlanner(blocks);
            } else {
                try {
                    var blacklistedGW = InetAddress.getByName(config.getExcludeAddress());
                    planner = new RotatingNanoIpRoutePlanner(
                            blocks, inetAddress -> !inetAddress.equals(blacklistedGW)
                    );
                } catch (Exception e) {
                    //Fallback: did I screw up putting the IP in? lmao
                    planner = new RotatingNanoIpRoutePlanner(blocks);
                    e.printStackTrace();
                }
            }

            new YoutubeIpRotatorSetup(planner)
                    .forSource(youtubeAudioSourceManager)
                    .setup();
        }
        //IPv6 rotation config end

        //Register source manager and configure the Player
        playerManager.registerSourceManager(youtubeAudioSourceManager);
        playerManager.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
        playerManager.registerSourceManager(new BandcampAudioSourceManager());
        playerManager.registerSourceManager(new VimeoAudioSourceManager());
        playerManager.registerSourceManager(new TwitchStreamAudioSourceManager());
        playerManager.registerSourceManager(new BeamAudioSourceManager());
    }

    public GuildMusicManager getMusicManager(Guild guild) {
        return musicManagers.computeIfAbsent(guild.getId(), id -> new GuildMusicManager(guild.getId()));
    }

    public void resetMusicManagerFor(String id) {
        var previousManager = musicManagers.get(id);
        previousManager.destroy();

        musicManagers.remove(id);
    }

    public long getTotalQueueSize() {
        return musicManagers.values().stream().map(m -> m.getTrackScheduler().getQueue().size()).mapToInt(Integer::intValue).sum();
    }

    public void loadAndPlay(GuildMessageReceivedEvent event, String trackUrl,
                            boolean skipSelection, boolean addFirst, I18nContext lang) {
        AudioCmdUtils.connectToVoiceChannel(event, lang).thenAcceptAsync(bool -> {
            if (bool) {
                var musicManager = getMusicManager(event.getGuild());
                var scheduler = musicManager.getTrackScheduler();

                scheduler.getMusicPlayer().setPaused(false);

                if (scheduler.getQueue().isEmpty()) {
                    scheduler.setRepeatMode(null);
                }

                var loader = new AudioLoader(musicManager, event, skipSelection, addFirst);
                playerManager.loadItemOrdered(musicManager, trackUrl, loader);
            }
        }, LOAD_EXECUTOR.get());
    }

    public Map<String, GuildMusicManager> getMusicManagers() {
        return this.musicManagers;
    }

    public AudioPlayerManager getPlayerManager() {
        return this.playerManager;
    }
}
