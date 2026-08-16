package net.colin.younameit.mixin;

import net.minecraft.core.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Entity.class, remap = false)
public interface EntityFireImmuneAccessor {

    @Accessor("fireImmune")
    boolean younameit$isFireImmune();

    @Accessor("fireImmune")
    void younameit$setFireImmune(boolean value);
}
