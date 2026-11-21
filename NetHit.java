package com.tentnets;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

/** Определяет, дрон это или снаряд. Теги + запасной поиск по имени сущности. */
public final class NetHit {
    public enum Kind { NONE, DRONE, SHELL }

    public static final TagKey<EntityType<?>> DRONES =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("tentnets", "drones"));
    public static final TagKey<EntityType<?>> SHELLS =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("tentnets", "shells"));

    /** 20 HP: 2-3 дрона (8 HP) или 5-6 снарядов (3.6 HP). */
    public static final float DRONE_DAMAGE = 8.0F;
    public static final float SHELL_DAMAGE = 3.6F;

    private static final String[] DRONE_WORDS = {"dron", "uav", "quad"};
    private static final String[] SHELL_WORDS = {"shell", "mortar", "rocket", "missile", "grenade", "snaryad", "snariad", "artillery"};

    private NetHit() {}

    public static Kind classify(Entity entity) {
        if (entity instanceof Player) {
            return Kind.NONE;
        }
        EntityType<?> type = entity.getType();
        if (type.is(DRONES)) {
            return Kind.DRONE;
        }
        if (type.is(SHELLS)) {
            return Kind.SHELL;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null || "minecraft".equals(id.getNamespace())) {
            return Kind.NONE;
        }
        String path = id.getPath().toLowerCase();
        for (String w : DRONE_WORDS) {
            if (path.contains(w)) {
                return Kind.DRONE;
            }
        }
        for (String w : SHELL_WORDS) {
            if (path.contains(w)) {
                return Kind.SHELL;
            }
        }
        return Kind.NONE;
    }
}
