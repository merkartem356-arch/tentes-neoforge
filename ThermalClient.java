package com.tentnets;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Клиентская часть тепловизора: чёрно-белый шейдер и белое свечение игроков. */
public final class ThermalClient {
    private static final ResourceLocation POST =
            ResourceLocation.fromNamespaceAndPath("tentnets", "shaders/post/thermal.json");
    private static final double RANGE = 64.0;
    private static final Set<Integer> GLOWING = new HashSet<>();
    private static boolean loadedByUs = false;

    private ThermalClient() {}

    private static boolean isUsing(Player p) {
        return p.getMainHandItem().getItem() instanceof ThermalItem
                || p.getOffhandItem().getItem() instanceof ThermalItem;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null) {
            GLOWING.clear();
            loadedByUs = false;
            return;
        }
        boolean on = isUsing(me);

        if (on) {
            if (mc.gameRenderer.currentEffect() == null) {
                mc.gameRenderer.loadEffect(POST);
                loadedByUs = true;
            }
        } else if (loadedByUs) {
            if (mc.gameRenderer.currentEffect() != null) {
                mc.gameRenderer.shutdownEffect();
            }
            loadedByUs = false;
        }

        for (Player p : mc.level.players()) {
            if (p == me) {
                continue;
            }
            boolean glow = on && !p.isSpectator() && me.distanceTo(p) <= RANGE && !hidden(mc.level, me, p);
            if (glow) {
                p.setGlowingTag(true);
                GLOWING.add(p.getId());
            } else if (GLOWING.remove(p.getId())) {
                p.setGlowingTag(false);
            }
        }
    }

    /** Игрок не виден тепловизору, если он в маскировочной сетке или она на линии взгляда. */
    private static boolean hidden(Level level, Player viewer, Player target) {
        Vec3 a = viewer.getEyePosition();
        Vec3 b = target.position().add(0, target.getBbHeight() * 0.5, 0);
        if (camoAt(level, BlockPos.containing(b)) || camoAt(level, target.blockPosition())) {
            return true;
        }
        double dist = a.distanceTo(b);
        int steps = (int) Math.ceil(dist * 4.0);
        for (int i = 1; i < steps; i++) {
            Vec3 p = a.lerp(b, i / (double) steps);
            if (camoAt(level, BlockPos.containing(p))) {
                return true;
            }
        }
        return false;
    }

    private static boolean camoAt(Level level, BlockPos pos) {
        return NetBlock.isCamo(level.getBlockState(pos));
    }
}
