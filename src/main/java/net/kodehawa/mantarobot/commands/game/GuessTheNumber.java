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

package net.kodehawa.mantarobot.commands.game;

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.kodehawa.mantarobot.commands.currency.TextChannelGround;
import net.kodehawa.mantarobot.commands.currency.item.ItemReference;
import net.kodehawa.mantarobot.commands.currency.profile.Badge;
import net.kodehawa.mantarobot.commands.currency.seasons.helpers.UnifiedPlayer;
import net.kodehawa.mantarobot.commands.game.core.Game;
import net.kodehawa.mantarobot.commands.game.core.GameLobby;
import net.kodehawa.mantarobot.core.listeners.operations.InteractiveOperations;
import net.kodehawa.mantarobot.core.listeners.operations.core.InteractiveOperation;
import net.kodehawa.mantarobot.core.listeners.operations.core.Operation;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.utils.commands.EmoteReference;

import java.util.List;
import java.util.Random;

public class GuessTheNumber extends Game<Object> {
    private static final int maxAttempts = 5;
    private final Random r = new Random();
    private int attempts = 1;
    private int number = 0; //set to random number on game start

    @Override
    public void call(GameLobby lobby, List<String> players) {
        //This class is not using Game<T>#callDefault due to it being custom/way too different from the default ones (aka give hints/etc)
        InteractiveOperations.create(lobby.getChannel(), Long.parseLong(lobby.getPlayers().get(0)), 60, new InteractiveOperation() {
            @Override
            public int run(GuildMessageReceivedEvent e) {
                final var channel = lobby.getChannel();
                if (!e.getChannel().getId().equals(channel.getId())) {
                    return Operation.IGNORED;
                }

                final var contentRaw = e.getMessage().getContentRaw();
                final var languageContext = lobby.getLanguageContext();

                for (var s : MantaroData.config().get().getPrefix()) {
                    if (contentRaw.startsWith(s)) {
                        return Operation.IGNORED;
                    }
                }

                if (players.contains(e.getAuthor().getId())) {
                    if (contentRaw.equalsIgnoreCase("end")) {
                        channel.sendMessageFormat(languageContext.get("commands.game.number.ended_game"),
                                EmoteReference.CORRECT, number
                        ).queue();

                        lobby.startNextGame(true);
                        return Operation.COMPLETED;
                    }

                    if (contentRaw.equalsIgnoreCase("endlobby")) {
                        channel.sendMessageFormat(languageContext.get("commands.game.lobby.ended_lobby"),
                                EmoteReference.CORRECT
                        ).queue();

                        lobby.getGamesToPlay().clear();
                        lobby.startNextGame(true);
                        return Operation.COMPLETED;
                    }

                    int parsedAnswer;

                    try {
                        parsedAnswer = Integer.parseInt(contentRaw);
                    } catch (NumberFormatException ex) {
                        channel.sendMessageFormat(languageContext.get("commands.game.number.nan"),
                                EmoteReference.ERROR
                        ).queue();

                        attempts = attempts + 1;
                        return Operation.IGNORED;
                    }

                    if (contentRaw.equals(String.valueOf(number))) {
                        var unifiedPlayer = UnifiedPlayer.of(e.getAuthor(), config.getCurrentSeason());
                        var player = unifiedPlayer.getPlayer();
                        var seasonalPlayer = unifiedPlayer.getSeasonalPlayer();
                        var gains = 140;

                        unifiedPlayer.addMoney(gains);
                        player.getData().setGamesWon(player.getData().getGamesWon() + 1);
                        seasonalPlayer.getData().setGamesWon(seasonalPlayer.getData().getGamesWon() + 1);

                        if (player.getData().getGamesWon() == 100) {
                            player.getData().addBadgeIfAbsent(Badge.GAMER);
                        }

                        if (player.getData().getGamesWon() == 1000) {
                            player.getData().addBadgeIfAbsent(Badge.ADDICTED_GAMER);
                        }

                        if (number > 90) {
                            player.getData().addBadgeIfAbsent(Badge.APPROACHING_DESTINY);
                        }

                        unifiedPlayer.saveUpdating();

                        TextChannelGround.of(e).dropItemWithChance(ItemReference.FLOPPY_DISK, 3);
                        channel.sendMessageFormat(languageContext.get("commands.game.lobby.won_game"),
                                EmoteReference.MEGA, e.getMember().getEffectiveName(), gains
                        ).queue();

                        lobby.startNextGame(true);
                        return Operation.COMPLETED;
                    }

                    if (attempts >= maxAttempts) {
                        channel.sendMessageFormat(languageContext.get("commands.game.number.all_attempts_used"),
                                EmoteReference.ERROR, number
                        ).queue();

                        lobby.startNextGame(true); //This should take care of removing the lobby, actually.
                        return Operation.COMPLETED;
                    }

                    channel.sendMessageFormat(languageContext.get("commands.game.lobby.incorrect_answer") + "\n" +
                            String.format(languageContext.get("commands.game.number.hint"),
                                    (parsedAnswer < number ? languageContext.get("commands.game.number.higher") :
                                            languageContext.get("commands.game.number.lower")
                                    )

                            ), EmoteReference.ERROR, (maxAttempts - attempts)
                    ).queue();
                    attempts = attempts + 1;
                    return Operation.IGNORED;
                }

                return Operation.IGNORED;
            }

            @Override
            public void onExpire() {
                final var channel = lobby.getChannel();
                if (channel == null) {
                    GameLobby.LOBBYS.remove(Long.parseLong(lobby.getChannelId()));
                    return;
                }

                channel.sendMessageFormat(lobby.getLanguageContext().get("commands.game.lobby_timed_out"), EmoteReference.ERROR, number).queue();
                GameLobby.LOBBYS.remove(channel.getIdLong());
            }

            @Override
            public void onCancel() {
                GameLobby.LOBBYS.remove(lobby.getChannel().getIdLong());
            }
        });
    }

    @Override
    public boolean onStart(GameLobby lobby) {
        number = r.nextInt(150);
        lobby.getChannel().sendMessageFormat(lobby.getLanguageContext().get("commands.game.number.start"),
                EmoteReference.THINKING
        ).queue(success -> lobby.setGameLoaded(true));

        return true;
    }

    @Override
    public String name() {
        return "number";
    }
}
