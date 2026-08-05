package net.colin.younameit.mixin;

import net.minecraft.core.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches {@code Entity.fireImmune}, which is where the flag actually lives.
 *
 * <p>{@code @Shadow} resolves a field only against the mixin's own target class, so shadowing
 * {@code fireImmune} from a {@code Player} mixin fails outright — Mixin reports
 * "@Shadow field fireImmune was not located in the target class" and, because that happens during
 * class transformation, it takes the whole game down at launch rather than degrading. Declaring
 * the accessor against {@code Entity}, the class that really declares the field, is the way to
 * reach an inherited member. The field is {@code protected}, so a plain cast would not compile
 * from this package either.
 */
@Mixin(value = Entity.class, remap = false)
public interface EntityFireImmuneAccessor {

    @Accessor("fireImmune")
    boolean younameit$isFireImmune();

    @Accessor("fireImmune")
    void younameit$setFireImmune(boolean value);
}
