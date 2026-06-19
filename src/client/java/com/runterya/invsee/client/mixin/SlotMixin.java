package com.runterya.invsee.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Slot.class)
public abstract class SlotMixin {
    @Shadow public int index;

    @Inject(method = "getNoItemIcon", at = @At("HEAD"), cancellable = true)
    private void invsee$injectCustomIcon(CallbackInfoReturnable<Identifier> cir) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.screen instanceof ContainerScreen screen) {
            String title = screen.getTitle().getString();
            if (title.endsWith("'s Inventory") || title.endsWith("'s Offline Inv")) {
                if (this.index == 0) cir.setReturnValue(InventoryMenu.EMPTY_ARMOR_SLOT_HELMET);
                else if (this.index == 1) cir.setReturnValue(InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE);
                else if (this.index == 2) cir.setReturnValue(InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS);
                else if (this.index == 3) cir.setReturnValue(InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS);
                else if (this.index == 4) cir.setReturnValue(InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
            }
        }
    }
}
