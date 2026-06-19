package com.runterya.invsee;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.component.ResolvableProfile;
import java.util.List;

public class PlayerListMenu extends ChestMenu {
    private final List<GameProfile> players;
    private int page;
    private final java.util.function.Consumer<GameProfile> onSelect;

    private static final int[] PLAYER_SLOTS = {2, 3, 4, 5, 6, 11, 12, 13, 14, 15};
    private static final int PREV_SLOT = 18;
    private static final int NEXT_SLOT = 26;

    public PlayerListMenu(int syncId, Inventory playerInv, List<GameProfile> players, int page, java.util.function.Consumer<GameProfile> onSelect) {
        super(MenuType.GENERIC_9x3, syncId, playerInv, new SimpleContainer(27), 3);
        this.players = players;
        this.page = page;
        this.onSelect = onSelect;

        Container wrapped = this.getContainer();

        for (int j = 0; j < 27; ++j) {
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

        refreshSlots();
    }

    private void refreshSlots() {
        Container container = this.getContainer();
        container.clearContent();

        int start = page * 10;
        int end = Math.min(start + 10, players.size());

        for (int i = 0; i < (end - start); i++) {
            GameProfile profile = players.get(start + i);
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, new ResolvableProfile(profile));
            head.set(DataComponents.CUSTOM_NAME, Component.literal("§a" + profile.getName()));
            container.setItem(PLAYER_SLOTS[i], head);
        }

        if (page > 0) {
            ItemStack prev = new ItemStack(Items.ARROW);
            prev.set(DataComponents.CUSTOM_NAME, Component.literal("§ePrevious Page"));
            container.setItem(PREV_SLOT, prev);
        }

        if (end < players.size()) {
            ItemStack next = new ItemStack(Items.ARROW);
            next.set(DataComponents.CUSTOM_NAME, Component.literal("§eNext Page"));
            container.setItem(NEXT_SLOT, next);
        }
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (slotId >= 0 && slotId < this.slots.size()) {
            Slot slot = this.slots.get(slotId);
            int containerSlot = slot.getContainerSlot();
            
            if (containerSlot == PREV_SLOT && page > 0) {
                page--;
                refreshSlots();
                return;
            }
            if (containerSlot == NEXT_SLOT && (page * 10 + 10) < players.size()) {
                page++;
                refreshSlots();
                return;
            }
            
            for (int i = 0; i < PLAYER_SLOTS.length; i++) {
                if (containerSlot == PLAYER_SLOTS[i]) {
                    int index = page * 10 + i;
                    if (index < players.size()) {
                        GameProfile profile = players.get(index);
                        player.closeContainer();
                        onSelect.accept(profile);
                    }
                    return;
                }
            }
        }
        super.clicked(slotId, button, clickType, player);
    }
}
