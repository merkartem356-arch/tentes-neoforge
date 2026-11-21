package com.tentnets;

import net.minecraft.world.item.Item;

/** Тепловизор. Работает, пока предмет в основной или второй руке (логика в ThermalClient). */
public class ThermalItem extends Item {
    public ThermalItem() {
        super(new Item.Properties().stacksTo(1));
    }
}
