package com.runterya.invsee;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

public class InvSeeMenu extends ChestMenu {
    private final Runnable xpTransferAction;
    private final Runnable openEnderChestAction;
    private final Runnable tpAction;
    private final java.util.function.Consumer<Integer> statusAction;

    private final java.util.function.Consumer<String> commandRunner;
    private final java.util.function.Function<String, String> placeholderReplacer;
    private final Runnable clearInvAction;
    private final Runnable clearEnderAction;
    private final Runnable accessoriesAction;
    private final Runnable onCloseAction;
    private final Runnable healAction;
    private final Runnable feedAction;
    private final Runnable smiteAction;

    public InvSeeMenu(int syncId, Inventory playerInv) {
        this(syncId, playerInv, new SimpleContainer(41), () -> {}, () -> {}, () -> {}, () -> "0 0 0", () -> 0, () -> "minecraft:overworld", () -> null, b -> {}, cmd -> {}, s -> s, () -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {});
    }

    private static class Wrapper implements Container {
        private final Container delegate;
        private final java.util.function.Supplier<String> coordsTextSupplier;
        private final java.util.function.Supplier<Integer> targetXpLevelSupplier;
        private final java.util.function.Supplier<String> dimensionSupplier;
        private final java.util.function.Supplier<java.util.List<Component>> statusLoreSupplier;
        private final java.util.function.Function<String, String> placeholderReplacer;
        private final String clientLang;
        
        public Wrapper(Container delegate, java.util.function.Supplier<String> coordsTextSupplier, java.util.function.Supplier<Integer> targetXpLevelSupplier, java.util.function.Supplier<String> dimensionSupplier, java.util.function.Supplier<java.util.List<Component>> statusLoreSupplier, java.util.function.Function<String, String> placeholderReplacer, String clientLang) { 
            this.delegate = delegate;
            this.coordsTextSupplier = coordsTextSupplier;
            this.targetXpLevelSupplier = targetXpLevelSupplier;
            this.dimensionSupplier = dimensionSupplier;
            this.statusLoreSupplier = statusLoreSupplier;
            this.placeholderReplacer = placeholderReplacer;
            this.clientLang = clientLang;
        }
        
        private int map(int slot) {
            if (slot == 0) return 39; // Helmet
            if (slot == 1) return 38; // Chestplate
            if (slot == 2) return 37; // Leggings
            if (slot == 3) return 36; // Boots
            if (slot == 4) return 40; // Offhand
            if (slot >= 5 && slot <= 8) return -1; // Dummy slots for buttons
            if (slot >= 9 && slot <= 35) return slot; // Main inv is 9-35
            if (slot >= 36 && slot <= 44) return slot - 36; // Hotbar is 0-8
            return -1;
        }

        public int getContainerSize() { return 45; }
        public boolean isEmpty() { return delegate.isEmpty(); }
        public net.minecraft.world.item.ItemStack getItem(int slot) { 
            if (slot >= 5 && slot <= 8) {
                int buttonIndex = slot - 5;
                if (Config.INSTANCE.buttons != null && buttonIndex < Config.INSTANCE.buttons.size()) {
                    Config.ButtonConfig btn = Config.INSTANCE.buttons.get(buttonIndex);
                    if (btn == null || "empty".equals(btn.type)) return ItemStack.EMPTY;
                    
                    if ("status".equals(btn.type) && this.statusLoreSupplier != null) {
                        java.util.List<Component> currentLore = this.statusLoreSupplier.get();
                        if (currentLore != null) {
                            ItemStack apple = new ItemStack(Items.GOLDEN_APPLE);
                            apple.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
                            apple.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(currentLore));
                            return apple;
                        }
                    } else if ("location".equals(btn.type)) {
                        ItemStack paper = new ItemStack(Items.PAPER);
                        paper.set(DataComponents.CUSTOM_NAME, Component.literal("§b" + Lang.getFor(this.clientLang, "location", this.coordsTextSupplier.get())));
                        paper.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(Component.literal("§7" + Lang.getFor(this.clientLang, "dimension", this.dimensionSupplier.get())))));
                        return paper;
                    } else if ("ender_chest".equals(btn.type)) {
                        ItemStack enderChest = new ItemStack(Items.ENDER_CHEST);
                        enderChest.set(DataComponents.CUSTOM_NAME, Component.literal("§6" + Lang.getFor(this.clientLang, "open_ender_chest")));
                        return enderChest;
                    } else if ("xp".equals(btn.type)) {
                        ItemStack xpBottle = new ItemStack(Items.EXPERIENCE_BOTTLE);
                        xpBottle.set(DataComponents.CUSTOM_NAME, Component.literal("§e" + Lang.getFor(this.clientLang, "xp_level", this.targetXpLevelSupplier.get())));
                        return xpBottle;
                    } else if ("custom".equals(btn.type)) {
                        Identifier itemId = Identifier.tryParse(btn.item != null ? btn.item : "minecraft:stone");
                        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId).map(ref -> ref.value()).orElse(Items.STONE);
                        ItemStack customStack = new ItemStack(item);
                        if (btn.name != null && !btn.name.isEmpty()) {
                            String name = this.placeholderReplacer != null ? this.placeholderReplacer.apply(btn.name) : btn.name;
                            java.util.regex.Pattern langPattern = java.util.regex.Pattern.compile("\\{lang:([a-zA-Z0-9_]+)\\}");
                            java.util.regex.Matcher matcher = langPattern.matcher(name);
                            StringBuilder sb = new StringBuilder();
                            while (matcher.find()) {
                                matcher.appendReplacement(sb, Lang.getFor(this.clientLang, matcher.group(1)));
                            }
                            matcher.appendTail(sb);
                            name = sb.toString();
                            customStack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
                        }
                        if (btn.lore != null && !btn.lore.isEmpty()) {
                            java.util.List<Component> customLore = new java.util.ArrayList<>();
                            for (String l : btn.lore) {
                                if (l.contains("{effects}") && this.statusLoreSupplier != null) {
                                    java.util.List<Component> sLore = this.statusLoreSupplier.get();
                                    if (sLore != null) {
                                        boolean foundEffects = false;
                                        for (Component comp : sLore) {
                                            if (foundEffects || comp.getString().contains(Lang.getFor(this.clientLang, "effects"))) {
                                                foundEffects = true;
                                                if (comp.getString().trim().isEmpty() || comp.getString().contains(Lang.getFor(this.clientLang, "last_seen"))) {
                                                    foundEffects = false;
                                                    continue;
                                                }
                                                customLore.add(comp);
                                            }
                                        }
                                    }
                                    continue;
                                }
                                if (l.contains("{lastseen}") && this.statusLoreSupplier != null) {
                                    java.util.List<Component> sLore = this.statusLoreSupplier.get();
                                    if (sLore != null) {
                                        for (Component comp : sLore) {
                                            if (comp.getString().contains(Lang.getFor(this.clientLang, "last_seen"))) {
                                                customLore.add(Component.literal(""));
                                                customLore.add(comp);
                                            }
                                        }
                                    }
                                    continue;
                                }
                                String loreLine = this.placeholderReplacer != null ? this.placeholderReplacer.apply(l) : l;
                                java.util.regex.Pattern langPattern = java.util.regex.Pattern.compile("\\{lang:([a-zA-Z0-9_]+)\\}");
                                java.util.regex.Matcher matcher = langPattern.matcher(loreLine);
                                StringBuilder sb = new StringBuilder();
                                while (matcher.find()) {
                                    matcher.appendReplacement(sb, Lang.getFor(this.clientLang, matcher.group(1)));
                                }
                                matcher.appendTail(sb);
                                loreLine = sb.toString();
                                if (loreLine != null && (!loreLine.isEmpty() || l.isEmpty())) {
                                    customLore.add(Component.literal(loreLine));
                                }
                            }
                            customStack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(customLore));
                        }
                        return customStack;
                    }
                }
                return ItemStack.EMPTY;
            }
            int m = map(slot);
            return m != -1 ? delegate.getItem(m) : net.minecraft.world.item.ItemStack.EMPTY; 
        }
        public net.minecraft.world.item.ItemStack removeItem(int slot, int amount) { 
            int m = map(slot);
            return m != -1 ? delegate.removeItem(m, amount) : net.minecraft.world.item.ItemStack.EMPTY; 
        }
        public net.minecraft.world.item.ItemStack removeItemNoUpdate(int slot) { 
            int m = map(slot);
            return m != -1 ? delegate.removeItemNoUpdate(m) : net.minecraft.world.item.ItemStack.EMPTY; 
        }
        public void setItem(int slot, net.minecraft.world.item.ItemStack stack) { 
            int m = map(slot);
            if (m != -1) delegate.setItem(m, stack); 
        }
        public void setChanged() { delegate.setChanged(); }
        public boolean stillValid(net.minecraft.world.entity.player.Player player) { return delegate.stillValid(player); }
        public void clearContent() { delegate.clearContent(); }
        public void startOpen(net.minecraft.world.entity.ContainerUser user) { delegate.startOpen(user); }
        public void stopOpen(net.minecraft.world.entity.ContainerUser user) { delegate.stopOpen(user); }
    }


    private final String clientLang;

    public InvSeeMenu(int syncId, Inventory playerInv, Container target, Runnable xpTransferAction, Runnable openEnderChestAction, Runnable tpAction, java.util.function.Supplier<String> coordsTextSupplier, java.util.function.Supplier<Integer> targetXpLevelSupplier, java.util.function.Supplier<String> dimensionSupplier, java.util.function.Supplier<java.util.List<Component>> statusLoreSupplier, java.util.function.Consumer<Integer> statusAction, java.util.function.Consumer<String> commandRunner, java.util.function.Function<String, String> placeholderReplacer, Runnable clearInvAction, Runnable clearEnderAction, Runnable accessoriesAction, Runnable onCloseAction, Runnable healAction, Runnable feedAction, Runnable smiteAction) {
        super(MenuType.GENERIC_9x5, syncId, playerInv, new Wrapper(target, coordsTextSupplier, targetXpLevelSupplier, dimensionSupplier, statusLoreSupplier, placeholderReplacer, playerInv.player instanceof net.minecraft.server.level.ServerPlayer sp ? InvSeeMod.getClientLang(sp) : "en_us"), 5);
        this.clientLang = playerInv.player instanceof net.minecraft.server.level.ServerPlayer sp2 ? InvSeeMod.getClientLang(sp2) : "en_us";
        this.xpTransferAction = xpTransferAction;
        this.openEnderChestAction = openEnderChestAction;
        this.tpAction = tpAction;
        this.statusAction = statusAction;
        this.commandRunner = commandRunner;
        this.placeholderReplacer = placeholderReplacer;
        this.clearInvAction = clearInvAction;
        this.clearEnderAction = clearEnderAction;
        this.accessoriesAction = accessoriesAction;
        this.onCloseAction = onCloseAction;
        this.healAction = healAction;
        this.feedAction = feedAction;
        this.smiteAction = smiteAction;

        Container wrapped = this.getContainer();

        for (int j = 5; j <= 8; ++j) {
            Slot dummySlot = this.slots.get(j);
            Slot newDummy = new Slot(wrapped, dummySlot.getContainerSlot(), dummySlot.x, dummySlot.y) {
                @Override
                public boolean mayPickup(Player player) { return false; }
                @Override
                public boolean mayPlace(ItemStack stack) { return false; }
            };
            newDummy.index = dummySlot.index;
            this.slots.set(j, newDummy);
        }
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (slotId >= 0 && slotId < this.slots.size()) {
            Slot slot = this.slots.get(slotId);
            if (slot.container instanceof Wrapper && slot.getContainerSlot() >= 5 && slot.getContainerSlot() <= 8) {
                int buttonIndex = slot.getContainerSlot() - 5;
                if (Config.INSTANCE.buttons != null && buttonIndex < Config.INSTANCE.buttons.size()) {
                    Config.ButtonConfig btn = Config.INSTANCE.buttons.get(buttonIndex);
                    if (btn != null) {
                        boolean isInternalCmd = false;
                        if (btn.command != null && btn.command.startsWith("#")) {
                            isInternalCmd = true;
                            if ("#transfer_xp".equals(btn.command)) { this.xpTransferAction.run(); }
                            else if ("#open_ender".equals(btn.command)) { this.openEnderChestAction.run(); }
                            else if ("#tp".equals(btn.command)) { this.tpAction.run(); }
                            else if ("#clear_inv".equals(btn.command)) { if (this.clearInvAction != null) this.clearInvAction.run(); }
                            else if ("#clear_ender".equals(btn.command)) { if (this.clearEnderAction != null) this.clearEnderAction.run(); }
                            else if ("#accessories".equals(btn.command)) { if (this.accessoriesAction != null) this.accessoriesAction.run(); }
                            else if ("#heal".equals(btn.command)) { if (this.healAction != null) this.healAction.run(); }
                            else if ("#feed".equals(btn.command)) { if (this.feedAction != null) this.feedAction.run(); }
                            else if ("#lightning".equals(btn.command)) { if (this.smiteAction != null) this.smiteAction.run(); }
                            else if ("#status".equals(btn.command)) { if (this.statusAction != null) this.statusAction.accept(button); }
                            // #dummy does nothing
                        }

                        if (!isInternalCmd && btn.command != null && !btn.command.isEmpty() && this.commandRunner != null) {
                            this.commandRunner.accept(btn.command);
                        }
                        
                        if ("xp".equals(btn.type)) {
                            this.xpTransferAction.run();
                        } else if ("ender_chest".equals(btn.type)) {
                            this.openEnderChestAction.run();
                        } else if ("location".equals(btn.type)) {
                            this.tpAction.run();
                        } else if ("status".equals(btn.type)) {
                            if (this.statusAction != null) this.statusAction.accept(button);
                        }
                    }
                }
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.onCloseAction != null) {
            this.onCloseAction.run();
        }
    }
}
