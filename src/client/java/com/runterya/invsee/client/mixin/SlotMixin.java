package com.runterya.invsee.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

@Mixin(Slot.class)
public abstract class SlotMixin {
    @Shadow public int index;

    @Inject(method = "getNoItemIcon", at = @At("HEAD"), cancellable = true)
    private void invsee$injectCustomIcon(CallbackInfoReturnable<Identifier> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null) return;
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        if (!(screen.getMenu() instanceof ChestMenu menu)) return;
        // InvSeeMenu uses a 9x5 chest (45 container slots + 36 player slots = 81 total slots)
        // Armor slots are at index 0-4 (0=helmet,1=chestplate,2=leggings,3=boots,4=offhand)
        if (menu.getContainer().getContainerSize() != 45) return;

        switch (this.index) {
            case 0 -> cir.setReturnValue(InventoryMenu.EMPTY_ARMOR_SLOT_HELMET);
            case 1 -> cir.setReturnValue(InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE);
            case 2 -> cir.setReturnValue(InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS);
            case 3 -> cir.setReturnValue(InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS);
            case 4 -> cir.setReturnValue(InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
        }
    }
}
