/*
 * Copyright (C) 2016-2018 David Alejandro Rubio Escares / Kodehawa
 *
 * Mantaro is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Mantaro is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Mantaro.  If not, see http://www.gnu.org/licenses/
 */

package net.kodehawa.mantarobot.commands;

import com.google.common.eventbus.Subscribe;
import com.jagrosh.jdautilities.commons.utils.FinderUtil;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.kodehawa.mantarobot.MantaroBot;
import net.kodehawa.mantarobot.commands.currency.item.ItemStack;
import net.kodehawa.mantarobot.commands.currency.item.Items;
import net.kodehawa.mantarobot.commands.currency.pets.Pet;
import net.kodehawa.mantarobot.commands.currency.pets.PetData;
import net.kodehawa.mantarobot.commands.currency.pets.PetStats;
import net.kodehawa.mantarobot.core.CommandRegistry;
import net.kodehawa.mantarobot.core.listeners.operations.InteractiveOperations;
import net.kodehawa.mantarobot.core.listeners.operations.core.Operation;
import net.kodehawa.mantarobot.core.modules.Module;
import net.kodehawa.mantarobot.core.modules.commands.SimpleCommand;
import net.kodehawa.mantarobot.core.modules.commands.SubCommand;
import net.kodehawa.mantarobot.core.modules.commands.TreeCommand;
import net.kodehawa.mantarobot.core.modules.commands.base.Category;
import net.kodehawa.mantarobot.core.modules.commands.base.Command;
import net.kodehawa.mantarobot.core.modules.commands.i18n.I18nContext;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.db.ManagedDatabase;
import net.kodehawa.mantarobot.db.entities.DBGuild;
import net.kodehawa.mantarobot.db.entities.Player;
import net.kodehawa.mantarobot.db.entities.helpers.PlayerData;
import net.kodehawa.mantarobot.utils.DiscordUtils;
import net.kodehawa.mantarobot.utils.StringUtils;
import net.kodehawa.mantarobot.utils.Utils;
import net.kodehawa.mantarobot.utils.commands.EmoteReference;
import net.kodehawa.mantarobot.utils.commands.IncreasingRateLimiter;
import net.kodehawa.mantarobot.utils.commands.RateLimit;

import java.awt.*;
import java.lang.annotation.ElementType;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Module
public class PetCmds {
    private final static String BLOCK_INACTIVE = "\u25A1";
    private final static String BLOCK_ACTIVE = "\u25A0";
    private static final int TOTAL_BLOCKS = 5;


    @Subscribe
    public void petAction(CommandRegistry cr) {
        IncreasingRateLimiter rateLimiter = new IncreasingRateLimiter.Builder()
                .cooldown(20, TimeUnit.SECONDS)
                .limit(1)
                .maxCooldown(5, TimeUnit.MINUTES)
                .premiumAware(false)
                .prefix("petaction")
                .pool(MantaroData.getDefaultJedisPool())
                .build();

        IncreasingRateLimiter petRateLimiter = new IncreasingRateLimiter.Builder()
                .cooldown(30, TimeUnit.MINUTES)
                .maxCooldown(1, TimeUnit.HOURS)
                .limit(1)
                .premiumAware(false)
                .prefix("petpet") //owo
                .pool(MantaroData.getDefaultJedisPool())
                .build();

        TreeCommand petActionCommand = (TreeCommand) cr.register("pet", new TreeCommand(Category.PETS) {
            @Override
            public Command defaultTrigger(GuildMessageReceivedEvent event, String mainCommand, String commandName) {
                return new SubCommand() {
                    @Override
                    protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content) {
                        String[] args = StringUtils.advancedSplitArgs(content, 2);
                        ManagedDatabase db = MantaroData.db();
                        List<Member> found = FinderUtil.findMembers(content, event.getGuild());
                        String userId;
                        String petName;

                        if(content.isEmpty()) {
                            event.getChannel().sendMessageFormat(languageContext.get("commands.pet.no_content"), EmoteReference.ERROR).queue();
                            return;
                        }

                        //We only want one result, don't we?
                        if(found.size() > 1 && args.length > 1) {
                            event.getChannel().sendMessageFormat(languageContext.get("general.too_many_members"), EmoteReference.THINKING, found.stream().limit(7).map(m -> String.format("%s#%s", m.getUser().getName(), m.getUser().getDiscriminator())).collect(Collectors.joining(", "))).queue();
                            return;
                        }

                        if(found.isEmpty() || args.length == 1) {
                            userId = event.getAuthor().getId();
                            petName = content;
                        } else {
                            userId = found.get(0).getUser().getId();
                            petName = args[1];
                        }


                        if(petName.isEmpty()) {
                            event.getChannel().sendMessageFormat(languageContext.get("commands.pet.not_specified"), EmoteReference.ERROR).queue();
                            return;
                        }

                        Player player = db.getPlayer(userId);
                        PlayerData playerData = player.getData();

                        Pet pet = playerData.getProfilePets().get(petName);
                        if(pet == null) {
                            event.getChannel().sendMessageFormat(languageContext.get("commands.pet.not_found"), EmoteReference.ERROR).queue();
                            return;
                        }

                        final PetStats stats = pet.getStats();
                        DateTimeFormatter formatter =
                                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                                        .withLocale(Locale.UK)
                                        .withZone(ZoneId.systemDefault());

                        //This is a placeholder to test stuff. Mostly how it'll look on release though.
                        event.getChannel().sendMessage(
                                new EmbedBuilder()
                                        .setAuthor("Pet Overview and Statistics", null, event.getAuthor().getEffectiveAvatarUrl())
                                        //change to pet image when i actually have it
                                        .setThumbnail(event.getAuthor().getEffectiveAvatarUrl())
                                        .setDescription(
                                                Utils.prettyDisplay("Name", pet.getName()) + "\n" +
                                                        Utils.prettyDisplay("Tier", String.valueOf(pet.calculateTier())) + "\n" +
                                                        // ------ Change to translatable when I have the translation tables ready for this
                                                        Utils.prettyDisplay("Element", pet.getElement().getReadable()) + "\n" +
                                                        Utils.prettyDisplay("Owner", MantaroBot.getInstance().getUserById(pet.getOwner()).getAsTag())  + "\n" +
                                                        Utils.prettyDisplay("Created At", formatter.format(Instant.ofEpochMilli(pet.getEpochCreatedAt())))
                                        )
                                        .addField("Affection", getProgressBar(stats.getAffection(), 50) + String.format(" (%s/%s)", stats.getAffection(), 50), true)
                                        .addField("Current HP", getProgressBar(stats.getCurrentHP(), stats.getHp()) + String.format(" (%s/%s)", stats.getCurrentHP(), stats.getHp()), true)
                                        .addField("Current Stamina", getProgressBar(stats.getCurrentStamina(), stats.getStamina()) + String.format(" (%s/%s)", stats.getCurrentStamina(), stats.getStamina()), true)
                                        .addField("Fly", String.valueOf(pet.getStats().isFly()), true)
                                        .addField("Venom", String.valueOf(pet.getStats().isVenom()), true)
                                        .addField("Inventory", ItemStack.toString(pet.getPetInventory().asList()), false)
                                        .setFooter("Pet ID: " + pet.getData().getId(), null)
                                        .setColor(Color.PINK)
                                        .build()
                        ).queue();
                    }
                };
            }
        });

        petActionCommand.setPredicate(event -> Utils.handleDefaultIncreasingRatelimit(rateLimiter, event.getAuthor(), event, null));

        //Incubate new pet.
        petActionCommand.addSubCommand("incubate", new SubCommand() {
            SecureRandom random = new SecureRandom();

            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content) {
                ManagedDatabase managedDatabase = MantaroData.db();
                Player player = managedDatabase.getPlayer(event.getAuthor());
                PlayerData playerData = player.getData();

                String name;

                if(content.isEmpty()) {
                    event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.incubate.no_name"), EmoteReference.ERROR).queue();
                    return;
                }

                if(!content.trim().matches("^[A-Za-z]+$")) {
                    event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.incubate.only_letters"), EmoteReference.ERROR).queue();
                    return;
                }

                name = content.trim();

                if(!player.getInventory().containsItem(Items.INCUBATOR_EGG)) {
                    event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.incubate.no_egg"), EmoteReference.ERROR).queue();
                    return;
                }

                if(playerData.getPetSlots() < playerData.getProfilePets().size() + 1) {
                    event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.incubate.not_enough_slots"), EmoteReference.ERROR, playerData.getPetSlots(), playerData.getProfilePets().size()).queue();
                    return;
                }

                if(playerData.getProfilePets().containsKey(name)) {
                    event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.incubate.already_exists"), EmoteReference.ERROR).queue();
                    return;
                }

                long moneyNeeded = Math.max(70, random.nextInt(1000));
                if(player.getMoney() < moneyNeeded) {
                    event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.incubate.no_money"), EmoteReference.ERROR, moneyNeeded).queue();
                    return;
                }

                Pet pet = generatePet(event.getAuthor().getId(), name);
                playerData.getProfilePets().put(name, pet);

                player.getInventory().process(new ItemStack(Items.INCUBATOR_EGG, -1));
                player.save();

                event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.incubate.success"), EmoteReference.POPPER, name, pet.getData().getId()).queue();
            }
        });

        petActionCommand.addSubCommand("rename", new SubCommand() {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content) {
                ManagedDatabase managedDatabase = MantaroData.db();
                Player player = managedDatabase.getPlayer(event.getAuthor());
                DBGuild guildData = managedDatabase.getGuild(event.getGuild());
                PlayerData playerData = player.getData();

                Map<String, Pet> playerPets = playerData.getProfilePets();
                String[] args = StringUtils.advancedSplitArgs(content, -1);
                if (args.length < 2) {
                    event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.rename.not_enough_args"), EmoteReference.ERROR).queue();
                    return;
                }

                String originalName = args[0];
                String rename = args[1];

                if (!playerPets.containsKey(originalName)) {
                    event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.rename.no_pet"), EmoteReference.ERROR).queue();
                    return;
                }

                if (playerPets.containsKey(rename)) {
                    event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.rename.new_name_exists"), EmoteReference.ERROR).queue();
                    return;
                }

                if(player.getMoney() < 500) {
                    event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.rename.not_enough_money"), EmoteReference.ERROR).queue();
                    return;
                }

                InteractiveOperations.create(event.getChannel(), event.getAuthor().getIdLong(), 30, ie -> {
                    //Ignore all messages from anyone that isn't the user we already proposed to. Waiting for confirmation...
                    if(!ie.getAuthor().getId().equals(event.getAuthor().getId()))
                        return Operation.IGNORED;

                    //Replace prefix because people seem to think you have to add the prefix before saying yes.
                    String message = ie.getMessage().getContentRaw();
                    for(String s : MantaroData.config().get().prefix) {
                        if(message.toLowerCase().startsWith(s)) {
                            message = message.substring(s.length());
                        }
                    }

                    String guildCustomPrefix = guildData.getData().getGuildCustomPrefix();
                    if(guildCustomPrefix != null && !guildCustomPrefix.isEmpty() && message.toLowerCase().startsWith(guildCustomPrefix)) {
                        message = message.substring(guildCustomPrefix.length());
                    }

                    if(message.equalsIgnoreCase("yes")) {
                        Player player2 = managedDatabase.getPlayer(event.getAuthor());
                        PlayerData playerData2 = player.getData();
                        Map<String, Pet> playerPetsConfirmed = playerData2.getProfilePets();
                        if (!playerPetsConfirmed.containsKey(originalName)) {
                            event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.rename.no_pet"), EmoteReference.ERROR).queue();
                            return Operation.COMPLETED;
                        }

                        if (playerPetsConfirmed.containsKey(rename)) {
                            event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.rename.new_name_exists"), EmoteReference.ERROR).queue();
                            return Operation.COMPLETED;
                        }

                        if(player2.getMoney() < 500) {
                            event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.rename.not_enough_money"), EmoteReference.ERROR).queue();
                            return Operation.COMPLETED;
                        }

                        Pet renamedPet = playerData2.getProfilePets().remove(originalName);
                        playerData2.getProfilePets().put(rename, renamedPet);
                        player2.removeMoney(500);

                        player2.save();
                        new MessageBuilder().setContent(String.format(languageContext.get("commands.petactions.rename.success"), EmoteReference.ERROR, originalName, rename, renamedPet.getData().getId()))
                                .stripMentions(event.getJDA())
                                .sendTo(event.getChannel())
                                .queue();

                        return Operation.COMPLETED;
                    }

                    return Operation.IGNORED;
                });
            }
        });

        //List your pets.
        petActionCommand.addSubCommand("ls", new SubCommand() {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content) {
                ManagedDatabase managedDatabase = MantaroData.db();
                Player player = managedDatabase.getPlayer(event.getAuthor());
                PlayerData playerData = player.getData();

                Map<String, Pet> playerPets = playerData.getProfilePets();
                EmbedBuilder builder = new EmbedBuilder()
                        .setAuthor("Pet List", null, event.getAuthor().getEffectiveAvatarUrl())
                        .setThumbnail(event.getAuthor().getEffectiveAvatarUrl())
                        .setColor(Color.DARK_GRAY)
                        .setFooter("Pet slots: " + playerData.getPetSlots() + ", Used: " + playerData.getProfilePets().size(), null);

                List<MessageEmbed.Field> fields = new LinkedList<>();

                playerPets.forEach((key, pet) -> fields.add(new MessageEmbed.Field(pet.getName(),
                        Utils.prettyDisplay("Tier", String.valueOf(pet.getTier())) + "\n" +
                                Utils.prettyDisplay("XP", String.format("%s (Level %s)", pet.getData().getXp(), pet.getData().getLevel()) + "\n" +
                                Utils.prettyDisplay("Element", pet.getElement().getReadable()) + "\n" +
                                Utils.prettyDisplay("Age", pet.getAgeDays() + " days") + "\n"
                        ),
                        true)
                ));

                List<List<MessageEmbed.Field>> splitFields = DiscordUtils.divideFields(8, fields);
                if(splitFields.isEmpty()) {
                    event.getChannel().sendMessageFormat("%1$sYou have no pets.", EmoteReference.BLUE_SMALL_MARKER).queue();
                    return;
                }

                boolean hasReactionPerms = event.getGuild().getSelfMember().hasPermission(event.getChannel(), Permission.MESSAGE_ADD_REACTION);

                builder.setDescription("**Total pages: " + splitFields.size() + "**\nUse the message reaction to move between pages.\n\n" +
                        EmoteReference.TALKING + " This is a list of the pets you currently own.\n" +
                                "Pets are your companion on the usage of currency! **You can train them, pet them, feed them or have fights between your own pets or pets" +
                                " from other people!** To create a pet you need an incubator, which you can cast from milk, old beverage and diamonds, this will allow you to " +
                                "incubate a pet, which will require you to give it a name."
                        );

                if(hasReactionPerms) {
                    DiscordUtils.list(event, 120, false, builder, splitFields);
                } else {
                    DiscordUtils.listText(event, 120, false, builder, splitFields);
                }
            }
        }).createSubCommandAlias("ls", "list");

        Random rand = new SecureRandom();

        //Yes. Exactly. Can increase affection. Don't overdo it though!
        petActionCommand.addSubCommand("pet", new SubCommand() {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content) {
                ManagedDatabase managedDatabase = MantaroData.db();
                Player player = managedDatabase.getPlayer(event.getAuthor());
                PlayerData playerData = player.getData();

                String petName = content.trim();
                Map<String, Pet> playerPets = playerData.getProfilePets();

                if(!playerPets.containsKey(petName)) {
                    event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.pet.no_pet"), EmoteReference.ERROR).queue();
                    return;
                }

                Pet pet = playerPets.get(petName);
                PetData data = pet.getData();
                //Too many pats @ _ @
                if(!Utils.handleDefaultIncreasingRatelimit(petRateLimiter, data.getId(), event, languageContext, false)) {
                    return;
                }

                data.setTimesPetted(data.getTimesPetted() + 1);
                long affectionIncrease = data.getTimesPetted() / Math.max(10, rand.nextInt(50));
                data.setAffection(data.getAffection() + affectionIncrease);

                event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.pet.pet"), EmoteReference.HEART, data.getAffection(), data.getTimesPetted()).queue();
                player.save();
            }
        });

        petActionCommand.addSubCommand("train", new SubCommand() {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content) {
                ManagedDatabase managedDatabase = MantaroData.db();
                Player player = managedDatabase.getPlayer(event.getAuthor());
                PlayerData playerData = player.getData();

                String petName = content.trim();
                Map<String, Pet> playerPets = playerData.getProfilePets();

                if(!playerPets.containsKey(petName)) {
                    event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.pet.no_pet"), EmoteReference.ERROR).queue();
                    return;
                }

                Pet pet = playerPets.get(petName);
                PetData data = pet.getData();

                //Train experience?
            }
        });

        //Apply effect to pet.
        petActionCommand.addSubCommand("effect", new SubCommand() {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content) {
                ManagedDatabase managedDatabase = MantaroData.db();
                Player player = managedDatabase.getPlayer(event.getAuthor());
                PlayerData playerData = player.getData();

                String petName = content.trim();
                Map<String, Pet> playerPets = playerData.getProfilePets();

                if(!playerPets.containsKey(petName)) {
                    event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.pet.no_pet"), EmoteReference.ERROR).queue();
                    return;
                }

                Pet pet = playerPets.get(petName);
                PetData data = pet.getData();

                //PotionEffect will do?
            }
        });

        //Upgrade stats: requires materials and luck.
        petActionCommand.addSubCommand("upgrade", new SubCommand() {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content) {
                ManagedDatabase managedDatabase = MantaroData.db();
                Player player = managedDatabase.getPlayer(event.getAuthor());
                PlayerData playerData = player.getData();

                String petName = content.trim();
                Map<String, Pet> playerPets = playerData.getProfilePets();

                if(!playerPets.containsKey(petName)) {
                    event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.pet.no_pet"), EmoteReference.ERROR).queue();
                    return;
                }

                Pet pet = playerPets.get(petName);
                PetData data = pet.getData();

                //How?
            }
        });

        //Not aplicable to fire-type pets.
        petActionCommand.addSubCommand("feed", new SubCommand() {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content) {
                ManagedDatabase managedDatabase = MantaroData.db();
                Player player = managedDatabase.getPlayer(event.getAuthor());
                PlayerData playerData = player.getData();

                String petName = content.trim();
                Map<String, Pet> playerPets = playerData.getProfilePets();

                if(!playerPets.containsKey(petName)) {
                    event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.pet.no_pet"), EmoteReference.ERROR).queue();
                    return;
                }

                Pet pet = playerPets.get(petName);
                PetData data = pet.getData();

                //todo: add pet food store (petshop)
            }
        });

        //Only for water-type pets.
        petActionCommand.addSubCommand("hydrate", new SubCommand() {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content) {
                ManagedDatabase managedDatabase = MantaroData.db();
                Player player = managedDatabase.getPlayer(event.getAuthor());
                PlayerData playerData = player.getData();

                String petName = content.trim();
                Map<String, Pet> playerPets = playerData.getProfilePets();

                if(!playerPets.containsKey(petName)) {
                    event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.pet.no_pet"), EmoteReference.ERROR).queue();
                    return;
                }

                Pet pet = playerPets.get(petName);
                PetData data = pet.getData();

                if(pet.getElement() != Pet.Type.WATER) {
                    event.getChannel().sendMessageFormat(languageContext.get("commands.petactions.hydrate.not_water"), EmoteReference.ERROR).queue();
                    return;
                }

                //todo: add water bottle (petshop)
            }
        });

        //todo: shop (prolly just gonna use the item repo for this, no need to create a separate item handling logic)
        petActionCommand.addSubCommand("shop", new SubCommand() {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content) {

            }
        });
    }

    @Subscribe
    public void battle(CommandRegistry cr) {
        cr.register("battle", new SimpleCommand(Category.PETS) {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content, String[] args) {

            }
        });
    }

    private static String getProgressBar(long now, long total) {
        int activeBlocks = (int) ((float) now / total * TOTAL_BLOCKS);
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < TOTAL_BLOCKS; i++)
            builder.append(activeBlocks == i ? BLOCK_ACTIVE : BLOCK_INACTIVE);

        return builder.append(BLOCK_INACTIVE).toString();
    }

    private Pet generatePet(String owner, String name) {
        SecureRandom random = new SecureRandom();

        //Get random element.
        Pet pet = Pet.create(owner, name, Pet.Type.values()[random.nextInt(Pet.Type.values().length)]);
        PetStats petStats = pet.getStats();

        petStats.setHp(Math.max(20, random.nextInt(150)));
        petStats.setStamina(Math.max(20, random.nextInt(140)));
        petStats.setAffection(Math.max(15, random.nextInt(100)));

        //Can't have both a venom-type and fly-type pet: would be broken
        petStats.setVenom(random.nextBoolean());
        petStats.setFly(!petStats.isVenom() && random.nextBoolean());

        pet.getData().setId(UUID.randomUUID().toString());
        return pet;
    }
}
