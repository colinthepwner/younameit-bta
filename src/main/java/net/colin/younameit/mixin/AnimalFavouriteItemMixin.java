package net.colin.younameit.mixin;

import net.minecraft.core.block.Blocks;
import net.minecraft.core.entity.animal.MobPig;
import net.minecraft.core.entity.animal.MobSheep;
import net.minecraft.core.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {MobPig.class, MobSheep.class}, remap = false)
public abstract class AnimalFavouriteItemMixin {

    @Inject(method = "isFavouriteItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void younameit$ignoreUnresolvableItem(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack == null) return;
        int id = stack.itemID;

        if (id >= 0 && id < Blocks.blocksList.length && Blocks.blocksList[id] == null) {
            cir.setReturnValue(false);
        }
    }
}
