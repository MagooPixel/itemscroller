package fi.dy.masa.itemscroller.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import fi.dy.masa.itemscroller.event.RenderEventHandler;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer
{
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "render(FJZ)V",
            at = @At(value = "INVOKE", shift = Shift.AFTER,
                     target = "Lnet/minecraft/client/gui/screen/Screen;render(IIF)V"))
    private void onDrawScreenPost(float partialTicks, long nanoTime, boolean renderWorldIn, CallbackInfo ci)
    {
        RenderEventHandler.instance().onDrawScreenPost(this.client);
    }
}
