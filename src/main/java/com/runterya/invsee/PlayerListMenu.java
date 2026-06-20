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
    private final List<GameProfile> onlinePlayers;
    private final List<GameProfile> offlinePlayers;
    private int page;
    private final java.util.function.Consumer<GameProfile> onSelect;
    private final String clientLang;

    private static final int ITEMS_PER_PAGE = 18;
    private static final int PREV_SLOT = 18; // Row 3, slot 0
    private static final int NEXT_SLOT = 26; // Row 3, slot 8

    public PlayerListMenu(int syncId, Inventory playerInv, List<GameProfile> onlinePlayers, List<GameProfile> offlinePlayers, int page, java.util.function.Consumer<GameProfile> onSelect) {
        super(MenuType.GENERIC_9x3, syncId, playerInv, new SimpleContainer(27), 3);
        this.clientLang = InvSeeMod.getClientLang((net.minecraft.server.level.ServerPlayer) playerInv.player);
        this.onlinePlayers = onlinePlayers;
        this.offlinePlayers = offlinePlayers;
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

        int totalOnlinePages = (int) Math.ceil(onlinePlayers.size() / (double) ITEMS_PER_PAGE);
        if (totalOnlinePages == 0 && !offlinePlayers.isEmpty()) {
            totalOnlinePages = 0;
        } else if (totalOnlinePages == 0) {
            totalOnlinePages = 1;
        }

        int totalOfflinePages = (int) Math.ceil(offlinePlayers.size() / (double) ITEMS_PER_PAGE);
        int totalPages = totalOnlinePages + totalOfflinePages;
        if (totalPages == 0) totalPages = 1;

        boolean isOnlinePage = page < totalOnlinePages;
        
        List<GameProfile> currentList;
        int startIndex;
        boolean isOnline;

        if (isOnlinePage) {
            currentList = onlinePlayers;
            startIndex = page * ITEMS_PER_PAGE;
            isOnline = true;
        } else {
            currentList = offlinePlayers;
            startIndex = (page - totalOnlinePages) * ITEMS_PER_PAGE;
            isOnline = false;
        }

        int end = Math.min(startIndex + ITEMS_PER_PAGE, currentList.size());

        for (int i = 0; i < (end - startIndex); i++) {
            GameProfile profile = currentList.get(startIndex + i);
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
            head.set(DataComponents.CUSTOM_NAME, Component.literal((isOnline ? "§a" : "§7") + profile.name()));
            container.setItem(i, head);
        }

        if (page > 0) {
            ItemStack prev = new ItemStack(Items.ARROW);
            prev.set(DataComponents.CUSTOM_NAME, Component.literal("§e" + Lang.getFor(this.clientLang, "prev_page")));
            container.setItem(PREV_SLOT, prev);
        }

        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Items.ARROW);
            next.set(DataComponents.CUSTOM_NAME, Component.literal("§e" + Lang.getFor(this.clientLang, "next_page")));
            container.setItem(NEXT_SLOT, next);
        }
        
        ItemStack info = new ItemStack(isOnlinePage ? Items.LIME_CONCRETE : Items.RED_CONCRETE);
        info.set(DataComponents.CUSTOM_NAME, Component.literal(isOnlinePage ? "§a" + Lang.getFor(this.clientLang, "online_players") : "§c" + Lang.getFor(this.clientLang, "offline_players")));
        
        java.util.List<Component> loreList = new java.util.ArrayList<>();
        loreList.add(Component.literal("§b" + Lang.getFor(this.clientLang, "page_info", page + 1, totalPages)));
        
        if (isOnlinePage && !offlinePlayers.isEmpty()) {
            loreList.add(Component.literal(""));
            loreList.add(Component.literal("§e" + Lang.getFor(this.clientLang, "click_view_offline")));
        } else if (!isOnlinePage && !onlinePlayers.isEmpty()) {
            loreList.add(Component.literal(""));
            loreList.add(Component.literal("§e" + Lang.getFor(this.clientLang, "click_view_online")));
        }
        
        info.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(loreList));
        container.setItem(22, info);
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (slotId >= 0 && slotId < 27) {
            Slot slot = this.slots.get(slotId);
            int containerSlot = slot.getContainerSlot();
            
            if (containerSlot == PREV_SLOT && page > 0) {
                page--;
                refreshSlots();
                return;
            }
            
            int totalOnlinePages = (int) Math.ceil(onlinePlayers.size() / (double) ITEMS_PER_PAGE);
            if (totalOnlinePages == 0 && !offlinePlayers.isEmpty()) totalOnlinePages = 0;
            else if (totalOnlinePages == 0) totalOnlinePages = 1;
            
            int totalOfflinePages = (int) Math.ceil(offlinePlayers.size() / (double) ITEMS_PER_PAGE);
            int totalPages = totalOnlinePages + totalOfflinePages;
            if (totalPages == 0) totalPages = 1;

            if (containerSlot == NEXT_SLOT && page < totalPages - 1) {
                page++;
                refreshSlots();
                return;
            }
            
            if (containerSlot == 22) {
                boolean isOnlinePage = page < totalOnlinePages;
                if (isOnlinePage && !offlinePlayers.isEmpty()) {
                    page = totalOnlinePages;
                    refreshSlots();
                } else if (!isOnlinePage && !onlinePlayers.isEmpty()) {
                    page = 0;
                    refreshSlots();
                }
                return;
            }
            
            if (containerSlot >= 0 && containerSlot < ITEMS_PER_PAGE) {
                boolean isOnlinePage = page < totalOnlinePages;
                List<GameProfile> currentList = isOnlinePage ? onlinePlayers : offlinePlayers;
                int startIndex = isOnlinePage ? (page * ITEMS_PER_PAGE) : ((page - totalOnlinePages) * ITEMS_PER_PAGE);
                
                int index = startIndex + containerSlot;
                if (index < currentList.size()) {
                    GameProfile profile = currentList.get(index);
                    onSelect.accept(profile);
                }
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }
}
