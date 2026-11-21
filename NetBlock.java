package com.tentnets;

import com.mojang.serialization.MapCodec;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Тонкая мягкая сетка. Один класс для антидроновой и маскировочной сетки.
 *
 * FACING = UP    -> горизонтальная сетка под потолком клетки (натянута между опорами)
 * FACING = N/E/S/W -> вертикальная сетка вплотную к соседнему блоку
 * DAMAGE 0..9    -> стадия повреждения (по 2 HP, всего 20 HP)
 */
public class NetBlock extends Block {
    public enum Kind { ANTI_DRONE, CAMO }

    public static final DirectionProperty FACING = DirectionProperty.create("facing", d -> d != Direction.DOWN);
    public static final IntegerProperty DAMAGE = IntegerProperty.create("damage", 0, 9);

    /** 1 стадия повреждения = 2 HP, максимум 20 HP. */
    public static final float HP_PER_STAGE = 2.0F;

    private static final MapCodec<NetBlock> CODEC = simpleCodec(p -> new NetBlock(p, Kind.ANTI_DRONE));

    private static final VoxelShape SHAPE_UP = Block.box(0, 14, 0, 16, 16, 16);
    private static final VoxelShape SHAPE_NORTH = Block.box(0, 0, 0, 16, 16, 2);
    private static final VoxelShape SHAPE_SOUTH = Block.box(0, 0, 14, 16, 16, 16);
    private static final VoxelShape SHAPE_WEST = Block.box(0, 0, 0, 2, 16, 16);
    private static final VoxelShape SHAPE_EAST = Block.box(14, 0, 0, 16, 16, 16);

    /** Высота верхней плоскости горизонтальной сетки внутри клетки. */
    private static final double PLATE_TOP = 15.75 / 16.0;
    private static final int MAX_BFS_NODES = 256;

    private final Kind kind;

    public NetBlock(BlockBehaviour.Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(DAMAGE, 0));
    }

    public static BlockBehaviour.Properties props(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(0.3F)
                .sound(SoundType.WOOL)
                .noOcclusion()
                .pushReaction(PushReaction.DESTROY)
                .isValidSpawn((s, g, p, t) -> false)
                .isRedstoneConductor((s, g, p) -> false)
                .isSuffocating((s, g, p) -> false)
                .isViewBlocking((s, g, p) -> false);
    }

    public Kind getKind() {
        return kind;
    }

    public static boolean isCamo(BlockState state) {
        return state.getBlock() instanceof NetBlock nb && nb.kind == Kind.CAMO;
    }

    @Override
    public MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DAMAGE);
    }

    // ---------------------------------------------------------------- форма

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_UP;
        };
    }

    /** Сетка мягкая: физику делаем сами в entityInside. */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    // ------------------------------------------------- установка и опора

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction clicked = ctx.getClickedFace();
        Level level = ctx.getLevel();
        Direction facing;
        BlockState other = ctx.replacingClickedOnBlock()
                ? Blocks.AIR.defaultBlockState()
                : level.getBlockState(ctx.getClickedPos().relative(clicked.getOpposite()));
        if (other.getBlock() instanceof NetBlock && clicked.getAxis() != other.getValue(FACING).getAxis()) {
            // продолжаем существующую сетку в её плоскости
            facing = other.getValue(FACING);
        } else if (clicked.getAxis().isVertical()) {
            facing = Direction.UP;
        } else {
            facing = clicked.getOpposite();
        }
        BlockState st = defaultBlockState().setValue(FACING, facing);
        return st.canSurvive(level, ctx.getClickedPos()) ? st : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return isAnchored(level, pos, state.getValue(FACING));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!isAnchored(level, pos, state.getValue(FACING))) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    private static Direction[] inPlane(Direction facing) {
        if (facing == Direction.UP) {
            return new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        }
        Direction left = facing.getClockWise();
        return new Direction[]{Direction.UP, Direction.DOWN, left, left.getOpposite()};
    }

    private static boolean sturdy(LevelReader level, BlockPos pos, Direction faceTowardNet) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, faceTowardNet);
    }

    private static boolean directSupport(LevelReader level, BlockPos p, Direction facing) {
        if (facing == Direction.UP) {
            if (sturdy(level, p.above(), Direction.DOWN)) {
                return true;
            }
        } else if (sturdy(level, p.relative(facing), facing.getOpposite())) {
            return true;
        }
        for (Direction d : inPlane(facing)) {
            if (sturdy(level, p.relative(d), d.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    /** Сетка держится, если сама или через цепочку соседних сеток касается твёрдого блока. */
    private static boolean isAnchored(LevelReader level, BlockPos start, Direction facing) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(start);
        seen.add(start.asLong());
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            if (directSupport(level, p, facing)) {
                return true;
            }
            if (seen.size() >= MAX_BFS_NODES) {
                return true; // очень большая сетка: считаем закреплённой
            }
            for (Direction d : inPlane(facing)) {
                BlockPos n = p.relative(d);
                if (seen.contains(n.asLong())) {
                    continue;
                }
                BlockState s = level.getBlockState(n);
                if (s.getBlock() instanceof NetBlock && s.getValue(FACING) == facing) {
                    seen.add(n.asLong());
                    queue.add(n);
                }
            }
        }
        return false;
    }

    // ----------------------------------------------------------- урон

    /** Нанести сетке урон в HP (20 HP = 10 стадий). Вызывать только на сервере. */
    public static void damageNet(Level level, BlockPos pos, BlockState state, float hp) {
        float stages = hp / HP_PER_STAGE;
        int add = (int) stages;
        if (level.random.nextFloat() < stages - add) {
            add++;
        }
        if (add <= 0) {
            return;
        }
        int next = state.getValue(DAMAGE) + add;
        if (next > 9) {
            level.destroyBlock(pos, false); // порвалась, без дропа
        } else {
            level.setBlock(pos, state.setValue(DAMAGE, next), 3);
        }
    }

    private void handleHit(NetHit.Kind hit, BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!entity.addTag("tn" + pos.asLong())) {
            return; // эта сущность уже ударила эту сетку
        }
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hurtMarked = true;
        level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
        damageNet(level, pos, state, hit == NetHit.Kind.DRONE ? NetHit.DRONE_DAMAGE : NetHit.SHELL_DAMAGE);
    }

    // ---------------------------------------------------------- физика

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity.isSpectator()) {
            return;
        }
        NetHit.Kind hit = NetHit.classify(entity);
        if (hit != NetHit.Kind.NONE) {
            if (!level.isClientSide) {
                handleHit(hit, state, level, pos, entity);
            }
            return;
        }
        if (!(entity instanceof LivingEntity living) || living.isFallFlying()) {
            return;
        }
        if (entity instanceof Player p && p.getAbilities().flying) {
            return;
        }
        if (state.getValue(FACING) == Direction.UP) {
            topPhysics(state, level, pos, living);
        } else {
            sidePhysics(state, pos, living);
        }
    }

    /** Горизонтальная сетка: пружина (провисает под падающим, слегка подбрасывает). */
    private void topPhysics(BlockState state, Level level, BlockPos pos, LivingEntity e) {
        Vec3 v = e.getDeltaMovement();
        double feet = e.getY();
        double base = pos.getY();
        double top = base + PLATE_TOP;
        double head = e.getBoundingBox().maxY;

        boolean above = feet >= base + 0.6 || (v.y < -0.5 && feet >= base - 0.9);
        if (above) {
            if (v.y <= 0.1) {
                double target = v.y * 0.4 + (top - feet) * 0.7;
                double floor = Math.min(0.0, (base + 0.6) - feet);
                target = Math.max(target, floor);
                target = Math.min(target, 0.25);
                e.setDeltaMovement(v.x * 0.93, target, v.z * 0.93);
                e.fallDistance = 0.0F;
                e.setOnGround(true);
            }
            if (!level.isClientSide && e instanceof Player) {
                checkTear(level, pos, state);
            }
        } else if (v.y > 0.1 && head > base + 0.8 && head < top + 0.6) {
            // игрок прыгает снизу: сетка мягко гасит импульс
            e.setDeltaMovement(v.x, v.y * 0.3, v.z);
        }
    }

    /** Два игрока, прыгающие на одной сетке, рвут её сразу. */
    private void checkTear(Level level, BlockPos pos, BlockState state) {
        AABB box = new AABB(pos.getX(), pos.getY() + 0.5, pos.getZ(), pos.getX() + 1, pos.getY() + 2.5, pos.getZ() + 1);
        List<Player> players = level.getEntitiesOfClass(Player.class, box,
                p -> !p.isSpectator() && !p.getAbilities().flying && p.getY() >= pos.getY() + 0.6);
        if (players.size() >= 2 && players.stream().anyMatch(p -> p.getDeltaMovement().y > 0.3)) {
            level.destroyBlock(pos, false);
        }
    }

    /** Вертикальная сетка: гасит скорость и слегка отталкивает назад. */
    private void sidePhysics(BlockState state, BlockPos pos, LivingEntity e) {
        Direction f = state.getValue(FACING);
        boolean xAxis = f.getAxis() == Direction.Axis.X;
        double plane = switch (f) {
            case NORTH -> pos.getZ() + 0.06;
            case SOUTH -> pos.getZ() + 0.94;
            case WEST -> pos.getX() + 0.06;
            default -> pos.getX() + 0.94;
        };
        Vec3 v = e.getDeltaMovement();
        double d = (xAxis ? e.getX() : e.getZ()) - plane;
        double vn = xAxis ? v.x : v.z;
        if (Math.abs(d) < 0.5 && vn * d < 0) {
            double nv = vn * 0.45 + Math.signum(d) * 0.03;
            e.setDeltaMovement(xAxis ? nv : v.x, v.y, xAxis ? v.z : nv);
        }
    }
}
