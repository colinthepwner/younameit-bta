package net.colin.younameit.mixin;

import net.colin.younameit.YniConfig;
import net.colin.younameit.item.YniGear;
import net.minecraft.core.enums.HumanArmorShape;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Player.class, remap = false)
public abstract class PlayerFireSetMixin {

    @Unique
    private boolean younameit$grantedImmunity = false;

    @Unique
    private int younameit$frostTick = 0;

    @Inject(method = "tick", at = @At("TAIL"))
    private void younameit$applyFireSetBonus(CallbackInfo ci) {
        Player self = (Player) (Object) this;

        younameit$frostWalk(self);

        if (!YniConfig.fireResistantSetBonus) {
            younameit$clear();
            return;
        }

        boolean complete = true;
        for (HumanArmorShape slot : HumanArmorShape.values()) {
            ItemStack stack = self.getItemInArmorSlot(slot);
            if (stack == null || !(stack.getItem() instanceof YniGear gear)
                    || !gear.yniSet().stats.fireResistant) {
                complete = false;
                break;
            }
        }

        if (complete) {
            younameit$accessor().younameit$setFireImmune(true);
            younameit$grantedImmunity = true;
        } else {
            younameit$clear();
        }
    }

    @Unique
    private void younameit$frostWalk(Player self) {
        if (self.world == null) return;
        if (++younameit$frostTick < 10) return;
        younameit$frostTick = 0;

        ItemStack boots = self.getItemInArmorSlot(HumanArmorShape.BOOTS);
        if (boots == null || !(boots.getItem() instanceof YniGear gear)) return;
        if (!gear.yniSet().stats.freezesWater) return;

        int px = (int) Math.floor(self.x);
        int py = (int) Math.floor(self.y) - 1;
        int pz = (int) Math.floor(self.z);
        int stillWater = Blocks.FLUID_WATER_STILL.id();
        int ice = Blocks.ICE.id();

        boolean froze = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = px + dx, z = pz + dz;
                try {
                    if (self.world.getBlockId(x, py, z) != stillWater) continue;

                    if (!self.world.isAirBlock(x, py + 1, z)) continue;
                    if (self.world.setBlockWithNotify(x, py, z, ice)) froze = true;
                } catch (Throwable ignored) {

                }
            }
        }

        if (froze) boots.damageItem(1, self);
    }

    @Unique
    private void younameit$clear() {
        if (younameit$grantedImmunity) {
            younameit$accessor().younameit$setFireImmune(false);
            younameit$grantedImmunity = false;
        }
    }

    @Unique
    private EntityFireImmuneAccessor younameit$accessor() {
        return (EntityFireImmuneAccessor) this;
    }
}
