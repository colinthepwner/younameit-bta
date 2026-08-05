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

/**
 * The full-set bonus: a complete set made from a fireproof material stops fire and lava hurting.
 *
 * <p>The armour material's own FIRE protection is not enough on its own. BTA clamps the damage
 * multiplier with {@code Math.max(protection, 0.01F)}, so even 100% protection still lets a
 * rounded-up point of damage through, and {@code lavaHurt} sets the burning timer regardless of
 * how well protected you are. {@code fireImmune} is the flag that actually short-circuits both
 * {@code fireHurt()} and {@code lavaHurt()}, so that is what gets toggled — via
 * {@link EntityFireImmuneAccessor}, because the field is declared on {@code Entity}.
 *
 * <p>Only ever set back to false if this mixin was the thing that set it, so nothing else that
 * grants fire immunity gets stomped.
 */
@Mixin(value = Player.class, remap = false)
public abstract class PlayerFireSetMixin {

    @Unique
    private boolean younameit$grantedImmunity = false;

    @Unique
    private int younameit$frostTick = 0;

    @Inject(method = "tick", at = @At("TAIL"))
    private void younameit$applyFireSetBonus(CallbackInfo ci) {
        Player self = (Player) (Object) this;

        // Frost walking runs first and unconditionally. Putting it after the fire-bonus early
        // return would have quietly tied it to fireResistantSetBonus, so turning off one feature
        // would disable an unrelated one.
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

    /**
     * Ice boots freeze the water they walk over, wearing themselves down as they go.
     *
     * <p>Run on a cadence rather than every tick, which keeps both the block writes and the
     * durability drain gentle: standing still on a frozen pond costs nothing once it is frozen,
     * because only water still counts. Flowing water is left alone — freezing a waterfall mid-fall
     * looks like a bug rather than a feature.
     */
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
                    // Only surface water, so nothing is sealed under a lid of ice.
                    if (!self.world.isAirBlock(x, py + 1, z)) continue;
                    if (self.world.setBlockWithNotify(x, py, z, ice)) froze = true;
                } catch (Throwable ignored) {
                    // A chunk edge or an unloaded neighbour is not worth stopping for.
                }
            }
        }

        // One point of wear per freezing step, not per block, so a wide pond is no harsher
        // than a narrow one.
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
