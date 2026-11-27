package com.mcsyr.clearitem;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

/**
 * 密集掉落物检测。
 * <p>
 * 性能模型：掉落事件只做一次常数开销的计数（取区块坐标、自增一个计数器），
 * 不做任何实体查询；只有当某个区块自上次复核以来新增的掉落物达到阈值时，
 * 才在下一 tick 复核一次该区块。复核只读取该区块的实体列表
 * （{@link Chunk#getEntities()}），不再遍历整个世界，也不使用
 * {@code getNearbyEntities} 那样的立方体范围查询。
 * <p>
 * 没有任何常驻的定时统计任务：平时计数为 0，开销为 0；提醒前一定会先复核真实数量，
 * 因此计数漂移不会造成误报。这依赖「掉落物必然触发 ItemSpawnEvent」这一前提，
 * 区块重新加载时从存档恢复的掉落物不计入（这类残留由每 15 分钟的定时清理处理）。
 * <p>
 * 复核结果达到 {@code ClearItem.ChunkMaxItems} 时立即清理该区块；
 * 达到 {@code DenseDrop.Threshold} 时向全服提醒，提醒里附带「传送」与「清理」两个按钮，
 * 两个按钮都只对持有 {@code ClearItem.Op.Command.DenseDrop} 的玩家生效。
 * 清理按 {@code DenseDrop.PurgeBatch} 分批跨 tick 执行，避免一次性移除上万件物品造成卡顿。
 */
public class DenseDrop {

    /** 各区块累计的掉落物计数 */
    private static final Map<String, Integer> Counts = new ConcurrentHashMap<>();

    /** 各区块上次复核时的计数，用于判断是否还需要复核 */
    private static final Map<String, Integer> LastVerified = new ConcurrentHashMap<>();

    /** 各区块上次提醒的时间（毫秒） */
    private static final Map<String, Long> LastAlert = new ConcurrentHashMap<>();

    /** 各区块取样到的掉落物位置，提醒里的坐标取自这里 */
    private static final Map<String, Location> Samples = new ConcurrentHashMap<>();

    /** 已排队等待复核的区块，避免同一区块重复排队 */
    private static final Map<String, Boolean> Pending = new ConcurrentHashMap<>();

    /** 缓存下来的 {@code Chunk#getEntities} 方法，不同服务端版本返回类型不同，按实际类型处理 */
    private static volatile Method GetEntitiesMethod;

    /** 计数表的上限，超过后整体清空重新计数，避免长期运行后无限增长 */
    private static final int MaxTrackedChunks = 20000;

    private DenseDrop() {
    }

    /** 坐标所在区块的区块坐标，负数同样向下取整 */
    public static int toChunk(double coordinate) {
        return (int) Math.floor(coordinate) >> 4;
    }

    public static String chunkKey(String world, int chunkX, int chunkZ) {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    public static boolean shouldAlert(int count, int threshold) {
        return threshold > 0 && count >= threshold;
    }

    /**
     * 是否需要复核：自上次复核以来新增的掉落物数量达到阈值。
     * 一次性刷出上万件物品时计数会直接跨过阈值，用差值判断可以保证一定触发一次复核。
     */
    public static boolean needsVerify(int current, int lastVerified, int trigger) {
        if (trigger <= 0) {
            return false;
        }
        return current - lastVerified >= trigger;
    }

    public static boolean isCoolingDown(long now, long lastAlertAt, int cooldownSeconds) {
        if (cooldownSeconds <= 0 || lastAlertAt <= 0) {
            return false;
        }
        return now - lastAlertAt < cooldownSeconds * 1000L;
    }

    /** 掉落事件入口：只做常数开销的计数，达到阈值时下一 tick 复核一次该区块 */
    public static void onItemSpawn(Item item) {
        if (item == null) {
            return;
        }
        int trigger = triggerCount();
        if (trigger == Integer.MAX_VALUE) {
            return;
        }

        Location location = item.getLocation();
        if (location == null) {
            return;
        }
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        int chunkX = toChunk(location.getX());
        int chunkZ = toChunk(location.getZ());
        String key = chunkKey(world.getName(), chunkX, chunkZ);

        int current = increment(key);
        Integer verified = LastVerified.get(key);
        if (!needsVerify(current, verified == null ? 0 : verified, trigger)) {
            return;
        }

        LastVerified.put(key, current);
        scheduleVerify(world, key, chunkX, chunkZ);
    }

    /** 复核入口，必须在主线程调用 */
    public static void verify(World world, int chunkX, int chunkZ) {
        if (world == null) {
            return;
        }
        String key = chunkKey(world.getName(), chunkX, chunkZ);
        Pending.remove(key);

        ChunkStat stat = statChunk(world, chunkX, chunkZ);
        if (stat == null || stat.count <= 0) {
            forget(key);
            return;
        }

        Counts.put(key, stat.count);
        LastVerified.put(key, stat.count);
        Samples.put(key, stat.sample);
        evaluate(world, chunkX, chunkZ, stat.count, stat.sample);
    }

    /**
     * 清理指定区块的掉落物，按 {@code DenseDrop.PurgeBatch} 分批跨 tick 执行。
     *
     * @param toDustbin     是否把物品放进公共垃圾箱
     * @param respectMinAge 是否保护未满最短存活时间的掉落物
     * @param callback      清理完成后的回调，可为 null
     */
    public static void purgeAsync(World world, int chunkX, int chunkZ, boolean toDustbin,
                                  boolean respectMinAge, PurgeCallback callback) {
        if (world == null) {
            if (callback != null) {
                callback.done(new Result());
            }
            return;
        }

        List<Item> targets = collectChunkItems(world, chunkX, chunkZ, respectMinAge);
        forget(chunkKey(world.getName(), chunkX, chunkZ));
        if (targets.isEmpty()) {
            if (callback != null) {
                callback.done(new Result());
            }
            return;
        }
        Bukkit.getScheduler().runTask(Main.plugin, new PurgeJob(targets, toDustbin, callback));
    }

    /** 当前各区块的计数快照，供管理员排查使用 */
    public static String report() {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : Counts.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                entries.add(entry);
            }
        }
        if (entries.isEmpty()) {
            return "§b[ClearItem] §f当前没有记录到掉落物";
        }
        entries.sort((left, right) -> right.getValue() - left.getValue());
        StringBuilder builder = new StringBuilder("§b[ClearItem] §f区块掉落物计数（最多显示 10 条）：");
        int limit = Math.min(10, entries.size());
        for (int index = 0; index < limit; ++index) {
            Map.Entry<String, Integer> entry = entries.get(index);
            builder.append("\n§7 - §f").append(entry.getKey()).append(" §e").append(entry.getValue()).append(" §7件");
            Location sample = Samples.get(entry.getKey());
            if (sample != null) {
                builder.append(" §7@ ").append(format(sample.getX()))
                        .append(" ").append(format(sample.getY()))
                        .append(" ").append(format(sample.getZ()));
            }
        }
        return builder.toString();
    }

    /** 两个清理阈值中较小的那个；都未开启时返回 Integer.MAX_VALUE */
    private static int triggerCount() {
        int trigger = Integer.MAX_VALUE;
        int autoClear = nz(Main.ClearItemChunkMaxItems);
        if (autoClear > 0) {
            trigger = Math.min(trigger, autoClear);
        }
        if (alertEnabled()) {
            int threshold = nz(Main.DenseDropThreshold);
            if (threshold > 0) {
                trigger = Math.min(trigger, threshold);
            }
        }
        return trigger;
    }

    /** 提醒功能是否开启；关闭后只保留 ChunkMaxItems 的即时清理 */
    private static boolean alertEnabled() {
        return Main.DenseDropEnable == null || Main.DenseDropEnable;
    }

    private static int increment(String key) {
        if (Counts.size() > MaxTrackedChunks) {
            Counts.clear();
            LastVerified.clear();
            Samples.clear();
        }
        Integer value = Counts.merge(key, 1, (left, right) -> left + right);
        return value == null ? 0 : value;
    }

    private static void scheduleVerify(World world, String key, int chunkX, int chunkZ) {
        if (Pending.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        Bukkit.getScheduler().runTask(Main.plugin, () -> verify(world, chunkX, chunkZ));
    }

    /** 读取单个区块的掉落物数量，区块未加载时返回 null */
    private static ChunkStat statChunk(World world, int chunkX, int chunkZ) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return null;
        }
        ChunkStat stat = new ChunkStat();
        for (Entity entity : chunkEntities(world, chunkX, chunkZ)) {
            if (!(entity instanceof Item)) {
                continue;
            }
            ++stat.count;
            if (stat.sample == null) {
                stat.sample = entity.getLocation();
            }
        }
        return stat;
    }

    /** 收集指定区块内需要清理的掉落物，必须在主线程调用 */
    private static List<Item> collectChunkItems(World world, int chunkX, int chunkZ, boolean respectMinAge) {
        List<Item> targets = new ArrayList<>();
        int minAge = nz(Main.ClearItemMinAge);
        for (Entity entity : chunkEntities(world, chunkX, chunkZ)) {
            if (!(entity instanceof Item)) {
                continue;
            }
            Item item = (Item) entity;
            if (respectMinAge && tools.isTooYoungToClear(item.getTicksLived(), minAge)) {
                continue;
            }
            targets.add(item);
        }
        return targets;
    }

    /**
     * 读取单个区块内的实体列表，区块未加载时返回空列表。
     * <p>
     * {@code Chunk#getEntities} 的返回类型在不同服务端版本上不一致（1.7.10 为 {@code Entity[]}，
     * 1.13 之后为 {@code Collection}），编译期只能固定一种签名，因此这里用反射调用并按实际类型展开；
     * 反射不可用时退回整个世界扫描再按区块过滤，行为仍然正确，只是开销更大。
     */
    private static List<Entity> chunkEntities(World world, int chunkX, int chunkZ) {
        List<Entity> entities = new ArrayList<>();
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return entities;
        }

        Object raw = null;
        try {
            Method method = GetEntitiesMethod;
            if (method == null) {
                method = Chunk.class.getMethod("getEntities");
                GetEntitiesMethod = method;
            }
            raw = method.invoke(world.getChunkAt(chunkX, chunkZ));
        } catch (Throwable throwable) {
            raw = null;
        }

        if (raw instanceof Entity[]) {
            for (Entity entity : (Entity[]) raw) {
                entities.add(entity);
            }
            return entities;
        }
        if (raw instanceof Collection) {
            for (Object value : (Collection<?>) raw) {
                if (value instanceof Entity) {
                    entities.add((Entity) value);
                }
            }
            return entities;
        }

        for (Entity entity : world.getEntities()) {
            Location location = entity.getLocation();
            if (location == null || location.getWorld() == null) {
                continue;
            }
            if (toChunk(location.getX()) != chunkX || toChunk(location.getZ()) != chunkZ) {
                continue;
            }
            entities.add(entity);
        }
        return entities;
    }

    private static void evaluate(World world, int chunkX, int chunkZ, int count, Location sample) {
        if (count <= 0 || sample == null) {
            return;
        }

        if (sample == null) {
            return;
        }
        String key = chunkKey(world.getName(), chunkX, chunkZ);
        long now = System.currentTimeMillis();

        // 即时清理是硬性行为，不受提醒冷却影响；沿用旧语义：保护未满最短存活时间的物品，不放进垃圾箱
        if (nz(Main.ClearItemChunkMaxItems) > 0 && count >= nz(Main.ClearItemChunkMaxItems)) {
            LastAlert.put(key, now);
            purgeAsync(world, chunkX, chunkZ, false, true, null);
            broadcastChunkMaxItems(world, sample, count);
            return;
        }

        if (!alertEnabled() || !shouldAlert(count, nz(Main.DenseDropThreshold))) {
            return;
        }

        Long last = LastAlert.get(key);
        if (isCoolingDown(now, last == null ? 0L : last, nz(Main.DenseDropAlertCooldown))) {
            return;
        }
        LastAlert.put(key, now);
        alert(world, chunkX, chunkZ, count, sample);
    }

    /** 全服提醒，附带传送与清理按钮 */
    private static void alert(World world, int chunkX, int chunkZ, int count, Location sample) {
        double x = sample.getX();
        double y = sample.getY();
        double z = sample.getZ();
        String worldName = world.getName();
        String message = text(Main.DenseDropMessageAlert)
                .replace("%world%", tools.IncludeWorldAlias(worldName))
                .replace("%X%", format(x))
                .replace("%Y%", format(y))
                .replace("%Z%", format(z))
                .replace("%count%", String.valueOf(count))
                .replace("%players%", nearbyPlayers(world, x, y, z, nz(Main.DenseDropNearbyRadius)));

        TextComponent line = new TextComponent(message);

        TextComponent teleport = new TextComponent(text(Main.DenseDropMessageTpButton));
        teleport.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/citem tp " + worldName + " " + format(x) + " " + format(y) + " " + format(z)));
        teleport.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(text(Main.DenseDropMessageTpInfo)).create()));

        TextComponent clean = new TextComponent(text(Main.DenseDropMessageCleanButton));
        clean.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/citem purge " + worldName + " " + chunkX + " " + chunkZ));
        clean.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(text(Main.DenseDropMessageCleanInfo)).create()));

        Player[] players = Bukkit.getServer().getOnlinePlayers().toArray(new Player[0]);
        for (Player player : players) {
            player.spigot().sendMessage(line, teleport, clean);
        }
        Bukkit.getServer().getConsoleSender().sendMessage(message);
    }

    private static void broadcastChunkMaxItems(World world, Location sample, int count) {
        String message = text(Main.ClearItemMessageClearChunkMaxItems)
                .replace("%world%", tools.IncludeWorldAlias(world.getName()))
                .replace("%X%", format(sample.getX()))
                .replace("%Y%", format(sample.getY()))
                .replace("%Z%", format(sample.getZ()))
                .replace("%count%", String.valueOf(count));
        Bukkit.getServer().broadcastMessage(message);
    }

    /** 半径内的玩家名，逗号分隔；没有则返回配置里的占位文案 */
    private static String nearbyPlayers(World world, double x, double y, double z, int radius) {
        if (radius <= 0) {
            return text(Main.DenseDropMessageNoNearby);
        }
        StringBuilder builder = new StringBuilder();
        double limit = (double) radius * radius;
        for (Player player : world.getPlayers()) {
            Location location = player.getLocation();
            double dx = location.getX() - x;
            double dy = location.getY() - y;
            double dz = location.getZ() - z;
            if (dx * dx + dy * dy + dz * dz > limit) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(player.getName());
        }
        return builder.length() == 0 ? text(Main.DenseDropMessageNoNearby) : builder.toString();
    }

    /** 移除单件掉落物，放入垃圾箱并累计结果 */
    private static void removeItem(Item item, boolean toDustbin, Result result) {
        if (item == null || !item.isValid()) {
            return;
        }
        Material type;
        int amount;
        try {
            type = item.getItemStack().getType();
            amount = item.getItemStack().getAmount();
        } catch (Throwable throwable) {
            type = null;
            amount = 0;
        }
        if (type == null || type == Material.AIR || amount <= 0) {
            ++result.removed;
            item.remove();
            return;
        }
        String name = type.name();
        if (Main.ClearItemWhiteList != null && Main.ClearItemWhiteList.contains(name)) {
            return;
        }
        if (toDustbin && (Main.BlockBlackList == null || !Main.BlockBlackList.contains(name))) {
            // 单件物品入箱失败不能中断整批清理，Dustbin.addItem 内部会读取 ItemMeta
            try {
                if (Dustbin.addItem(item.getItemStack())) {
                    ++result.dustbin;
                }
            } catch (Throwable throwable) {
                Main.plugin.getLogger().warning("掉落物放入公共垃圾箱失败：" + throwable);
            }
        }
        ++result.removed;
        item.remove();
    }

    private static void forget(String key) {
        Counts.remove(key);
        LastVerified.remove(key);
        Samples.remove(key);
    }

    /**
     * 丢弃某个世界的全部计数。
     * 定时清理会清空整个世界，此时这些计数已经没有意义，继续保留只会在下次掉落时
     * 让复核立即触发并报出错误的数量。
     */
    public static void forgetWorld(String worldName) {
        if (worldName == null) {
            return;
        }
        String prefix = worldName + ":";
        Counts.keySet().removeIf(key -> key.startsWith(prefix));
        LastVerified.keySet().removeIf(key -> key.startsWith(prefix));
        Samples.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** 单个区块的统计结果 */
    private static final class ChunkStat {
        private int count;
        private Location sample;
    }

    /** 清理完成回调 */
    public interface PurgeCallback {
        void done(Result result);
    }

    /** 分批清理任务，每 tick 处理一批 */
    private static final class PurgeJob implements Runnable {
        private final List<Item> targets;
        private final boolean toDustbin;
        private final PurgeCallback callback;
        private final Result result = new Result();
        private int cursor;

        private PurgeJob(List<Item> targets, boolean toDustbin, PurgeCallback callback) {
            this.targets = targets;
            this.toDustbin = toDustbin;
            this.callback = callback;
        }

        @Override
        public void run() {
            int batch = nz(Main.DenseDropPurgeBatch);
            if (batch < 1) {
                batch = 500;
            }
            int end = Math.min(cursor + batch, targets.size());
            for (; cursor < end; ++cursor) {
                removeItem(targets.get(cursor), toDustbin, result);
            }
            if (cursor < targets.size()) {
                Bukkit.getScheduler().runTask(Main.plugin, this);
                return;
            }
            if (callback != null) {
                callback.done(result);
            }
        }
    }

    /** 一次清理的结果 */
    public static final class Result {
        /** 被移除的掉落物数量 */
        public int removed;
        /** 其中放进公共垃圾箱的数量 */
        public int dustbin;
    }
}
