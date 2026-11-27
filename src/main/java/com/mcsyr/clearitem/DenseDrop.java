package com.mcsyr.clearitem;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

/**
 * 密集掉落物检测。
 * <p>
 * 掉落事件只做一次常数开销的计数，不再像旧实现那样每次掉落都扫描周边实体
 * （旧实现每掉落一件就调用一次 getNearbyEntities，掉落物上万时扫描量是掉落数的平方，
 * 本身就是卡服来源）。计数达到阈值时，下一 tick 才复核一次该区块的实际数量。
 * <p>
 * 复核结果达到 {@code ClearItem.ChunkMaxItems} 时沿用旧的即时清理；
 * 达到 {@code DenseDrop.Threshold} 时向全服提醒，提醒里附带「传送」与「清理」两个按钮，
 * 两个按钮都只对持有 {@code ClearItem.Op.Command.DenseDrop} 的玩家生效。
 */
public class DenseDrop {

    /** 各区块最近一次统计得到的掉落物数量 */
    private static final Map<String, Integer> Counts = new ConcurrentHashMap<>();

    /** 各区块最近一次统计时取样到的掉落物位置 */
    private static final Map<String, Location> Samples = new ConcurrentHashMap<>();

    /** 各区块上次提醒的时间（毫秒） */
    private static final Map<String, Long> LastAlert = new ConcurrentHashMap<>();

    /** 掉落事件累加的快速计数，每次统计时校正 */
    private static final Map<String, Integer> FastCounts = new ConcurrentHashMap<>();

    /** 各区块上次复核时的计数，避免同一批掉落物反复复核 */
    private static final Map<String, Integer> LastVerified = new ConcurrentHashMap<>();

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

    /** 定时统计任务是否已注册，重载配置时不重复注册 */
    private static boolean Started = false;

    /** 启动定时统计任务，关闭功能时不注册；重复调用只注册一次 */
    public static void start() {
        if (Started) {
            return;
        }
        if (Main.DenseDropEnable == null || !Main.DenseDropEnable) {
            return;
        }
        Started = true;
        int interval = Main.DenseDropScanInterval == null || Main.DenseDropScanInterval < 1
                ? 10 : Main.DenseDropScanInterval;
        long ticks = interval * 20L;
        Bukkit.getScheduler().runTaskTimer(Main.plugin, DenseDrop::scan, ticks, ticks);
    }

    /** 全量统计一次各世界的掉落物分布，并逐个区块判定 */
    public static void scan() {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Location> samples = new HashMap<>();

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Item)) {
                    continue;
                }
                Location location = entity.getLocation();
                if (location.getWorld() == null) {
                    continue;
                }
                String key = chunkKey(location.getWorld().getName(),
                        toChunk(location.getX()), toChunk(location.getZ()));
                Integer previous = counts.get(key);
                counts.put(key, previous == null ? 1 : previous + 1);
                samples.put(key, location);
            }
        }

        Counts.clear();
        Counts.putAll(counts);
        Samples.clear();
        Samples.putAll(samples);
        FastCounts.clear();
        FastCounts.putAll(counts);
        LastVerified.clear();
        LastVerified.putAll(counts);

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            Location sample = samples.get(entry.getKey());
            if (sample == null || sample.getWorld() == null) {
                continue;
            }
            evaluate(sample.getWorld(), toChunk(sample.getX()), toChunk(sample.getZ()), entry.getValue(), sample, null);
        }
    }

    /** 掉落事件入口：只做常数开销的计数，达到阈值时下一 tick 复核一次 */
    public static void onItemSpawn(Item item) {
        if (item == null || item.getLocation() == null || item.getLocation().getWorld() == null) {
            return;
        }
        int trigger = triggerCount();
        if (trigger == Integer.MAX_VALUE) {
            return;
        }

        Location location = item.getLocation();
        World world = location.getWorld();
        int chunkX = toChunk(location.getX());
        int chunkZ = toChunk(location.getZ());
        String key = chunkKey(world.getName(), chunkX, chunkZ);

        Integer previous = FastCounts.get(key);
        int current = (previous == null ? 0 : previous) + 1;
        FastCounts.put(key, current);

        Integer verified = LastVerified.get(key);
        if (!needsVerify(current, verified == null ? 0 : verified, trigger)) {
            return;
        }
        LastVerified.put(key, current);
        Bukkit.getScheduler().runTask(Main.plugin, () -> verify(world, chunkX, chunkZ, item));
    }

    /** 两个清理阈值中较小的那个；都未开启时返回 Integer.MAX_VALUE */
    private static int triggerCount() {
        int trigger = Integer.MAX_VALUE;
        int threshold = Main.DenseDropThreshold == null ? 0 : Main.DenseDropThreshold;
        int autoClear = Main.ClearItemChunkMaxItems == null ? 0 : Main.ClearItemChunkMaxItems;
        if (threshold > 0) {
            trigger = Math.min(trigger, threshold);
        }
        if (autoClear > 0) {
            trigger = Math.min(trigger, autoClear);
        }
        return trigger;
    }

    /** 复核单个区块的实际掉落物数量，避免快速计数漂移造成误报 */
    public static void verify(World world, int chunkX, int chunkZ, Item trigger) {
        if (world == null) {
            return;
        }
        int count = 0;
        Location sample = null;
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Item)) {
                continue;
            }
            Location location = entity.getLocation();
            if (location.getWorld() == null || !location.getWorld().getName().equals(world.getName())) {
                continue;
            }
            if (toChunk(location.getX()) != chunkX || toChunk(location.getZ()) != chunkZ) {
                continue;
            }
            ++count;
            if (sample == null) {
                sample = location;
            }
        }

        String key = chunkKey(world.getName(), chunkX, chunkZ);
        FastCounts.put(key, count);
        LastVerified.put(key, count);
        Counts.put(key, count);
        if (sample == null) {
            Samples.remove(key);
            return;
        }
        Samples.put(key, sample);
        evaluate(world, chunkX, chunkZ, count, sample, trigger);
    }

    private static void evaluate(World world, int chunkX, int chunkZ, int count, Location sample, Item trigger) {
        if (world == null || sample == null || count <= 0) {
            return;
        }

        int autoClear = Main.ClearItemChunkMaxItems == null ? 0 : Main.ClearItemChunkMaxItems;
        if (autoClear > 0 && count >= autoClear) {
            clearNearby(trigger, world, chunkX, chunkZ);
            resetCounters(world, chunkX, chunkZ);
            broadcastChunkMaxItems(world, sample, count);
            return;
        }

        int threshold = Main.DenseDropThreshold == null ? 0 : Main.DenseDropThreshold;
        if (!shouldAlert(count, threshold)) {
            return;
        }
        String key = chunkKey(world.getName(), chunkX, chunkZ);
        long now = System.currentTimeMillis();
        Long last = LastAlert.get(key);
        int cooldown = Main.DenseDropAlertCooldown == null ? 0 : Main.DenseDropAlertCooldown;
        if (isCoolingDown(now, last == null ? 0L : last, cooldown)) {
            return;
        }
        LastAlert.put(key, now);
        alert(world, chunkX, chunkZ, count, sample);
    }

    /**
     * 沿用旧的即时清理：以触发的掉落物为中心，清理 16*32*16 区域内的实体。
     * 触发实体已失效（例如刚被捡起）时改为按区块清理掉落物。
     */
    private static void clearNearby(Item trigger, World world, int chunkX, int chunkZ) {
        if (trigger != null && trigger.isValid()) {
            List<Entity> entities = trigger.getNearbyEntities(16.0, 32.0, 16.0);
            for (Entity entity : entities) {
                tools.clearEntityItem(entity);
            }
            return;
        }
        for (Entity entity : world.getEntities()) {
            Location location = entity.getLocation();
            if (location.getWorld() == null || !location.getWorld().getName().equals(world.getName())) {
                continue;
            }
            if (toChunk(location.getX()) != chunkX || toChunk(location.getZ()) != chunkZ) {
                continue;
            }
            tools.clearEntityItem(entity);
        }
    }

    /** 清理后把该区块的计数归零，避免下一次统计重复提醒 */
    private static void resetCounters(World world, int chunkX, int chunkZ) {
        String key = chunkKey(world.getName(), chunkX, chunkZ);
        Counts.put(key, 0);
        FastCounts.put(key, 0);
        LastVerified.put(key, 0);
        Samples.remove(key);
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
                .replace("%players%", nearbyPlayers(world, x, y, z,
                        Main.DenseDropNearbyRadius == null ? 32 : Main.DenseDropNearbyRadius));

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

    /**
     * 清理指定区块的掉落物。
     *
     * @param toDustbin     是否把物品放进公共垃圾箱（管理员手动清理时为 true）
     * @param respectMinAge 是否保护未满最短存活时间的掉落物（定时自动清理时为 true）
     */
    public static Result purge(World world, int chunkX, int chunkZ, boolean toDustbin, boolean respectMinAge) {
        Result result = new Result();
        if (world == null) {
            return result;
        }
        int minAge = Main.ClearItemMinAge == null ? 0 : Main.ClearItemMinAge;
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Item)) {
                continue;
            }
            Item item = (Item) entity;
            Location location = item.getLocation();
            if (location.getWorld() == null || !location.getWorld().getName().equals(world.getName())) {
                continue;
            }
            if (toChunk(location.getX()) != chunkX || toChunk(location.getZ()) != chunkZ) {
                continue;
            }
            if (respectMinAge && tools.isTooYoungToClear(item.getTicksLived(), minAge)) {
                continue;
            }
            if (item.getItemStack() == null || item.getItemStack().getType() == Material.AIR
                    || item.getItemStack().getAmount() <= 0) {
                ++result.removed;
                item.remove();
                continue;
            }
            String type = item.getItemStack().getType().name();
            if (Main.ClearItemWhiteList != null && Main.ClearItemWhiteList.contains(type)) {
                continue;
            }
            if (toDustbin && (Main.BlockBlackList == null || !Main.BlockBlackList.contains(type))
                    && Dustbin.addItem(item.getItemStack())) {
                ++result.dustbin;
            }
            ++result.removed;
            item.remove();
        }
        String key = chunkKey(world.getName(), chunkX, chunkZ);
        Counts.put(key, 0);
        FastCounts.put(key, 0);
        LastVerified.put(key, 0);
        return result;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** 一次清理的结果 */
    public static final class Result {
        /** 被移除的掉落物数量 */
        public int removed;
        /** 其中放进公共垃圾箱的数量 */
        public int dustbin;
    }
}
