package fi.dy.masa.itemscroller.recipes;

import javax.annotation.Nonnull;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import fi.dy.masa.malilib.util.ItemUtils;
import fi.dy.masa.malilib.util.data.IntRange;
import fi.dy.masa.malilib.util.nbt.NbtUtils;
import fi.dy.masa.itemscroller.util.Constants;
import fi.dy.masa.itemscroller.util.InventoryUtils;

public class CraftingRecipe
{
    private ItemStack result = InventoryUtils.EMPTY_STACK;
    private ItemStack[] recipe = new ItemStack[9];

    public CraftingRecipe()
    {
        this.ensureRecipeSizeAndClearRecipe(9);
    }

    public void ensureRecipeSize(int size)
    {
        if (this.getRecipeLength() != size)
        {
            this.recipe = new ItemStack[size];
        }
    }

    public void clearRecipe()
    {
        for (int i = 0; i < this.recipe.length; i++)
        {
            this.recipe[i] = InventoryUtils.EMPTY_STACK;
        }

        this.result = InventoryUtils.EMPTY_STACK;
    }

    public void ensureRecipeSizeAndClearRecipe(int size)
    {
        this.ensureRecipeSize(size);
        this.clearRecipe();
    }

    public void storeCraftingRecipe(Slot slot, GuiContainer gui, boolean clearIfEmpty)
    {
        IntRange range = CraftingHandler.getCraftingGridSlots(gui, slot);

        if (range != null)
        {
            if (slot.getHasStack())
            {
                int gridSize = range.getLength();
                int numSlots = gui.inventorySlots.inventorySlots.size();

                this.ensureRecipeSizeAndClearRecipe(gridSize);

                for (int i = 0, s = range.getFirst(); i < gridSize && s < numSlots; i++, s++)
                {
                    Slot slotTmp = gui.inventorySlots.getSlot(s);
                    this.recipe[i] = slotTmp.getHasStack() ? slotTmp.getStack().copy() : InventoryUtils.EMPTY_STACK;
                }

                this.result = slot.getStack().copy();
            }
            else if (clearIfEmpty)
            {
                this.clearRecipe();
            }
        }
    }

    public void copyRecipeFrom(CraftingRecipe other)
    {
        int size = other.getRecipeLength();
        ItemStack[] otherRecipe = other.getRecipeItems();

        this.ensureRecipeSizeAndClearRecipe(size);

        for (int i = 0; i < size; i++)
        {
            this.recipe[i] = InventoryUtils.isStackEmpty(otherRecipe[i]) == false ? otherRecipe[i].copy() : InventoryUtils.EMPTY_STACK;
        }

        this.result = InventoryUtils.isStackEmpty(other.getResult()) == false ? other.getResult().copy() : InventoryUtils.EMPTY_STACK;
    }

    public void readFromNBT(@Nonnull NBTTagCompound nbt)
    {
        if (NbtUtils.containsCompound(nbt, "Result") &&
            NbtUtils.containsList(nbt, "Ingredients"))
        {
            NBTTagList tagIngredients = NbtUtils.getList(nbt, "Ingredients", Constants.NBT.TAG_COMPOUND);
            int count = NbtUtils.getListSize(tagIngredients);
            int length = NbtUtils.getInt(nbt, "Length");

            if (length > 0)
            {
                this.ensureRecipeSizeAndClearRecipe(length);
            }

            for (int i = 0; i < count; i++)
            {
                NBTTagCompound tag = NbtUtils.getCompoundAt(tagIngredients, i);
                int slot = NbtUtils.getInt(tag, "Slot");

                if (slot >= 0 && slot < this.recipe.length)
                {
                    this.recipe[slot] = ItemUtils.fromTag(tag);
                }
            }

            this.result = ItemUtils.fromTag(NbtUtils.getCompound(nbt, "Result"));
        }
    }

    @Nonnull
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound nbt)
    {
        if (this.isValid())
        {
            NBTTagCompound tag = new NBTTagCompound();
            this.result.writeToNBT(tag);

            NbtUtils.putInt(nbt, "Length", this.recipe.length);
            NbtUtils.putTag(nbt, "Result", tag);

            NBTTagList tagIngredients = new NBTTagList();

            for (int i = 0; i < this.recipe.length; i++)
            {
                if (InventoryUtils.isStackEmpty(this.recipe[i]) == false)
                {
                    tag = new NBTTagCompound();
                    NbtUtils.putInt(tag, "Slot", i);
                    this.recipe[i].writeToNBT(tag);
                    NbtUtils.addTag(tagIngredients, tag);
                }
            }

            NbtUtils.putTag(nbt, "Ingredients", tagIngredients);
        }

        return nbt;
    }

    public ItemStack getResult()
    {
        return this.result;
    }

    public int getRecipeLength()
    {
        return this.recipe.length;
    }

    public ItemStack[] getRecipeItems()
    {
        return this.recipe;
    }

    public boolean isValid()
    {
        return InventoryUtils.isStackEmpty(this.getResult()) == false;
    }
}
