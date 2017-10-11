package fi.dy.masa.itemscroller.event;

import java.lang.invoke.MethodHandle;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.items.SlotItemHandler;
import fi.dy.masa.itemscroller.ItemScroller;
import fi.dy.masa.itemscroller.config.Configs;
import fi.dy.masa.itemscroller.proxy.ClientProxy;
import fi.dy.masa.itemscroller.recipes.RecipeStorage;
import fi.dy.masa.itemscroller.util.InventoryUtils;
import fi.dy.masa.itemscroller.util.MethodHandleUtils;

public class InputEventHandler
{
    private static InputEventHandler instance;
    private boolean disabled;
    private int lastPosX;
    private int lastPosY;
    private int slotNumberLast;
    private final Set<Integer> draggedSlots = new HashSet<Integer>();
    private WeakReference<Slot> sourceSlotCandidate = new WeakReference<Slot>(null);
    private WeakReference<Slot> sourceSlot = new WeakReference<Slot>(null);
    private ItemStack stackInCursorLast = InventoryUtils.EMPTY_STACK;
    private RecipeStorage recipes;

    private static final MethodHandle methodHandle_getSlotAtPosition = MethodHandleUtils.getMethodHandleVirtual(GuiContainer.class,
            new String[] { "func_146975_c", "getSlotAtPosition" }, int.class, int.class);

    public InputEventHandler()
    {
        this.initializeRecipeStorage();
        instance = this;
    }

    public static InputEventHandler instance()
    {
        return instance;
    }

    private void slotChangedCraftingGridClient(World world, InventoryCrafting craftingInventory, InventoryCraftResult craftResultInventory)
    {
        ItemStack stack = ItemStack.EMPTY;
        IRecipe recipe = CraftingManager.findMatchingRecipe(craftingInventory, world);

        if (recipe != null &&
                (recipe.isDynamic() ||
                 world.getGameRules().getBoolean("doLimitedCrafting") ||
                 Minecraft.getMinecraft().player.getRecipeBook().isUnlocked(recipe)))
        {
            craftResultInventory.setRecipeUsed(recipe);
            stack = recipe.getCraftingResult(craftingInventory);
        }

        craftResultInventory.setInventorySlotContents(0, stack);
    }

    @SubscribeEvent
    public void onCraftingEventSlotChanged(CraftingEventSlotChanged event)
    {
        if (event.getWorld().isRemote && Configs.enableClientCraftingFixHook)
        {
            this.slotChangedCraftingGridClient(event.getWorld(), event.getCraftingInventory(), event.getCraftResultInventory());
        }
    }

    @SubscribeEvent
    public void onMouseInputEventPre(GuiScreenEvent.MouseInputEvent.Pre event)
    {
        if (this.disabled == false && event.getGui() instanceof GuiContainer &&
            (event.getGui() instanceof GuiContainerCreative) == false &&
            event.getGui().mc != null && event.getGui().mc.player != null &&
            Configs.GUI_BLACKLIST.contains(event.getGui().getClass().getName()) == false)
        {
            GuiContainer gui = (GuiContainer) event.getGui();
            int dWheel = Mouse.getEventDWheel();
            boolean cancel = false;

            if (dWheel != 0)
            {
                // When scrolling while the recipe view is open, change the selection instead of moving items
                if (RenderEventHandler.getRenderStoredRecipes())
                {
                    this.recipes.scrollSelection(dWheel < 0);
                }
                else
                {
                    cancel = InventoryUtils.tryMoveItems(gui, this.recipes, dWheel > 0);
                }
            }
            else
            {
                this.checkForItemPickup(gui);
                this.storeSourceSlotCandidate(gui);

                if (Configs.enableRightClickCraftingOneStack && Mouse.getEventButton() == 1 &&
                    InventoryUtils.isCraftingSlot(gui, gui.getSlotUnderMouse()))
                {
                    InventoryUtils.rightClickCraftOneStack(gui);
                }
                else if (Configs.enableShiftPlaceItems && InventoryUtils.canShiftPlaceItems(gui))
                {
                    cancel = this.shiftPlaceItems(gui);
                }
                else if (Configs.enableShiftDropItems && this.canShiftDropItems(gui))
                {
                    cancel = this.shiftDropItems(gui);
                }
                else if (Configs.enableDragMovingShiftLeft || Configs.enableDragMovingShiftRight || Configs.enableDragMovingControlLeft)
                {
                    cancel = this.dragMoveItems(gui);
                }
            }

            if (cancel)
            {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onKeyInputEventPre(GuiScreenEvent.KeyboardInputEvent.Pre event)
    {
        if ((event.getGui() instanceof GuiContainer) == false ||
            event.getGui().mc == null || event.getGui().mc.player == null)
        {
            return;
        }

        GuiContainer gui = (GuiContainer) event.getGui();

        if (Keyboard.getEventKey() == Keyboard.KEY_I && Keyboard.getEventKeyState() &&
            GuiScreen.isAltKeyDown() && GuiScreen.isCtrlKeyDown() && GuiScreen.isShiftKeyDown())
        {
            if (gui.getSlotUnderMouse() != null)
            {
                debugPrintSlotInfo(gui, gui.getSlotUnderMouse());
            }
            else
            {
                ItemScroller.logger.info("GUI class: {}", gui.getClass().getName());
            }
        }
        // Drop all matching stacks from the same inventory when pressing Ctrl + Shift + Drop key
        else if (Configs.enableControlShiftDropkeyDropItems && Keyboard.getEventKeyState() &&
            Configs.GUI_BLACKLIST.contains(gui.getClass().getName()) == false &&
            GuiScreen.isCtrlKeyDown() && GuiScreen.isShiftKeyDown() &&
            gui.mc.gameSettings.keyBindDrop.isActiveAndMatches(Keyboard.getEventKey()))
        {
            Slot slot = gui.getSlotUnderMouse();

            if (slot != null && slot.getHasStack())
            {
                InventoryUtils.dropStacks(gui, slot.getStack(), slot);
            }
        }
        // Toggle mouse functionality on/off
        else if (Keyboard.getEventKeyState() && ClientProxy.KEY_DISABLE.isActiveAndMatches(Keyboard.getEventKey()))
        {
            this.disabled = ! this.disabled;

            if (this.disabled)
            {
                gui.mc.player.playSound(SoundEvents.BLOCK_NOTE_BASS, 0.8f, 0.8f);
            }
            else
            {
                gui.mc.player.playSound(SoundEvents.BLOCK_NOTE_PLING, 0.5f, 1.0f);
            }
        }
        // Show or hide the recipe selection
        else if (Keyboard.getEventKey() == ClientProxy.KEY_RECIPE.getKeyCode())
        {
            if (Keyboard.getEventKeyState())
            {
                RenderEventHandler.setRenderStoredRecipes(true);
            }
            else
            {
                RenderEventHandler.setRenderStoredRecipes(false);
            }
        }
        // Store or load a recipe
        else if (Keyboard.getEventKeyState() && Keyboard.isKeyDown(ClientProxy.KEY_RECIPE.getKeyCode()) &&
                 Keyboard.getEventKey() >= Keyboard.KEY_1 && Keyboard.getEventKey() <= Keyboard.KEY_9)
        {
            int index = MathHelper.clamp(Keyboard.getEventKey() - Keyboard.KEY_1, 0, 8);
            InventoryUtils.storeOrLoadRecipe(gui, index);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event)
    {
        this.recipes.readFromDisk();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Load event)
    {
        this.recipes.writeToDisk();
    }

    public void initializeRecipeStorage()
    {
        this.recipes = new RecipeStorage(18, Configs.craftingScrollingSaveFileIsGlobal);
    }

    public RecipeStorage getRecipes()
    {
        return this.recipes;
    }

    /**
     * Store a reference to the slot when a slot is left or right clicked on.
     * The slot is then later used to determine which inventory an ItemStack was
     * picked up from, if the stack from the cursor is dropped while holding shift.
     */
    private void storeSourceSlotCandidate(GuiContainer gui)
    {
        // Left or right mouse button was pressed
        if (Mouse.getEventButtonState() && (Mouse.getEventButton() == 0 || Mouse.getEventButton() == 1))
        {
            Slot slot = gui.getSlotUnderMouse();

            if (slot != null)
            {
                ItemStack stackCursor = gui.mc.player.inventory.getItemStack();
                ItemStack stack = InventoryUtils.EMPTY_STACK;

                if (InventoryUtils.isStackEmpty(stackCursor) == false)
                {
                    // Do a cheap copy without NBT data
                    stack = new ItemStack(stackCursor.getItem(), InventoryUtils.getStackSize(stackCursor), stackCursor.getMetadata());
                }

                this.stackInCursorLast = stack;
                this.sourceSlotCandidate = new WeakReference<Slot>(slot);
            }
        }
    }

    /**
     * Check if the (previous) mouse event resulted in picking up a new ItemStack to the cursor
     */
    private void checkForItemPickup(GuiContainer gui)
    {
        ItemStack stackCursor = gui.mc.player.inventory.getItemStack();

        // Picked up or swapped items to the cursor, grab a reference to the slot that the items came from
        // Note that we are only checking the item and metadata here!
        if (InventoryUtils.isStackEmpty(stackCursor) == false && stackCursor.isItemEqual(this.stackInCursorLast) == false)
        {
            this.sourceSlot = new WeakReference<Slot>(this.sourceSlotCandidate.get());
        }
    }

    private static void debugPrintSlotInfo(GuiContainer gui, Slot slot)
    {
        if (slot == null)
        {
            ItemScroller.logger.info("slot was null");
            return;
        }

        boolean hasSlot = gui.inventorySlots.inventorySlots.contains(slot);
        Object inv = slot instanceof SlotItemHandler ? ((SlotItemHandler) slot).getItemHandler() : slot.inventory;
        String stackStr = InventoryUtils.getStackString(slot.getStack());

        ItemScroller.logger.info(String.format("slot: slotNumber: %d, getSlotIndex(): %d, getHasStack(): %s, " +
                "slot class: %s, inv class: %s, Container's slot list has slot: %s, stack: %s",
                slot.slotNumber, slot.getSlotIndex(), slot.getHasStack(), slot.getClass().getName(),
                inv != null ? inv.getClass().getName() : "<null>", hasSlot ? " true" : "false", stackStr));
    }

    private boolean shiftPlaceItems(GuiContainer gui)
    {
        Slot slot = gui.getSlotUnderMouse();

        // Left click to place the items from the cursor to the slot
        InventoryUtils.leftClickSlot(gui, slot.slotNumber);

        // Ugly fix to prevent accidentally drag-moving the stack from the slot that it was just placed into...
        this.draggedSlots.add(slot.slotNumber);

        InventoryUtils.tryMoveStacks(slot, gui, true, false, false);

        return true;
    }

    private boolean shiftDropItems(GuiContainer gui)
    {
        ItemStack stackReference = gui.mc.player.inventory.getItemStack();

        if (InventoryUtils.isStackEmpty(stackReference) == false)
        {
            stackReference = stackReference.copy();

            // First drop the existing stack from the cursor
            InventoryUtils.dropItemsFromCursor(gui);

            InventoryUtils.dropStacks(gui, stackReference, this.sourceSlot.get());
            return true;
        }

        return false;
    }

    private boolean canShiftDropItems(GuiContainer gui)
    {
        if (GuiScreen.isShiftKeyDown() == false || Mouse.getEventButton() != 0 ||
            InventoryUtils.isStackEmpty(gui.mc.player.inventory.getItemStack()))
        {
            return false;
        }

        int left = gui.getGuiLeft();
        int top = gui.getGuiTop();
        int xSize = gui.getXSize();
        int ySize = gui.getYSize();
        int mouseAbsX = Mouse.getEventX() * gui.width / gui.mc.displayWidth;
        int mouseAbsY = gui.height - Mouse.getEventY() * gui.height / gui.mc.displayHeight - 1;
        boolean isOutsideGui = mouseAbsX < left || mouseAbsY < top || mouseAbsX >= left + xSize || mouseAbsY >= top + ySize;

        return isOutsideGui && this.getSlotAtPosition(gui, mouseAbsX - left, mouseAbsY - top) == null;
    }

    private boolean dragMoveItems(GuiContainer gui)
    {
        int mouseX = Mouse.getEventX() * gui.width / gui.mc.displayWidth;
        int mouseY = gui.height - Mouse.getEventY() * gui.height / gui.mc.displayHeight - 1;

        if (InventoryUtils.isStackEmpty(gui.mc.player.inventory.getItemStack()) == false)
        {
            // Updating these here is part of the fix to preventing a drag after shift + place
            this.lastPosX = mouseX;
            this.lastPosY = mouseY;
            return false;
        }

        boolean eventKeyIsLeftButton = Mouse.getEventButton() == 0;
        boolean eventKeyIsRightButton = Mouse.getEventButton() == 1;
        boolean leftButtonDown = Mouse.isButtonDown(0);
        boolean rightButtonDown = Mouse.isButtonDown(1);
        boolean isShiftDown = GuiScreen.isShiftKeyDown();
        boolean isControlDown = GuiScreen.isCtrlKeyDown();
        boolean eitherMouseButtonDown = leftButtonDown || rightButtonDown;

        if ((isShiftDown && leftButtonDown && Configs.enableDragMovingShiftLeft == false) ||
            (isShiftDown && rightButtonDown && Configs.enableDragMovingShiftRight == false) ||
            (isControlDown && eitherMouseButtonDown && Configs.enableDragMovingControlLeft == false))
        {
            return false;
        }

        boolean leaveOneItem = leftButtonDown == false;
        boolean moveOnlyOne = isShiftDown == false;
        boolean cancel = false;

        if (Mouse.getEventButtonState())
        {
            if (((eventKeyIsLeftButton || eventKeyIsRightButton) && isControlDown && Configs.enableDragMovingControlLeft) ||
                (eventKeyIsRightButton && isShiftDown && Configs.enableDragMovingShiftRight))
            {
                // Reset this or the method call won't do anything...
                this.slotNumberLast = -1;
                cancel = this.dragMoveFromSlotAtPosition(gui, mouseX, mouseY, leaveOneItem, moveOnlyOne);
            }
        }

        // Check that either mouse button is down
        if (cancel == false && (isShiftDown || isControlDown) && eitherMouseButtonDown)
        {
            int distX = mouseX - this.lastPosX;
            int distY = mouseY - this.lastPosY;
            int absX = Math.abs(distX);
            int absY = Math.abs(distY);

            if (absX > absY)
            {
                int inc = distX > 0 ? 1 : -1;

                for (int x = this.lastPosX; ; x += inc)
                {
                    int y = absX != 0 ? this.lastPosY + ((x - this.lastPosX) * distY / absX) : mouseY;
                    this.dragMoveFromSlotAtPosition(gui, x, y, leaveOneItem, moveOnlyOne);

                    if (x == mouseX)
                    {
                        break;
                    }
                }
            }
            else
            {
                int inc = distY > 0 ? 1 : -1;

                for (int y = this.lastPosY; ; y += inc)
                {
                    int x = absY != 0 ? this.lastPosX + ((y - this.lastPosY) * distX / absY) : mouseX;
                    this.dragMoveFromSlotAtPosition(gui, x, y, leaveOneItem, moveOnlyOne);

                    if (y == mouseY)
                    {
                        break;
                    }
                }
            }
        }

        this.lastPosX = mouseX;
        this.lastPosY = mouseY;

        // Always update the slot under the mouse.
        // This should prevent a "double click/move" when shift + left clicking on slots that have more
        // than one stack of items. (the regular slotClick() + a "drag move" from the slot that is under the mouse
        // when the left mouse button is pressed down and this code runs).
        Slot slot = this.getSlotAtPosition(gui, mouseX, mouseY);
        this.slotNumberLast = slot != null ? slot.slotNumber : -1;

        if (eitherMouseButtonDown == false)
        {
            this.draggedSlots.clear();
        }

        return cancel;
    }

    private boolean dragMoveFromSlotAtPosition(GuiContainer gui, int x, int y, boolean leaveOneItem, boolean moveOnlyOne)
    {
        Slot slot = this.getSlotAtPosition(gui, x, y);
        boolean flag = slot != null && InventoryUtils.isValidSlot(slot, gui, true) && slot.canTakeStack(gui.mc.player);
        boolean cancel = flag && (leaveOneItem || moveOnlyOne);

        if (flag && slot.slotNumber != this.slotNumberLast && this.draggedSlots.contains(slot.slotNumber) == false)
        {
            if (moveOnlyOne)
            {
                cancel = InventoryUtils.tryMoveSingleItemToOtherInventory(slot, gui);
            }
            else if (leaveOneItem)
            {
                cancel = InventoryUtils.tryMoveAllButOneItemToOtherInventory(slot, gui);
            }
            else
            {
                InventoryUtils.shiftClickSlot(gui, slot.slotNumber);
                cancel = true;
            }

            this.draggedSlots.add(slot.slotNumber);
        }

        return cancel;
    }

    private Slot getSlotAtPosition(GuiContainer gui, int x, int y)
    {
        try
        {
            return (Slot) methodHandle_getSlotAtPosition.invokeExact(gui, x, y);
        }
        catch (Throwable e)
        {
            ItemScroller.logger.error("Error while trying invoke GuiContainer#getSlotAtPosition() from {}", gui.getClass().getSimpleName(), e);
        }

        return null;
    }
}