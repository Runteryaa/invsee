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

    public InvSeeMenu(int syncId, Inventory playerInv) {
        this(syncId, playerInv, new SimpleContainer(41), () -> {}, () -> {}, () -> {}, () -> "0 0 0", () -> 0, () -> "minecraft:overworld", () -> null, b -> {});
    }

    private static class Wrapper implements Container {
        private final Container delegate;
        private final java.util.function.Supplier<String> coordsTextSupplier;
        private final java.util.function.Supplier<Integer> targetXpLevelSupplier;
        private final java.util.function.Supplier<String> dimensionSupplier;
        private final java.util.function.Supplier<java.util.List<Component>> statusLoreSupplier;
        public Wrapper(Container delegate, java.util.function.Supplier<String> coordsTextSupplier, java.util.function.Supplier<Integer> targetXpLevelSupplier, java.util.function.Supplier<String> dimensionSupplier, java.util.function.Supplier<java.util.List<Component>> statusLoreSupplier) { 
            this.delegate = delegate;
            this.coordsTextSupplier = coordsTextSupplier;
            this.targetXpLevelSupplier = targetXpLevelSupplier;
            this.dimensionSupplier = dimensionSupplier;
            this.statusLoreSupplier = statusLoreSupplier;
        }
        
        private int map(int slot) {
            if (slot == 0) return 39; // Helmet
            if (slot == 1) return 38; // Chestplate
            if (slot == 2) return 37; // Leggings
            if (slot == 3) return 36; // Boots
            if (slot == 4) return 40; // Offhand
            if (slot == 5 || slot == 6) return -1; // Dummy
            if (slot == 7 || slot == 8) return -1; // Special
            if (slot >= 9 && slot <= 35) return slot; // Main inv is 9-35
            if (slot >= 36 && slot <= 44) return slot - 36; // Hotbar is 0-8
            return -1;
        }

        public int getContainerSize() { return 45; }
        public boolean isEmpty() { return delegate.isEmpty(); }
        public net.minecraft.world.item.ItemStack getItem(int slot) { 
            if (slot == 5 && this.statusLoreSupplier != null) {
                java.util.List<Component> currentLore = this.statusLoreSupplier.get();
                if (currentLore != null) {
                    ItemStack apple = new ItemStack(Items.GOLDEN_APPLE);
                    apple.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(currentLore));
                    return apple;
                }
            }
            if (slot == 6) {
                ItemStack paper = new ItemStack(Items.PAPER);
                paper.set(DataComponents.CUSTOM_NAME, Component.literal("§bLocation: " + this.coordsTextSupplier.get()));
                paper.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(Component.literal("§7Dimension: " + this.dimensionSupplier.get()))));
                return paper;
            }
            if (slot == 8) {
                ItemStack xpBottle = new ItemStack(Items.EXPERIENCE_BOTTLE);
                xpBottle.set(DataComponents.CUSTOM_NAME, Component.literal("§eXP: " + this.targetXpLevelSupplier.get() + " lvl"));
                return xpBottle;
            }
            if (slot == 7) {
                ItemStack enderChest = new ItemStack(Items.ENDER_CHEST);
                enderChest.set(DataComponents.CUSTOM_NAME, Component.literal("§6Open Ender Chest"));
                return enderChest;
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

    public InvSeeMenu(int syncId, Inventory playerInv, Container target, Runnable xpTransferAction, Runnable openEnderChestAction, Runnable tpAction, java.util.function.Supplier<String> coordsTextSupplier, java.util.function.Supplier<Integer> targetXpLevelSupplier, java.util.function.Supplier<String> dimensionSupplier, java.util.function.Supplier<java.util.List<Component>> statusLoreSupplier, java.util.function.Consumer<Integer> statusAction) {
        super(MenuType.GENERIC_9x5, syncId, playerInv, new Wrapper(target, coordsTextSupplier, targetXpLevelSupplier, dimensionSupplier, statusLoreSupplier), 5);
        this.xpTransferAction = xpTransferAction;
        this.openEnderChestAction = openEnderChestAction;
        this.tpAction = tpAction;
        this.statusAction = statusAction;

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
            if (slot.getContainerSlot() == 8 && slot.container instanceof Wrapper) {
                this.xpTransferAction.run();
                return;
            }
            if (slot.getContainerSlot() == 7 && slot.container instanceof Wrapper) {
                this.openEnderChestAction.run();
                return;
            }
            if (slot.getContainerSlot() == 6 && slot.container instanceof Wrapper) {
                this.tpAction.run();
                return;
            }
            if (slot.getContainerSlot() == 5 && slot.container instanceof Wrapper) {
                if (this.statusAction != null) this.statusAction.accept(button);
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }
}
