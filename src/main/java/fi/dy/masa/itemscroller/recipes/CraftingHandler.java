package fi.dy.masa.itemscroller.recipes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.ContainerScreen;
import net.minecraft.container.Container;
import net.minecraft.container.Slot;
import fi.dy.masa.itemscroller.ItemScroller;

public class CraftingHandler
{
    private static final Map<CraftingOutputSlot, SlotRange> CRAFTING_GRID_SLOTS = new HashMap<CraftingOutputSlot, SlotRange>();
    private static final Set<Class<? extends ContainerScreen<?>>> CRAFTING_GUIS = new HashSet<>();

    public static void clearDefinitions()
    {
        CRAFTING_GRID_SLOTS.clear();
        CRAFTING_GUIS.clear();
    }

    @SuppressWarnings("unchecked")
    public static boolean addCraftingGridDefinition(String guiClassName, int outputSlot, SlotRange range)
    {
        try
        {
            Class<? extends ContainerScreen<?>> guiClass = (Class<? extends ContainerScreen<?>>) Class.forName(guiClassName);

            CRAFTING_GRID_SLOTS.put(new CraftingOutputSlot(guiClass, outputSlot), range);
            CRAFTING_GUIS.add(guiClass);

            return true;
        }
        catch (Exception e)
        {
            ItemScroller.logger.warn("addCraftingGridDefinition(): Failed to find classes for grid definition: gui: '{}', outputSlot: {}", guiClassName, outputSlot);
        }

        return false;
    }

    public static boolean isCraftingGui(Screen gui)
    {
        return (gui instanceof ContainerScreen) && CRAFTING_GUIS.contains(((ContainerScreen<?>) gui).getClass());
    }

    /**
     * Gets the crafting grid SlotRange associated with the given slot in the given gui, if any.
     * @param gui
     * @param slot
     * @return the SlotRange of the crafting grid, or null, if the given slot is not a crafting output slot
     */
    @Nullable
    public static SlotRange getCraftingGridSlots(ContainerScreen<?> gui, Slot slot)
    {
        return CRAFTING_GRID_SLOTS.get(CraftingOutputSlot.from(gui, slot.id));
    }

    @Nullable
    public static Slot getFirstCraftingOutputSlotForGui(ContainerScreen<? extends Container> gui)
    {
        if (CRAFTING_GUIS.contains(gui.getClass()))
        {
            for (Slot slot : gui.getContainer().slots)
            {
                if (getCraftingGridSlots(gui, slot) != null)
                {
                    return slot;
                }
            }
        }

        return null;
    }

    public static class CraftingOutputSlot
    {
        private final Class<? extends ContainerScreen<?>> guiClass;
        private final int outputSlot;

        private CraftingOutputSlot (Class<? extends ContainerScreen<?>> guiClass, int outputSlot)
        {
            this.guiClass = guiClass;
            this.outputSlot = outputSlot;
        }

        @SuppressWarnings("unchecked")
        public static CraftingOutputSlot from(ContainerScreen<?> gui, int slotId)
        {
            return new CraftingOutputSlot((Class<? extends ContainerScreen<?>>) gui.getClass(), slotId);
        }

        public Class<? extends ContainerScreen<?>> getGuiClass()
        {
            return this.guiClass;
        }

        public int getSlotNumber()
        {
            return this.outputSlot;
        }

        public boolean matches(ContainerScreen<?> gui, Slot slot, int outputSlot)
        {
            return outputSlot == this.outputSlot && gui.getClass() == this.guiClass;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((guiClass == null) ? 0 : guiClass.hashCode());
            result = prime * result + outputSlot;
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CraftingOutputSlot other = (CraftingOutputSlot) obj;
            if (guiClass == null)
            {
                if (other.guiClass != null)
                    return false;
            }
            else if (guiClass != other.guiClass)
                return false;
            if (outputSlot != other.outputSlot)
                return false;
            return true;
        }

    }

    public static class SlotRange
    {
        private final int first;
        private final int last;

        public SlotRange(int start, int numSlots)
        {
            this.first = start;
            this.last = start + numSlots - 1;
        }

        public int getFirst()
        {
            return this.first;
        }

        public int getLast()
        {
            return this.last;
        }

        public int getSlotCount()
        {
            return this.last - this.first + 1;
        }

        public boolean contains(int slot)
        {
            return slot >= this.first && slot <= this.last;
        }

        @Override
        public String toString()
        {
            return String.format("SlotRange: {first: %d, last: %d}", this.first, this.last);
        }
    }
}
