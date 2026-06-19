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

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher, registryAccess);
        });
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(Commands.literal("invsee")
            .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
            .then(Commands.argument("target", GameProfileArgument.gameProfile())
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    ServerPlayer user = source.getPlayerOrException();
                    NameAndId profile = GameProfileArgument.getGameProfiles(context, "target").iterator().next();

                    ServerPlayer onlineTarget = source.getServer().getPlayerList().getPlayer(profile.id());

                    if (onlineTarget != null) {
                        if (user == onlineTarget) {
                            source.sendFailure(Component.literal("You cannot invsee yourself!"));
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
                                user.sendSystemMessage(Component.literal("§aStolen " + total + " XP!"));
                            }
                        };

                        Container targetInv = new Container() {
                            public int getContainerSize() { return 41; }
                            public boolean isEmpty() { return onlineTarget.getInventory().isEmpty(); }
                            public ItemStack getItem(int slot) {
                                if (slot < 36) return onlineTarget.getInventory().getItem(slot);
                                if (slot == 36) return onlineTarget.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
                                if (slot == 37) return onlineTarget.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
                                if (slot == 38) return onlineTarget.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
                                if (slot == 39) return onlineTarget.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                                if (slot == 40) return onlineTarget.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND);
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
                                if (slot < 36) onlineTarget.getInventory().setItem(slot, stack);
                                else if (slot == 36) onlineTarget.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, stack);
                                else if (slot == 37) onlineTarget.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, stack);
                                else if (slot == 38) onlineTarget.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, stack);
                                else if (slot == 39) onlineTarget.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, stack);
                                else if (slot == 40) onlineTarget.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, stack);
                            }
                            public void setChanged() { onlineTarget.getInventory().setChanged(); }
                            public boolean stillValid(net.minecraft.world.entity.player.Player p) { return true; }
                            public void clearContent() { onlineTarget.getInventory().clearContent(); }
                        };

                        Runnable onlineOpenEnderChestAction = () -> {
                            user.openMenu(new SimpleMenuProvider((syncId, playerInv, p) -> {
                                return net.minecraft.world.inventory.ChestMenu.threeRows(syncId, playerInv, onlineTarget.getEnderChestInventory());
                            }, Component.literal(profile.name() + "'s Ender Chest")));
                        };

                        String keyStr = onlineTarget.level().dimension().toString();
                        String onlineDim = keyStr.substring(keyStr.lastIndexOf('/') + 1, keyStr.length() - 1).trim();
                        String onlineCoords = String.format(java.util.Locale.US, "%.1f %.1f %.1f", onlineTarget.getX(), onlineTarget.getY(), onlineTarget.getZ());
                        String onlineCmd = "/execute in " + onlineDim + " run tp @s " + onlineCoords;
                        Runnable onlineTpAction = () -> {
                            user.closeContainer();
                            user.sendSystemMessage(Component.literal("§aClick here to teleport!").withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand(onlineCmd))));
                        };

                        int onlineXpLevel = onlineTarget.experienceLevel;
                        
                        java.util.List<Component> onlineStatusLore = new java.util.ArrayList<>();
                        onlineStatusLore.add(Component.literal(String.format(java.util.Locale.US, "§cHealth: %.1f/%.1f", onlineTarget.getHealth(), onlineTarget.getMaxHealth())));
                        onlineStatusLore.add(Component.literal("§6Food: " + onlineTarget.getFoodData().getFoodLevel() + "/20"));
                        java.util.Collection<net.minecraft.world.effect.MobEffectInstance> effects = onlineTarget.getActiveEffects();
                        if (effects.isEmpty()) {
                            onlineStatusLore.add(Component.literal("§7Effects: None"));
                        } else {
                            onlineStatusLore.add(Component.literal("§dEffects:"));
                            for (net.minecraft.world.effect.MobEffectInstance eff : effects) {
                                String effName = eff.getEffect().unwrapKey().map(k -> k.location().getPath()).orElse("unknown");
                                onlineStatusLore.add(Component.literal("§d- " + effName + " " + (eff.getAmplifier() + 1)));
                            }
                        }
                        onlineStatusLore.add(Component.literal(""));
                        onlineStatusLore.add(Component.literal("§eLeft-Click: §aHeal & Feed"));
                        onlineStatusLore.add(Component.literal("§eRight-Click: §aClear Effects"));

                        java.util.function.Consumer<Integer> onlineStatusAction = (button) -> {
                            if (button == 0) { // Left-Click
                                onlineTarget.setHealth(onlineTarget.getMaxHealth());
                                onlineTarget.getFoodData().setFoodLevel(20);
                                onlineTarget.getFoodData().setSaturation(5.0f);
                                user.sendSystemMessage(Component.literal("§aHealed and fed " + onlineTarget.getName().getString() + "!"));
                            } else if (button == 1) { // Right-Click
                                onlineTarget.removeAllEffects();
                                user.sendSystemMessage(Component.literal("§aCleared effects of " + onlineTarget.getName().getString() + "!"));
                            }
                            user.closeContainer();
                        };

                        user.openMenu(new SimpleMenuProvider((syncId, playerInv, p) -> {
                            return new InvSeeMenu(syncId, playerInv, targetInv, onlineXpAction, onlineOpenEnderChestAction, onlineTpAction, onlineCoords, onlineXpLevel, onlineDim, onlineStatusLore, onlineStatusAction);
                        }, Component.literal(profile.name() + "'s Inventory")));
                        return 1;
                    }

                    File playerDataDir = source.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).toFile();
                    File playerFile = new File(playerDataDir, profile.id() + ".dat");
                    if (!playerFile.exists()) {
                        source.sendFailure(Component.literal("Player not found or has no data!"));
                        return 0;
                    }

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
                                    for (int i = 0; i < this.getContainerSize(); i++) {
                                        if (i < 36) {
                                            nowOnline.getInventory().setItem(i, this.getItem(i));
                                        } else if (i == 36) {
                                            nowOnline.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, this.getItem(i));
                                        } else if (i == 37) {
                                            nowOnline.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, this.getItem(i));
                                        } else if (i == 38) {
                                            nowOnline.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, this.getItem(i));
                                        } else if (i == 39) {
                                            nowOnline.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, this.getItem(i));
                                        } else if (i == 40) {
                                            nowOnline.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, this.getItem(i));
                                        }
                                    }
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

                                        // Save back into 'equipment' CompoundTag for 1.21.2+
                                        if (latestNbt.contains("equipment")) {
                                            CompoundTag equipmentTag = latestNbt.getCompoundOrEmpty("equipment");
                                            
                                            // Save armor slots
                                            ItemStack head = this.getItem(39);
                                            equipmentTag.put("head", head.isEmpty() ? new CompoundTag() : ItemStack.CODEC.encodeStart(ops, head).getOrThrow());
                                            
                                            ItemStack chest = this.getItem(38);
                                            equipmentTag.put("chest", chest.isEmpty() ? new CompoundTag() : ItemStack.CODEC.encodeStart(ops, chest).getOrThrow());
                                            
                                            ItemStack legs = this.getItem(37);
                                            equipmentTag.put("legs", legs.isEmpty() ? new CompoundTag() : ItemStack.CODEC.encodeStart(ops, legs).getOrThrow());
                                            
                                            ItemStack feet = this.getItem(36);
                                            equipmentTag.put("feet", feet.isEmpty() ? new CompoundTag() : ItemStack.CODEC.encodeStart(ops, feet).getOrThrow());
                                            
                                            // Save offhand slot
                                            ItemStack offhand = this.getItem(40);
                                            equipmentTag.put("offhand", offhand.isEmpty() ? new CompoundTag() : ItemStack.CODEC.encodeStart(ops, offhand).getOrThrow());
                                            
                                            latestNbt.put("equipment", equipmentTag);
                                        }

                                        java.io.File tempFile = java.io.File.createTempFile(profile.id().toString(), ".dat", playerFile.getParentFile());
                                        NbtIo.writeCompressed(latestNbt, tempFile.toPath());
                                        java.nio.file.Files.move(tempFile.toPath(), playerFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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

                        // Fallback for 1.21.2+ EntityEquipment format
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
                                    user.sendSystemMessage(Component.literal("§aStolen " + total + " XP!"));
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
                                    user.sendSystemMessage(Component.literal("§aStolen " + xpTotal + " XP from offline player."));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        };

                        SimpleContainer offlineEnderChest = new SimpleContainer(27) {
                            @Override
                            public void stopOpen(net.minecraft.world.entity.ContainerUser player) {
                                source.getServer().execute(() -> {
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
                            }, Component.literal(profile.name() + "'s Ender Chest")));
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
                            user.sendSystemMessage(Component.literal("§aClick here to teleport!").withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand(offlineCmd))));
                        };

                        int offlineXpLevel = nbt.getInt("XpLevel").orElse(0);
                        user.openMenu(new SimpleMenuProvider((syncId, playerInv, p) -> {
                            return new InvSeeMenu(syncId, playerInv, offlineInv, offlineXpAction, offlineOpenEnderChestAction, offlineTpAction, finalOfflineCoords, offlineXpLevel, offlineDim, null, null);
                        }, Component.literal(profile.name() + "'s Offline Inv")));
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                        source.sendFailure(Component.literal("Failed to load player data!"));
                    }

                    return 1;
                })
            )
        );
    }
}
