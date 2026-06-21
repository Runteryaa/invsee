package com.runterya.invsee;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.server.players.NameAndId;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InvSeeMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("invsee");
    // Stores client language per player UUID
    private static final java.util.Map<java.util.UUID, String> clientLangs = new java.util.concurrent.ConcurrentHashMap<>();

    public static String getClientLang(ServerPlayer player) {
        return clientLangs.getOrDefault(player.getUUID(), LangHelper.getClientLanguage(player));
    }

    public static void sendWebhook(String url, String message) {
        if (url == null || url.isEmpty()) return;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String json = "{\"content\": \"" + message.replace("\"", "\\\"") + "\"}";
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                conn.getResponseCode();
            } catch (Exception e) {}
        });
    }

    @Override
    public void onInitialize() {
        LOGGER.info("InvSee mod loaded successfully!");
        Config config = Config.load();
        Lang.setLanguage(config.language);

        // Register the lang_sync payload on server-side
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay()
            .register(LangSyncPayload.ID, LangSyncPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay()
            .register(ClientReloadPayload.ID, ClientReloadPayload.CODEC);

        // Receive language preference from client
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
            LangSyncPayload.ID,
            (payload, context) -> {
                String lang = payload.lang();
                if (lang != null && lang.matches("^[a-z]{2}_[a-z]{2}$")) {
                    if (!clientLangs.containsKey(context.player().getUUID())) {
                        LOGGER.info("Player {} joined with InvSee client mod.", context.player().getName().getString());
                    }
                    clientLangs.put(context.player().getUUID(), lang);
                }
            }
        );

        // Clean up when player disconnects
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            clientLangs.remove(handler.player.getUUID());
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher, registryAccess);
        });
    }


    public static boolean checkPermission(CommandSourceStack source, String permission, int defaultRequiredLevel) {
        try {
            Class<?> permsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            for (java.lang.reflect.Method m : permsClass.getMethods()) {
                if (m.getName().equals("check") && m.getParameterCount() == 3 && m.getParameterTypes()[0].isAssignableFrom(source.getClass()) && m.getParameterTypes()[1] == String.class && m.getParameterTypes()[2] == int.class) {
                    return (boolean) m.invoke(null, source, permission, defaultRequiredLevel);
                }
            }
        } catch (Exception e) {}
        return source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    public static boolean checkPermission(net.minecraft.world.entity.player.Player player, String permission, int defaultRequiredLevel) {
        if (player instanceof ServerPlayer sp) {
            try {
                Class<?> permsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
                for (java.lang.reflect.Method m : permsClass.getMethods()) {
                    if (m.getName().equals("check") && m.getParameterCount() == 3 && m.getParameterTypes()[0].isAssignableFrom(sp.getClass()) && m.getParameterTypes()[1] == String.class && m.getParameterTypes()[2] == int.class) {
                        return (boolean) m.invoke(null, sp, permission, defaultRequiredLevel);
                    }
                }
            } catch (Exception e) {}
            return sp.createCommandSourceStack().permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        }
        return false;
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(Commands.literal("invsee")
            .requires(source -> checkPermission(source, "invsee.base", 2))
            .executes(context -> {
                CommandSourceStack source = context.getSource();
                ServerPlayer user = source.getPlayerOrException();
                
                File playerDataDir = source.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).toFile();
                if (!playerDataDir.exists()) {
                    source.sendFailure(Component.literal(Lang.get("no_player_data")));
                    return 0;
                }

                java.util.List<com.mojang.authlib.GameProfile> onlinePlayers = new java.util.ArrayList<>();
                java.util.List<com.mojang.authlib.GameProfile> offlinePlayers = new java.util.ArrayList<>();
                
                for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
                    if (p != user) {
                        onlinePlayers.add(p.getGameProfile());
                    }
                }
                
                File[] files = playerDataDir.listFiles((dir, name) -> name.endsWith(".dat"));
                if (files != null) {
                    for (File file : files) {
                        try {
                            String uuidStr = file.getName().substring(0, file.getName().length() - 4);
                            java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
                            if (uuid.equals(user.getUUID())) continue;
                            
                            boolean isOnline = onlinePlayers.stream().anyMatch(p -> p.id().equals(uuid));
                            if (!isOnline) {
                                com.mojang.authlib.GameProfile prof = source.getServer().services().profileResolver().fetchById(uuid).orElseGet(() -> new com.mojang.authlib.GameProfile(uuid, uuidStr.substring(0, 16)));
                                offlinePlayers.add(prof);
                            }
                        } catch (Exception e) {}
                    }
                }

                if (onlinePlayers.isEmpty() && offlinePlayers.isEmpty()) {
                    source.sendFailure(Component.literal(Lang.get("no_other_players")));
                    return 0;
                }

                user.openMenu(new SimpleMenuProvider((syncId, playerInv, p) -> {
                    return new PlayerListMenu(syncId, playerInv, onlinePlayers, offlinePlayers, 0, (selectedProfile) -> {
                        openInvSee(source, user, new NameAndId(selectedProfile), registryAccess);
                    });
                }, Component.literal(Lang.get("player_list"))));

                return 1;
            })
            .then(Commands.argument("target", GameProfileArgument.gameProfile())
                .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    ServerPlayer user = source.getPlayerOrException();
                    NameAndId profile = GameProfileArgument.getGameProfiles(context, "target").iterator().next();
                    return openInvSee(source, user, profile, registryAccess);
                })
            )
            .then(Commands.literal("action")
                .requires(source -> checkPermission(source, "invsee.action", 2))
                .then(Commands.argument("action_id", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"#clear_inv", "#clear_ender", "#heal", "#feed", "#lightning", "#open_ender", "#transfer_xp", "#tp", "#accessories"}, builder))
                    .then(Commands.argument("target", GameProfileArgument.gameProfile())
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            String actionId = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "action_id");
                            NameAndId profile = GameProfileArgument.getGameProfiles(context, "target").iterator().next();
                            return executeActionCommand(source, profile, actionId, null);
                        })
                        .then(Commands.argument("receiver", net.minecraft.commands.arguments.EntityArgument.player())
                            .executes(context -> {
                                CommandSourceStack source = context.getSource();
                                String actionId = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "action_id");
                                NameAndId profile = GameProfileArgument.getGameProfiles(context, "target").iterator().next();
                                ServerPlayer receiver = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "receiver");
                                return executeActionCommand(source, profile, actionId, receiver);
                            })
                        )
                    )
                )
            )
            .then(Commands.literal("search")
                .requires(source -> checkPermission(source, "invsee.search", 2))
                .then(Commands.argument("item", net.minecraft.commands.arguments.item.ItemArgument.item(registryAccess))
                    .executes(context -> {
                        CommandSourceStack source = context.getSource();
                        net.minecraft.commands.arguments.item.ItemInput itemInput = net.minecraft.commands.arguments.item.ItemArgument.getItem(context, "item");
                        net.minecraft.world.item.Item targetItem = itemInput.item().value();
                        
                        source.sendSystemMessage(Component.literal("§eSearching for players with " + targetItem.getDescriptionId() + "..."));
                        
                        java.util.concurrent.CompletableFuture.runAsync(() -> {
                            java.util.List<String> foundPlayers = new java.util.ArrayList<>();
                            
                            for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
                                boolean found = false;
                                for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                                    if (p.getInventory().getItem(i).is(targetItem)) found = true;
                                }
                                for (int i = 0; i < p.getEnderChestInventory().getContainerSize(); i++) {
                                    if (p.getEnderChestInventory().getItem(i).is(targetItem)) found = true;
                                }
                                if (found) foundPlayers.add(p.getScoreboardName());
                            }
                            
                            File playerDataDir = source.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).toFile();
                            File[] files = playerDataDir.listFiles((dir, name) -> name.endsWith(".dat"));
                            if (files != null) {
                                net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, source.getServer().registryAccess());
                                for (File file : files) {
                                    try {
                                        String uuidStr = file.getName().substring(0, file.getName().length() - 4);
                                        java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
                                        if (source.getServer().getPlayerList().getPlayer(uuid) != null) continue;
                                        
                                        CompoundTag nbt = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
                                        boolean found = false;
                                        
                                        ListTag inv = nbt.getListOrEmpty("Inventory");
                                        for (int i=0; i<inv.size(); i++) {
                                            ItemStack stack = ItemStack.CODEC.parse(ops, inv.getCompoundOrEmpty(i)).result().orElse(ItemStack.EMPTY);
                                            if (stack.is(targetItem)) found = true;
                                        }
                                        ListTag ender = nbt.getListOrEmpty("EnderItems");
                                        for (int i=0; i<ender.size(); i++) {
                                            ItemStack stack = ItemStack.CODEC.parse(ops, ender.getCompoundOrEmpty(i)).result().orElse(ItemStack.EMPTY);
                                            if (stack.is(targetItem)) found = true;
                                        }
                                        
                                        if (found) {
                                            com.mojang.authlib.GameProfile prof = source.getServer().services().profileResolver().fetchById(uuid).orElse(null);
                                            if (prof != null) foundPlayers.add(prof.name());
                                            else foundPlayers.add(uuidStr);
                                        }
                                    } catch (Exception e) {}
                                }
                            }
                            
                            source.getServer().execute(() -> {
                                if (foundPlayers.isEmpty()) {
                                    source.sendSystemMessage(Component.literal("§cNo players found with this item."));
                                } else {
                                    source.sendSystemMessage(Component.literal("§aFound in: §f" + String.join(", ", foundPlayers)));
                                }
                            });
                        });
                        return 1;
                    })
                )
            )
            .then(Commands.literal("reload")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    ServerPlayer player = source.getPlayer();
                    if (source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) {
                        Config.load();
                        Lang.setLanguage(Config.INSTANCE.language);
                        source.sendSystemMessage(Component.literal("§aServer configuration reloaded!"));
                        if (player != null && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, ClientReloadPayload.ID)) {
                            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new ClientReloadPayload());
                        }
                    } else if (player != null && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, ClientReloadPayload.ID)) {
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new ClientReloadPayload());
                    }
                    return 1;
                })
                .then(Commands.literal("server")
                    .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                    .executes(context -> {
                        Config.load();
                        Lang.setLanguage(Config.INSTANCE.language);
                        context.getSource().sendSystemMessage(Component.literal("§aServer configuration reloaded!"));
                        return 1;
                    })
                )
                .then(Commands.literal("client")
                    .requires(source -> {
                        ServerPlayer player = source.getPlayer();
                        return player != null && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, ClientReloadPayload.ID);
                    })
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayer();
                        if (player != null) {
                            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new ClientReloadPayload());
                        }
                        return 1;
                    })
                )
            )
        );
    }

    private static String getPlaytimeStr(int ticks) {
        int totalSeconds = ticks / 20;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }

    private int openInvSee(CommandSourceStack source, ServerPlayer user, NameAndId profile, CommandBuildContext registryAccess) {

                    ServerPlayer onlineTarget = source.getServer().getPlayerList().getPlayer(profile.id());

                    if (onlineTarget != null) {
                        if (user == onlineTarget) {
                            source.sendFailure(Component.literal(Lang.get("cannot_self")));
                            return 0;
                        }
                        Runnable onlineXpAction = () -> {
                            if (onlineTarget.totalExperience > 0 || onlineTarget.experienceLevel > 0 || onlineTarget.experienceProgress > 0) {
                                int total = onlineTarget.totalExperience;
                                user.giveExperiencePoints(total);
                                onlineTarget.experienceLevel = 0;
                                onlineTarget.experienceProgress = 0.0f;
                                onlineTarget.totalExperience = 0;
                                onlineTarget.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(0.0f, 0, 0));
                                user.sendSystemMessage(Component.literal(Lang.get("stolen_xp", total)));
                            }
                        };

                        Container targetInv = new Container() {
                            private ServerPlayer getTarget() {
                                ServerPlayer p = source.getServer().getPlayerList().getPlayer(profile.id());
                                return p != null ? p : onlineTarget;
                            }
                            public int getContainerSize() { return 41; }
                            public boolean isEmpty() { return getTarget().getInventory().isEmpty(); }
                            public ItemStack getItem(int slot) {
                                ServerPlayer t = getTarget();
                                if (slot < 36) return t.getInventory().getItem(slot);
                                if (slot == 36) return t.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
                                if (slot == 37) return t.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
                                if (slot == 38) return t.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
                                if (slot == 39) return t.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                                if (slot == 40) return t.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND);
                                return ItemStack.EMPTY;
                            }
                            public ItemStack removeItem(int slot, int amount) {
                                ItemStack stack = getItem(slot);
                                if (stack.isEmpty()) return ItemStack.EMPTY;
                                ItemStack split = stack.split(amount);
                                setItem(slot, stack);
                                return split;
                            }
                            public ItemStack removeItemNoUpdate(int slot) {
                                ItemStack stack = getItem(slot);
                                setItem(slot, ItemStack.EMPTY);
                                return stack;
                            }
                            public void setItem(int slot, ItemStack stack) {
                                ServerPlayer t = getTarget();
                                if (slot < 36) t.getInventory().setItem(slot, stack);
                                else if (slot == 36) t.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, stack);
                                else if (slot == 37) t.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, stack);
                                else if (slot == 38) t.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, stack);
                                else if (slot == 39) t.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, stack);
                                else if (slot == 40) t.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, stack);
                            }
                            public void setChanged() { getTarget().getInventory().setChanged(); }
                            public boolean stillValid(net.minecraft.world.entity.player.Player p) { return true; }
                            public void clearContent() { getTarget().getInventory().clearContent(); }
                        };

                        Runnable onlineOpenEnderChestAction = () -> {
                            ServerPlayer p = source.getServer().getPlayerList().getPlayer(profile.id());
                            ServerPlayer t = p != null ? p : onlineTarget;
                            user.openMenu(new SimpleMenuProvider((syncId, playerInv, pl) -> {
                                return net.minecraft.world.inventory.ChestMenu.threeRows(syncId, playerInv, t.getEnderChestInventory());
                            }, Component.literal(Lang.get("ender_chest", profile.name()))));
                        };

                        java.util.function.Supplier<String> onlineDimSupplier = () -> {
                            ServerPlayer p = source.getServer().getPlayerList().getPlayer(profile.id());
                            ServerPlayer t = p != null ? p : onlineTarget;
                            String keyStr = t.level().dimension().toString();
                            return keyStr.substring(keyStr.lastIndexOf('/') + 1, keyStr.length() - 1).trim();
                        };
                        java.util.function.Supplier<String> onlineCoordsSupplier = () -> {
                            ServerPlayer p = source.getServer().getPlayerList().getPlayer(profile.id());
                            ServerPlayer t = p != null ? p : onlineTarget;
                            return String.format(java.util.Locale.US, "%.1f %.1f %.1f", t.getX(), t.getY(), t.getZ());
                        };
                        
                        Runnable onlineTpAction = () -> {
                            user.closeContainer();
                            String onlineCmd = "/execute in " + onlineDimSupplier.get() + " run tp @s " + onlineCoordsSupplier.get();
                            user.sendSystemMessage(Component.literal("§a" + Lang.get("click_teleport")).withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand(onlineCmd))));
                        };

                        java.util.function.Supplier<Integer> onlineXpLevelSupplier = () -> {
                            ServerPlayer p = source.getServer().getPlayerList().getPlayer(profile.id());
                            return p != null ? p.experienceLevel : onlineTarget.experienceLevel;
                        };
                        
                        java.util.function.Supplier<java.util.List<Component>> onlineStatusLoreSupplier = () -> {
                            ServerPlayer p = source.getServer().getPlayerList().getPlayer(profile.id());
                            ServerPlayer t = p != null ? p : onlineTarget;
                            java.util.List<Component> lore = new java.util.ArrayList<>();
                            lore.add(Component.literal(String.format(java.util.Locale.US, "§c%s: %.1f/%.1f", Lang.get("health"), t.getHealth(), t.getMaxHealth())));
                            lore.add(Component.literal("§6" + Lang.get("food") + ": " + t.getFoodData().getFoodLevel() + "/20"));
                            java.util.Collection<net.minecraft.world.effect.MobEffectInstance> effects = t.getActiveEffects();
                            if (effects.isEmpty()) {
                                lore.add(Component.literal("§7" + Lang.get("effects") + ": " + Lang.get("effects_none")));
                            } else {
                                lore.add(Component.literal("§d" + Lang.get("effects") + ":"));
                                for (net.minecraft.world.effect.MobEffectInstance eff : effects) {
                                    String effName = eff.getEffect().unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");
                                    String durationStr = "";
                                    if (eff.isInfiniteDuration()) {
                                        durationStr = " (XX:XX)";
                                    } else {
                                        int secs = eff.getDuration() / 20;
                                        durationStr = String.format(" (%02d:%02d)", secs / 60, secs % 60);
                                    }
                                    lore.add(Component.literal("§d- " + effName + " " + (eff.getAmplifier() + 1) + durationStr));
                                }
                            }
                            return lore;
                        };

                        java.util.function.Consumer<Integer> onlineStatusAction = (button) -> {};
                        java.util.function.Consumer<String> commandRunner = cmd -> source.getServer().getCommands().performPrefixedCommand(source, cmd);
                        java.util.function.Function<String, String> placeholderReplacer = s -> {
                            ServerPlayer t = source.getServer().getPlayerList().getPlayer(profile.id());
                            if (t == null) t = onlineTarget;
                            String res = s.replace("{player}", profile.name());
                            res = res.replace("{health}", String.format(java.util.Locale.US, "%.1f", t.getHealth()));
                            res = res.replace("{maxhealth}", String.format(java.util.Locale.US, "%.1f", t.getMaxHealth()));
                            res = res.replace("{food}", String.valueOf(t.getFoodData().getFoodLevel()));
                            res = res.replace("{xplevel}", String.valueOf(t.experienceLevel));
                            res = res.replace("{x}", String.format(java.util.Locale.US, "%.1f", t.getX()));
                            res = res.replace("{y}", String.format(java.util.Locale.US, "%.1f", t.getY()));
                            res = res.replace("{z}", String.format(java.util.Locale.US, "%.1f", t.getZ()));
                            String dimKey = t.level().dimension().toString();
                            dimKey = dimKey.substring(dimKey.lastIndexOf('/') + 1, dimKey.length() - 1).trim();
                            res = res.replace("{dimension}", dimKey);
                            res = res.replace("{playtime}", getPlaytimeStr(t.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME))));
                            res = res.replace("{deaths}", String.valueOf(t.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS))));
                            res = res.replace("{mob_kills}", String.valueOf(t.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.MOB_KILLS))));
                            return res;
                        };
                        String clientLang = InvSeeMod.getClientLang(user);

                        Runnable onlineClearInvAction = () -> {
                            ServerPlayer p = source.getServer().getPlayerList().getPlayer(profile.id());
                            ServerPlayer t = p != null ? p : onlineTarget;
                            t.getInventory().clearContent();
                            user.sendSystemMessage(Component.literal("§aInventory cleared!"));
                        };
                        Runnable onlineClearEnderAction = () -> {
                            ServerPlayer p = source.getServer().getPlayerList().getPlayer(profile.id());
                            ServerPlayer t = p != null ? p : onlineTarget;
                            t.getEnderChestInventory().clearContent();
                            user.sendSystemMessage(Component.literal("§aEnder Chest cleared!"));
                        };
                        
                        Runnable onlineAccessoriesAction = () -> {
                            user.sendSystemMessage(Component.literal("§eAccessories are not fully supported yet!"));
                        };
                        
                        Runnable onlineOnCloseAction = () -> {
                            sendWebhook(Config.INSTANCE.discord_webhook_url, user.getName().getString() + " closed " + profile.name() + "'s inventory.");
                        };

                        Runnable onlineHealAction = () -> {
                            ServerPlayer p = source.getServer().getPlayerList().getPlayer(profile.id());
                            ServerPlayer t = p != null ? p : onlineTarget;
                            t.setHealth(t.getMaxHealth());
                            user.sendSystemMessage(Component.literal("§aPlayer fully healed!"));
                        };
                        Runnable onlineFeedAction = () -> {
                            ServerPlayer p = source.getServer().getPlayerList().getPlayer(profile.id());
                            ServerPlayer t = p != null ? p : onlineTarget;
                            t.getFoodData().setFoodLevel(20);
                            user.sendSystemMessage(Component.literal("§aPlayer fully fed!"));
                        };
                        Runnable onlineSmiteAction = () -> {
                            ServerPlayer p = source.getServer().getPlayerList().getPlayer(profile.id());
                            ServerPlayer t = p != null ? p : onlineTarget;
                            net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(t.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                            if (bolt != null) {
                                bolt.setPos(t.getX(), t.getY(), t.getZ());
                                t.level().addFreshEntity(bolt);
                            }
                            user.sendSystemMessage(Component.literal("§ePlayer smited!"));
                        };

                        user.openMenu(new SimpleMenuProvider((syncId, playerInv, p) -> {
                            return new InvSeeMenu(syncId, playerInv, targetInv, onlineXpAction, onlineOpenEnderChestAction, onlineTpAction, onlineCoordsSupplier, onlineXpLevelSupplier, onlineDimSupplier, onlineStatusLoreSupplier, onlineStatusAction, commandRunner, placeholderReplacer, onlineClearInvAction, onlineClearEnderAction, onlineAccessoriesAction, onlineOnCloseAction, onlineHealAction, onlineFeedAction, onlineSmiteAction);
                        }, Component.literal(Lang.getFor(clientLang, "player_inventory", profile.name()))));
                        
                        sendWebhook(Config.INSTANCE.discord_webhook_url, user.getName().getString() + " opened " + profile.name() + "'s inventory.");
                        return 1;
                    }

                    File playerDataDir = source.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).toFile();
                    File playerFile = new File(playerDataDir, profile.id() + ".dat");
                    if (!playerFile.exists()) {
                        source.sendFailure(Component.literal(Lang.get("player_not_found")));
                        return 0;
                    }
                    final long originalLastModified = playerFile.lastModified();

                    try {
                        CompoundTag nbt = NbtIo.readCompressed(playerFile.toPath(), NbtAccounter.unlimitedHeap());

                        ListTag inventoryTag = nbt.getListOrEmpty("Inventory");
                        net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, source.getServer().registryAccess());
                        
                        SimpleContainer offlineInv = new SimpleContainer(41) {
                            @Override
                            public void stopOpen(net.minecraft.world.entity.ContainerUser p) {
                                super.stopOpen(p);
                                source.getServer().execute(() -> {
                                    ServerPlayer nowOnline = source.getServer().getPlayerList().getPlayer(profile.id());
                                    if (nowOnline != null) {
                                        user.sendSystemMessage(Component.literal("§c" + Lang.get("save_aborted_player_online")));
                                    } else {
                                        try {
                                            CompoundTag latestNbt = NbtIo.readCompressed(playerFile.toPath(), NbtAccounter.unlimitedHeap());
                                            ListTag newInvTag = new ListTag();
                                            for (int i = 0; i < this.getContainerSize(); i++) {
                                                ItemStack stack = this.getItem(i);
                                                if (!stack.isEmpty()) {
                                                    net.minecraft.nbt.Tag savedTag = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                                                    if (savedTag instanceof CompoundTag) {
                                                        CompoundTag itemTag = (CompoundTag) savedTag;
                                                        int slot = i;
                                                        if (i >= 36 && i < 40) slot = i - 36 + 100;
                                                        else if (i == 40) slot = 150;
                                                        itemTag.putByte("Slot", (byte) slot);
                                                        newInvTag.add(itemTag);
                                                    }
                                                }
                                            }
                                            latestNbt.put("Inventory", newInvTag);

                                            if (latestNbt.contains("equipment")) {
                                                CompoundTag equipmentTag = latestNbt.getCompoundOrEmpty("equipment");
                                                
                                                ItemStack head = this.getItem(39);
                                                equipmentTag.put("head", head.isEmpty() ? new CompoundTag() : ItemStack.CODEC.encodeStart(ops, head).getOrThrow());
                                                
                                                ItemStack chest = this.getItem(38);
                                                equipmentTag.put("chest", chest.isEmpty() ? new CompoundTag() : ItemStack.CODEC.encodeStart(ops, chest).getOrThrow());
                                                
                                                ItemStack legs = this.getItem(37);
                                                equipmentTag.put("legs", legs.isEmpty() ? new CompoundTag() : ItemStack.CODEC.encodeStart(ops, legs).getOrThrow());
                                                
                                                ItemStack feet = this.getItem(36);
                                                equipmentTag.put("feet", feet.isEmpty() ? new CompoundTag() : ItemStack.CODEC.encodeStart(ops, feet).getOrThrow());
                                                
                                                ItemStack offhand = this.getItem(40);
                                                equipmentTag.put("offhand", offhand.isEmpty() ? new CompoundTag() : ItemStack.CODEC.encodeStart(ops, offhand).getOrThrow());
                                                
                                                latestNbt.put("equipment", equipmentTag);
                                            }

                                            java.io.File tempFile = java.io.File.createTempFile(profile.id().toString(), ".dat", playerFile.getParentFile());
                                            NbtIo.writeCompressed(latestNbt, tempFile.toPath());
                                            java.nio.file.Files.move(tempFile.toPath(), playerFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                            playerFile.setLastModified(originalLastModified);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                            }
                        };

                        for (int i = 0; i < inventoryTag.size(); ++i) {
                            CompoundTag itemTag = inventoryTag.getCompoundOrEmpty(i);
                            int slot = itemTag.getByteOr("Slot", (byte) 0) & 255;
                            ItemStack stack = ItemStack.CODEC.parse(ops, itemTag).result().orElse(ItemStack.EMPTY);
                            if (slot >= 0 && slot < 36) {
                                offlineInv.setItem(slot, stack);
                            } else if (slot >= 100 && slot < 104) {
                                offlineInv.setItem(slot - 100 + 36, stack);
                            } else if (slot == 150) {
                                offlineInv.setItem(40, stack);
                            }
                        }

                        if (nbt.contains("equipment")) {
                            CompoundTag equipmentTag = nbt.getCompoundOrEmpty("equipment");
                            
                            if (equipmentTag.contains("head")) {
                                offlineInv.setItem(39, ItemStack.CODEC.parse(ops, equipmentTag.getCompoundOrEmpty("head")).result().orElse(ItemStack.EMPTY));
                            }
                            if (equipmentTag.contains("chest")) {
                                offlineInv.setItem(38, ItemStack.CODEC.parse(ops, equipmentTag.getCompoundOrEmpty("chest")).result().orElse(ItemStack.EMPTY));
                            }
                            if (equipmentTag.contains("legs")) {
                                offlineInv.setItem(37, ItemStack.CODEC.parse(ops, equipmentTag.getCompoundOrEmpty("legs")).result().orElse(ItemStack.EMPTY));
                            }
                            if (equipmentTag.contains("feet")) {
                                offlineInv.setItem(36, ItemStack.CODEC.parse(ops, equipmentTag.getCompoundOrEmpty("feet")).result().orElse(ItemStack.EMPTY));
                            }
                            if (equipmentTag.contains("offhand")) {
                                offlineInv.setItem(40, ItemStack.CODEC.parse(ops, equipmentTag.getCompoundOrEmpty("offhand")).result().orElse(ItemStack.EMPTY));
                            }
                        }

                        Runnable offlineXpAction = () -> {
                            ServerPlayer nowOnline = source.getServer().getPlayerList().getPlayer(profile.id());
                            if (nowOnline != null) {
                                if (nowOnline.totalExperience > 0 || nowOnline.experienceLevel > 0 || nowOnline.experienceProgress > 0) {
                                    int total = nowOnline.totalExperience;
                                    user.giveExperiencePoints(total);
                                    nowOnline.experienceLevel = 0;
                                    nowOnline.experienceProgress = 0.0f;
                                    nowOnline.totalExperience = 0;
                                    nowOnline.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(0.0f, 0, 0));
                                    user.sendSystemMessage(Component.literal(Lang.get("stolen_xp", total)));
                                }
                                return;
                            }
                            try {
                                CompoundTag latestNbt = NbtIo.readCompressed(playerFile.toPath(), NbtAccounter.unlimitedHeap());
                                int xpTotal = latestNbt.getIntOr("XpTotal", 0);
                                int xpLevel = latestNbt.getIntOr("XpLevel", 0);
                                float xpP = latestNbt.getFloatOr("XpP", 0f);

                                if (xpTotal > 0 || xpLevel > 0 || xpP > 0) {
                                    user.giveExperiencePoints(xpTotal); 
                                    latestNbt.putInt("XpTotal", 0);
                                    latestNbt.putInt("XpLevel", 0);
                                    latestNbt.putFloat("XpP", 0f);
                                    
                                    File tempFile = java.io.File.createTempFile(playerFile.getName(), ".dat", playerFile.getParentFile());
                                    NbtIo.writeCompressed(latestNbt, tempFile.toPath());
                                    java.nio.file.Files.move(tempFile.toPath(), playerFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                    playerFile.setLastModified(originalLastModified);
                                    user.sendSystemMessage(Component.literal(Lang.get("stolen_xp", xpTotal)));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        };

                        SimpleContainer offlineEnderChest = new SimpleContainer(27) {
                            @Override
                            public void stopOpen(net.minecraft.world.entity.ContainerUser player) {
                                source.getServer().execute(() -> {
                                    ServerPlayer nowOnline = source.getServer().getPlayerList().getPlayer(profile.id());
                                    if (nowOnline != null) {
                                        user.sendSystemMessage(Component.literal("§c" + Lang.get("save_aborted_player_online")));
                                        return;
                                    }
                                    try {
                                        CompoundTag latestNbt = NbtIo.readCompressed(playerFile.toPath(), NbtAccounter.unlimitedHeap());
                                        ListTag newEnderTag = new ListTag();
                                        for (int i = 0; i < this.getContainerSize(); i++) {
                                            ItemStack stack = this.getItem(i);
                                            if (!stack.isEmpty()) {
                                                net.minecraft.nbt.Tag savedTag = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                                                if (savedTag instanceof CompoundTag) {
                                                    CompoundTag itemTag = (CompoundTag) savedTag;
                                                    itemTag.putByte("Slot", (byte) i);
                                                    newEnderTag.add(itemTag);
                                                }
                                            }
                                        }
                                        latestNbt.put("EnderItems", newEnderTag);
                                        java.io.File tempFile = java.io.File.createTempFile(profile.id().toString() + "_ec", ".dat", playerFile.getParentFile());
                                        NbtIo.writeCompressed(latestNbt, tempFile.toPath());
                                        java.nio.file.Files.move(tempFile.toPath(), playerFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                        playerFile.setLastModified(originalLastModified);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                });
                            }
                        };

                        if (nbt.contains("EnderItems")) {
                            ListTag enderItems = nbt.getListOrEmpty("EnderItems");
                            for (int i = 0; i < enderItems.size(); ++i) {
                                CompoundTag itemTag = enderItems.getCompoundOrEmpty(i);
                                int slot = itemTag.getByteOr("Slot", (byte) 0) & 255;
                                ItemStack stack = ItemStack.CODEC.parse(ops, itemTag).result().orElse(ItemStack.EMPTY);
                                if (slot >= 0 && slot < 27) {
                                    offlineEnderChest.setItem(slot, stack);
                                }
                            }
                        }

                        Runnable offlineOpenEnderChestAction = () -> {
                            user.openMenu(new SimpleMenuProvider((syncId, playerInv, p) -> {
                                return net.minecraft.world.inventory.ChestMenu.threeRows(syncId, playerInv, offlineEnderChest);
                            }, Component.literal(Lang.get("ender_chest", profile.name()))));
                        };

                        ListTag posTag = nbt.getListOrEmpty("Pos");
                        String offlineCoords = "0 0 0";
                        if (posTag.size() == 3) {
                            offlineCoords = String.format(java.util.Locale.US, "%.1f %.1f %.1f", posTag.getDouble(0).orElse(0.0), posTag.getDouble(1).orElse(0.0), posTag.getDouble(2).orElse(0.0));
                        }
                        String finalOfflineCoords = offlineCoords;
                        String offlineDim = nbt.getString("Dimension").orElse("minecraft:overworld");
                        String offlineCmd = "/execute in " + offlineDim + " run tp @s " + finalOfflineCoords;
                        
                        Runnable offlineTpAction = () -> {
                            user.closeContainer();
                            user.sendSystemMessage(Component.literal("§a" + Lang.get("click_teleport")).withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand(offlineCmd))));
                        };

                        int offlineXpLevel = nbt.getInt("XpLevel").orElse(0);
                        
                        java.util.function.Supplier<java.util.List<Component>> offlineStatusLoreSupplier = () -> {
                            java.util.List<Component> lore = new java.util.ArrayList<>();
                            float health = nbt.getFloat("Health").orElse(20.0f);
                            lore.add(Component.literal(String.format(java.util.Locale.US, "§c%s: %.1f", Lang.get("health"), health)));
                            int food = nbt.getInt("foodLevel").orElse(20);
                            lore.add(Component.literal("§6" + Lang.get("food") + ": " + food + "/20"));
                            
                            ListTag effectsTag = nbt.getListOrEmpty("ActiveEffects");
                            if (effectsTag.isEmpty()) {
                                lore.add(Component.literal("§7" + Lang.get("effects") + ": " + Lang.get("effects_none")));
                            } else {
                                lore.add(Component.literal("§d" + Lang.get("effects") + ":"));
                                for (int i = 0; i < effectsTag.size(); i++) {
                                    CompoundTag eff = effectsTag.getCompoundOrEmpty(i);
                                    String effId = eff.getString("id").orElse("unknown").replace("minecraft:", "");
                                    int amp = eff.getByte("amplifier").orElse((byte) 0) + 1;
                                    int dur = eff.getInt("duration").orElse(0);
                                    int secs = dur / 20;
                                    String durStr = String.format(" (%02d:%02d)", secs / 60, secs % 60);
                                    lore.add(Component.literal("§d- " + effId + " " + amp + durStr));
                                }
                            }
                            
                            long lastPlayed = playerFile.lastModified();
                            if (lastPlayed > 0) {
                                long diff = System.currentTimeMillis() - lastPlayed;
                                long minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diff);
                                long hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(diff);
                                long days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff);
                                
                                String timeStr;
                                if (days > 0) timeStr = Lang.get("days_ago", days);
                                else if (hours > 0) timeStr = Lang.get("hours_ago", hours);
                                else if (minutes > 0) timeStr = Lang.get("minutes_ago", minutes);
                                else timeStr = Lang.get("just_now");
                                
                                lore.add(Component.literal(""));
                                lore.add(Component.literal("§8" + Lang.get("last_seen") + ": " + timeStr));
                            }
                            
                            return lore;
                        };

                        String clientLang2 = InvSeeMod.getClientLang(user);
                        java.util.function.Consumer<String> offlineCommandRunner = cmd -> source.getServer().getCommands().performPrefixedCommand(source, cmd);
                        java.util.function.Function<String, String> offlinePlaceholderReplacer = s -> {
                            String res = s.replace("{player}", profile.name());
                            res = res.replace("{health}", String.format(java.util.Locale.US, "%.1f", nbt.getFloat("Health").orElse(20.0f)));
                            res = res.replace("{maxhealth}", "20.0");
                            res = res.replace("{food}", String.valueOf(nbt.getInt("foodLevel").orElse(20)));
                            res = res.replace("{xplevel}", String.valueOf(nbt.getInt("XpLevel").orElse(0)));
                            ListTag posTag2 = nbt.getListOrEmpty("Pos");
                            if (posTag2.size() == 3) {
                                res = res.replace("{x}", String.format(java.util.Locale.US, "%.1f", posTag2.getDouble(0).orElse(0.0)));
                                res = res.replace("{y}", String.format(java.util.Locale.US, "%.1f", posTag2.getDouble(1).orElse(0.0)));
                                res = res.replace("{z}", String.format(java.util.Locale.US, "%.1f", posTag2.getDouble(2).orElse(0.0)));
                            } else {
                                res = res.replace("{x}", "0.0").replace("{y}", "0.0").replace("{z}", "0.0");
                            }
                            String offlineDimStr = nbt.getString("Dimension").orElse("minecraft:overworld");
                            res = res.replace("{dimension}", offlineDimStr);
                            
                            File statsFile = new File(source.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_STATS_DIR).toFile(), profile.id() + ".json");
                            int playtime = 0, deaths = 0, kills = 0;
                            if (statsFile.exists()) {
                                try (java.io.FileReader reader = new java.io.FileReader(statsFile)) {
                                    com.google.gson.JsonObject statsObj = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                                    if (statsObj.has("stats") && statsObj.getAsJsonObject("stats").has("minecraft:custom")) {
                                        com.google.gson.JsonObject customStats = statsObj.getAsJsonObject("stats").getAsJsonObject("minecraft:custom");
                                        if (customStats.has("minecraft:play_time")) playtime = customStats.get("minecraft:play_time").getAsInt();
                                        if (customStats.has("minecraft:deaths")) deaths = customStats.get("minecraft:deaths").getAsInt();
                                        if (customStats.has("minecraft:mob_kills")) kills = customStats.get("minecraft:mob_kills").getAsInt();
                                    }
                                } catch (Exception e) {}
                            }
                            res = res.replace("{playtime}", getPlaytimeStr(playtime));
                            res = res.replace("{deaths}", String.valueOf(deaths));
                            res = res.replace("{mob_kills}", String.valueOf(kills));
                            
                            return res;
                        };

                        Runnable offlineClearInvAction = () -> {
                            offlineInv.clearContent();
                            user.sendSystemMessage(Component.literal("§aInventory cleared! Changes will be saved when you close the menu."));
                        };
                        Runnable offlineClearEnderAction = () -> {
                            offlineEnderChest.clearContent();
                            user.sendSystemMessage(Component.literal("§aEnder Chest cleared! Changes will be saved when you close the menu."));
                        };
                        
                        Runnable offlineAccessoriesAction = () -> {
                            user.sendSystemMessage(Component.literal("§eAccessories are not fully supported yet!"));
                        };
                        
                        Runnable offlineOnCloseAction = () -> {
                            sendWebhook(Config.INSTANCE.discord_webhook_url, user.getName().getString() + " closed " + profile.name() + "'s offline inventory.");
                        };

                        Runnable offlineHealAction = () -> {
                            nbt.putFloat("Health", 20.0f);
                            user.sendSystemMessage(Component.literal("§aOffline player fully healed! Changes will be saved when you close the menu."));
                        };
                        Runnable offlineFeedAction = () -> {
                            nbt.putInt("foodLevel", 20);
                            user.sendSystemMessage(Component.literal("§aOffline player fully fed! Changes will be saved when you close the menu."));
                        };
                        Runnable offlineSmiteAction = () -> {
                            user.sendSystemMessage(Component.literal("§cCannot smite an offline player!"));
                        };

                        user.openMenu(new SimpleMenuProvider((syncId, playerInv, p) -> {
                            return new InvSeeMenu(syncId, playerInv, offlineInv, offlineXpAction, offlineOpenEnderChestAction, offlineTpAction, () -> finalOfflineCoords, () -> offlineXpLevel, () -> offlineDim, offlineStatusLoreSupplier, null, offlineCommandRunner, offlinePlaceholderReplacer, offlineClearInvAction, offlineClearEnderAction, offlineAccessoriesAction, offlineOnCloseAction, offlineHealAction, offlineFeedAction, offlineSmiteAction);
                        }, Component.literal(Lang.getFor(clientLang2, "offline_inv", profile.name()))));
                        
                        sendWebhook(Config.INSTANCE.discord_webhook_url, user.getName().getString() + " opened " + profile.name() + "'s offline inventory.");
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                        source.sendFailure(Component.literal(Lang.get("failed_load_data")));
                    }

                return 1;
    }

    private static int executeActionCommand(CommandSourceStack source, NameAndId profile, String actionId, ServerPlayer receiverPlayer) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = source.getServer().getPlayerList().getPlayer(profile.id());
        if (p != null) {
            if (actionId.equals("#heal")) { p.setHealth(p.getMaxHealth()); source.sendSystemMessage(Component.literal("§aPlayer fully healed!")); }
            else if (actionId.equals("#feed")) { p.getFoodData().setFoodLevel(20); source.sendSystemMessage(Component.literal("§aPlayer fully fed!")); }
            else if (actionId.equals("#clear_inv")) { p.getInventory().clearContent(); source.sendSystemMessage(Component.literal("§aInventory cleared!")); }
            else if (actionId.equals("#clear_ender")) { p.getEnderChestInventory().clearContent(); source.sendSystemMessage(Component.literal("§aEnder Chest cleared!")); }
            else if (actionId.equals("#open_ender")) {
                ServerPlayer user = source.getPlayerOrException();
                user.openMenu(new net.minecraft.world.SimpleMenuProvider((syncId, playerInv, pl) -> {
                    return net.minecraft.world.inventory.ChestMenu.threeRows(syncId, playerInv, p.getEnderChestInventory());
                }, Component.literal(Lang.get("ender_chest", profile.name()))));
                return 1;
            }
            else if (actionId.equals("#lightning")) {
                net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(p.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                if (bolt != null) {
                    bolt.setPos(p.getX(), p.getY(), p.getZ());
                    p.level().addFreshEntity(bolt);
                }
                source.sendSystemMessage(Component.literal("§ePlayer struck by lightning!"));
            }
            else if (actionId.equals("#tp")) {
                String dimKey = p.level().dimension().toString();
                dimKey = dimKey.substring(dimKey.lastIndexOf('/') + 1, dimKey.length() - 1).trim();
                String onlineCmd = "/execute in " + dimKey + " run tp @s " + String.format(java.util.Locale.US, "%.1f %.1f %.1f", p.getX(), p.getY(), p.getZ());
                ServerPlayer user = source.getPlayerOrException();
                user.sendSystemMessage(Component.literal("§a" + Lang.get("click_teleport")).withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand(onlineCmd))));
                return 1;
            }
            else if (actionId.equals("#transfer_xp")) {
                ServerPlayer receiver = receiverPlayer != null ? receiverPlayer : source.getPlayerOrException();
                if (p == receiver) {
                    source.sendFailure(Component.literal("§cCannot transfer XP to the same player!"));
                    return 0;
                }
                int total = p.totalExperience;
                if (total > 0) {
                    receiver.giveExperiencePoints(total);
                    p.experienceLevel = 0;
                    p.experienceProgress = 0.0f;
                    p.totalExperience = 0;
                    p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(0.0f, 0, 0));
                    source.sendSystemMessage(Component.literal("§aTransferred " + total + " XP to " + receiver.getName().getString() + "!"));
                }
                return 1;
            }
            else if (actionId.equals("#accessories")) {
                source.sendSystemMessage(Component.literal("§eAccessories are not fully supported yet!"));
                return 1;
            } else {
                source.sendFailure(Component.literal("Unknown action!"));
                return 0;
            }
            sendWebhook(Config.INSTANCE.discord_webhook_url, source.getTextName() + " executed " + actionId + " on " + profile.name() + ".");
            return 1;
        }

        File playerFile = new File(source.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).toFile(), profile.id() + ".dat");
        if (playerFile.exists()) {
            try {
                net.minecraft.nbt.CompoundTag nbt = net.minecraft.nbt.NbtIo.readCompressed(playerFile.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                boolean changed = false;
                if (actionId.equals("#heal")) { nbt.putFloat("Health", 20.0f); changed = true; }
                else if (actionId.equals("#feed")) { nbt.putInt("foodLevel", 20); changed = true; }
                else if (actionId.equals("#clear_inv")) { nbt.put("Inventory", new net.minecraft.nbt.ListTag()); changed = true; }
                else if (actionId.equals("#clear_ender")) { nbt.put("EnderItems", new net.minecraft.nbt.ListTag()); changed = true; }
                else if (actionId.equals("#open_ender")) {
                    ServerPlayer user = source.getPlayerOrException();
                    net.minecraft.world.SimpleContainer offlineEnderChest = new net.minecraft.world.SimpleContainer(27) {
                        @Override
                        public void stopOpen(net.minecraft.world.entity.ContainerUser player) {
                            source.getServer().execute(() -> {
                                if (source.getServer().getPlayerList().getPlayer(profile.id()) != null) {
                                    if (player instanceof ServerPlayer sp) {
                                        sp.sendSystemMessage(Component.literal("§cSave aborted: player is now online!"));
                                    }
                                    return;
                                }
                                try {
                                    net.minecraft.nbt.CompoundTag savedNbt = net.minecraft.nbt.NbtIo.readCompressed(playerFile.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                                    net.minecraft.nbt.ListTag newEnderTag = new net.minecraft.nbt.ListTag();
                                    com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> ops = source.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
                                    for (int i = 0; i < this.getContainerSize(); i++) {
                                        net.minecraft.world.item.ItemStack stack = this.getItem(i);
                                        if (!stack.isEmpty()) {
                                            net.minecraft.nbt.Tag savedTag = net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                                            if (savedTag instanceof net.minecraft.nbt.CompoundTag) {
                                                net.minecraft.nbt.CompoundTag itemTag = (net.minecraft.nbt.CompoundTag) savedTag;
                                                itemTag.putByte("Slot", (byte) i);
                                                newEnderTag.add(itemTag);
                                            }
                                        }
                                    }
                                    savedNbt.put("EnderItems", newEnderTag);
                                    File temp = File.createTempFile(profile.id().toString() + "-ec-", ".dat", playerFile.getParentFile());
                                    net.minecraft.nbt.NbtIo.writeCompressed(savedNbt, temp.toPath());
                                    File backup = new File(playerFile.getParentFile(), playerFile.getName() + "_old");
                                    if (backup.exists()) backup.delete();
                                    playerFile.renameTo(backup);
                                    java.nio.file.Files.move(temp.toPath(), playerFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    };
                    
                    if (nbt.contains("EnderItems")) {
                        net.minecraft.nbt.ListTag enderItems = nbt.getListOrEmpty("EnderItems");
                        com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> ops = source.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
                        for (int i = 0; i < enderItems.size(); ++i) {
                            net.minecraft.nbt.CompoundTag itemTag = enderItems.getCompoundOrEmpty(i);
                            int slot = itemTag.getByteOr("Slot", (byte) 0) & 255;
                            net.minecraft.world.item.ItemStack stack = net.minecraft.world.item.ItemStack.CODEC.parse(ops, itemTag).result().orElse(net.minecraft.world.item.ItemStack.EMPTY);
                            if (slot >= 0 && slot < 27) {
                                offlineEnderChest.setItem(slot, stack);
                            }
                        }
                    }
                    
                    user.openMenu(new net.minecraft.world.SimpleMenuProvider((syncId, playerInv, pl) -> {
                        return net.minecraft.world.inventory.ChestMenu.threeRows(syncId, playerInv, offlineEnderChest);
                    }, Component.literal(Lang.getFor(InvSeeMod.getClientLang(user), "ender_chest", profile.name()))));
                    return 1;
                }
                else if (actionId.equals("#lightning")) { source.sendSystemMessage(Component.literal("§cCannot strike an offline player with lightning!")); return 0; }
                else if (actionId.equals("#tp")) {
                    ServerPlayer user = source.getPlayerOrException();
                    net.minecraft.nbt.ListTag posTag = nbt.getListOrEmpty("Pos");
                    String offlineCoords = "0 0 0";
                    if (posTag.size() == 3) {
                        offlineCoords = String.format(java.util.Locale.US, "%.1f %.1f %.1f", posTag.getDouble(0).orElse(0.0), posTag.getDouble(1).orElse(0.0), posTag.getDouble(2).orElse(0.0));
                    }
                    String offlineDimStr = nbt.getString("Dimension").orElse("minecraft:overworld");
                    String offlineCmd = "/execute in " + offlineDimStr + " run tp @s " + offlineCoords;
                    user.sendSystemMessage(Component.literal("§a" + Lang.get("click_teleport")).withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand(offlineCmd))));
                    return 1;
                }
                else if (actionId.equals("#transfer_xp")) {
                    int xpTotal = nbt.getInt("XpTotal").orElse(0);
                    if (xpTotal > 0) {
                        ServerPlayer receiver = receiverPlayer != null ? receiverPlayer : source.getPlayerOrException();
                        receiver.giveExperiencePoints(xpTotal);
                        nbt.putInt("XpTotal", 0);
                        nbt.putInt("XpLevel", 0);
                        nbt.putFloat("XpP", 0f);
                        changed = true;
                        source.sendSystemMessage(Component.literal("§aTransferred " + xpTotal + " XP to " + receiver.getName().getString() + "!"));
                    }
                }
                else if (actionId.equals("#accessories")) {
                    source.sendSystemMessage(Component.literal("§eAccessories are not fully supported yet!"));
                    return 1;
                }
                else { source.sendFailure(Component.literal("Unknown action!")); return 0; }

                if (changed) {
                    File temp = File.createTempFile(profile.id().toString() + "-", ".dat", playerFile.getParentFile());
                    net.minecraft.nbt.NbtIo.writeCompressed(nbt, temp.toPath());
                    File backup = new File(playerFile.getParentFile(), playerFile.getName() + "_old");
                    if (backup.exists()) backup.delete();
                    playerFile.renameTo(backup);
                    java.nio.file.Files.move(temp.toPath(), playerFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    source.sendSystemMessage(Component.literal("§aAction " + actionId + " executed on offline player " + profile.name() + " and saved!"));
                    sendWebhook(Config.INSTANCE.discord_webhook_url, source.getTextName() + " executed " + actionId + " on " + profile.name() + " (offline).");
                }
            } catch (Exception e) {
                e.printStackTrace();
                source.sendFailure(Component.literal(Lang.get("failed_load_data")));
                return 0;
            }
            return 1;
        }
        
        source.sendFailure(Component.literal(Lang.get("player_never_joined")));
        return 0;
    }
}
