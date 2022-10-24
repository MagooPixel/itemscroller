package fi.dy.masa.itemscroller.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.inventory.Slot;

@Mixin(Slot.class)
public interface IMixinSlot
{
    @Accessor
    int getSlotIndex();
}
