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

public class InvSeeMod implements ModInitializer {
    @Override
    public void onInitialize() {
        Config config = Config.load();
        Lang.setLanguage(config.language);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher, registryAccess);
        });
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(Commands.literal("invsee")
            .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
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
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    ServerPlayer user = source.getPlayerOrException();
                    NameAndId profile = GameProfileArgument.getGameProfiles(context, "target").iterator().next();
                    return openInvSee(source, user, profile, registryAccess);
                })
            )
        );
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

                        user.openMenu(new SimpleMenuProvider((syncId, playerInv, p) -> {
                            return new InvSeeMenu(syncId, playerInv, targetInv, onlineXpAction, onlineOpenEnderChestAction, onlineTpAction, onlineCoordsSupplier, onlineXpLevelSupplier, onlineDimSupplier, onlineStatusLoreSupplier, onlineStatusAction);
                        }, Component.literal(Lang.get("player_inventory", profile.name()))));
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

                        user.openMenu(new SimpleMenuProvider((syncId, playerInv, p) -> {
                            return new InvSeeMenu(syncId, playerInv, offlineInv, offlineXpAction, offlineOpenEnderChestAction, offlineTpAction, () -> finalOfflineCoords, () -> offlineXpLevel, () -> offlineDim, offlineStatusLoreSupplier, null);
                        }, Component.literal(Lang.get("offline_inv", profile.name()))));
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                        source.sendFailure(Component.literal(Lang.get("failed_load_data")));
                    }

                return 1;
    }
}
