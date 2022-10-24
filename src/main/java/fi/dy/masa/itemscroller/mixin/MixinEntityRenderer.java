package fi.dy.masa.itemscroller.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.EntityRenderer;

import fi.dy.masa.itemscroller.event.RenderEventHandler;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer
{
    @Inject(method = "updateCameraAndRender(FJ)V", at = {
            @At(value = "INVOKE", shift = Shift.AFTER, target = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/GuiScreen;IIF)V"),
            @At(value = "INVOKE", shift = Shift.AFTER, target = "Lnet/minecraft/client/gui/GuiScreen;drawScreen(IIF)V")
            },
            require = 1, allow = 1)
    private void onDrawScreenPost(float partialTicks, long nanoTime, CallbackInfo ci)
    {
        RenderEventHandler.instance().onDrawScreenPost();
    }
}
