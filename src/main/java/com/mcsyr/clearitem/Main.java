package com.mcsyr.clearitem;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Main extends JavaPlugin {

  public static Main plugin;
  
  public static FileConfiguration Config;
  
  public static Boolean DustbinLock = Boolean.FALSE;


  public static String PublicDustbinPrePageName;

  public static String PublicDustbinNextPageName;

  public static String PublicDustbinPrePageDes;

  public static String PublicDustbinNextPageDes;
  public static int DustbinCount = 0;
  
  public static Boolean PublicDustbinEnable;

  public static String PublicDustbinAction;

  public static String PublicDustbinName;
  
  public static Integer PublicDustbinSize;
  
  public static Integer PublicDustbinClearInterval;
  
  public static List<String> PublicDustbinWhiteListName;
  
  public static String PublicDustbinMessageReminder;

  public static String PublicDustbinMessageButton;

  public static String PublicDustbinMessageInfo;
  
  public static String PublicDustbinMessageClear;
  
  public static Boolean PrivateDustbinEnable;
  
  public static String PrivateDustbinName;
  
  public static Integer PrivateDustbinSize;
  
  public static List<String> PrivateDustbinWhiteListName;
  
  public static List<String> PrivateDustbinWhiteListLore;
  
  public static String PrivateDustbinMessageClear;
  
  public static Boolean DropEnable;
  
  public static Integer DropTime;
  
  public static String DropMessageOpen;
  
  public static String DropMessageClose;
  
  public static String DropMessageDiscardInOpen;
  
  public static Integer ClearItemTime;
  
  public static List<String> ClearItemWhiteList;

  public static List<String> BlockBlackList;
  public static Integer ClearItemChunkMaxItems;

  public static Integer ClearItemMinAge;
  
  public static Boolean ClearItemItemFrame;
  
  public static Boolean ClearItemBoat;
  
  public static Boolean ClearItemExpBall;
  
  public static Boolean ClearItemFallingBlock;
  
  public static Boolean ClearItemPainting;
  
  public static Boolean ClearItemMinecart;
  
  public static Boolean ClearItemArrow;
  
  public static Boolean ClearItemSnowball;
  
  public static String ClearItemMessageClearPre;
  
  public static String ClearItemMessageClearStart;
  
  public static String ClearItemMessageClear;
  
  public static String ClearItemMessageClearWorld;
  
  public static String ClearItemMessageClearChunkMaxItems;
  
  public static Boolean CleaningTipsEnable;

  public static Integer time = 0;
  
  public static Integer WasteTotal = 0;
  
  public static Integer DustbinClearFrequency = 0;
  
  public static Map<Player, Boolean> PlayerDropLock = new HashMap<>();
  
  public static Map<Player, Date> PlayerDropLockTime = new HashMap<>();
  
  public static Map<Player, Inventory> PlayerPrivateDustbin = new HashMap<>();

  public static Boolean ShareEnable;

  public static String ShareAction;

  public static String SharePre;

  public static String SharePreInfo;

  public static Integer ShareTime = 0;

  public static String ShareNext;

  public static String ShareNextInfo;

  public static String ShareName;

  public static Integer ShareSize;

  public static Integer ShareClearTime;

  public static String ShareClearMessagePre;

  public static String ShareClearMessageStart;

  public static String ShareClearMessageEnd;

  public static String ShareButtonMessage;

  public static String ShareButtonInfo;

  /** 密集掉落物检测：是否开启 */
  public static Boolean DenseDropEnable = Boolean.TRUE;

  /** 密集掉落物检测：单区块掉落物数量阈值 */
  public static Integer DenseDropThreshold = 1000;

  /** 密集掉落物检测：全量统计周期（秒） */
  public static Integer DenseDropScanInterval = 60;

  /** 密集掉落物检测：同一区块两次提醒的最小间隔（秒） */
  public static Integer DenseDropAlertCooldown = 180;

  /** 密集掉落物检测：提醒中统计附近玩家的半径（格） */
  public static Integer DenseDropNearbyRadius = 32;

  public static String DenseDropMessageAlert = "";

  public static String DenseDropMessageTpButton = "";

  public static String DenseDropMessageTpInfo = "";

  public static String DenseDropMessageCleanButton = "";

  public static String DenseDropMessageCleanInfo = "";

  public static String DenseDropMessageNoPermission = "";

  public static String DenseDropMessageNoNearby = "";

  public static String DenseDropMessageBadTarget = "";

  public static String DenseDropMessageTpSuccess = "";

  public static String DenseDropMessagePurgeResult = "";

  public static String Version = "3.4.0";

  public static List<String> Arg1_TabCommand= new ArrayList<>(Arrays.asList("open","share","discard","drop"));

  public static List<String> Arg1_Op_TabCommand= new ArrayList<>(Arrays.asList("type","reload","PublicClear","PublicClean","ShareClean"));
  public Main() {
  }

  public void onEnable() {
    plugin = this;
    this.saveDefaultConfig();
    Bukkit.getServer().getConsoleSender().sendMessage("§b[ClearItem] 异步清理垃圾 插件启动");
    Bukkit.getPluginManager().registerEvents(new Event(), this);
    Objects.requireNonNull(Bukkit.getPluginCommand("clearitem")).setExecutor(new command());
    Objects.requireNonNull(Bukkit.getPluginCommand("clearitem")).setTabCompleter(new command());
    loadConfig();
    tools.Scheduler();
    DenseDrop.start();
    tools.TraversePlayer();
  }

  public static void loadConfig() {
    plugin.reloadConfig();
    Config = plugin.getConfig();
    PublicDustbinEnable = Config.getBoolean("PublicDustbin.Enable");
    PublicDustbinPrePageName = Objects.requireNonNull(Config.getString("PublicDustbin.PrePageName")).replaceAll("&", "§");
    PublicDustbinPrePageDes = Objects.requireNonNull(Config.getString("PublicDustbin.PrePageDes")).replaceAll("&", "§");
    PublicDustbinNextPageName = Objects.requireNonNull(Config.getString("PublicDustbin.NextPageName")).replaceAll("&", "§");
    PublicDustbinNextPageDes = Objects.requireNonNull(Config.getString("PublicDustbin.NextPageDes")).replaceAll("&", "§");
    PublicDustbinAction = Objects.requireNonNull(Config.getString("PublicDustbin.Action")).replaceAll("&", "§");
    PublicDustbinName = Objects.requireNonNull(Config.getString("PublicDustbin.Name")).replaceAll("&", "§");
    PublicDustbinSize = Config.getInt("PublicDustbin.Size");
    PublicDustbinClearInterval = Config.getInt("PublicDustbin.ClearInterval");
    PublicDustbinWhiteListName = Config.getStringList("PublicDustbin.WhiteListName");
    PublicDustbinMessageReminder = Objects.requireNonNull(Config.getString("PublicDustbin.Message.Reminder")).replaceAll("&", "§");
    PublicDustbinMessageButton = Objects.requireNonNull(Config.getString("PublicDustbin.Message.Button")).replaceAll("&", "§");
    PublicDustbinMessageInfo = Objects.requireNonNull(Config.getString("PublicDustbin.Message.ButtonInfo")).replaceAll("&", "§");
    PublicDustbinMessageClear = Objects.requireNonNull(Config.getString("PublicDustbin.Message.Clear")).replaceAll("&", "§");
    PrivateDustbinEnable = Config.getBoolean("PrivateDustbin.Enable");
    PrivateDustbinName = Objects.requireNonNull(Config.getString("PrivateDustbin.Name")).replaceAll("&", "§");
    PrivateDustbinSize = Config.getInt("PrivateDustbin.Size");
    PrivateDustbinWhiteListName = Config.getStringList("PrivateDustbin.WhiteListName");
    PrivateDustbinWhiteListLore = Config.getStringList("PrivateDustbin.WhiteListLore");
    PrivateDustbinMessageClear = Objects.requireNonNull(Config.getString("PrivateDustbin.Message.Clear")).replaceAll("&", "§");
    DropEnable = Config.getBoolean("Drop.Enable");
    DropTime = Config.getInt("Drop.Time") * 1000;
    DropMessageOpen = Objects.requireNonNull(Config.getString("Drop.Message.Open")).replaceAll("&", "§");
    DropMessageClose = Objects.requireNonNull(Config.getString("Drop.Message.Close")).replaceAll("&", "§");
    DropMessageDiscardInOpen = Objects.requireNonNull(Config.getString("Drop.Message.DiscardInOpen")).replaceAll("&", "§");
    ClearItemTime = Config.getInt("ClearItem.Time");
    ClearItemChunkMaxItems = Config.getInt("ClearItem.ChunkMaxItems");
    ClearItemMinAge = Config.isSet("ClearItem.MinAge") ? Config.getInt("ClearItem.MinAge") : 60;
    ClearItemWhiteList = Config.getStringList("ClearItem.WhiteList");
    ClearItemItemFrame = Config.getBoolean("ClearItem.ItemFrame");
    ClearItemBoat = Config.getBoolean("ClearItem.Boat");
    ClearItemExpBall = Config.getBoolean("ClearItem.ExpBall");
    ClearItemFallingBlock = Config.getBoolean("ClearItem.FallingBlock");
    ClearItemPainting = Config.getBoolean("ClearItem.Painting");
    ClearItemMinecart = Config.getBoolean("ClearItem.Minecart");
    ClearItemArrow = Config.getBoolean("ClearItem.Arrow");
    ClearItemSnowball = Config.getBoolean("ClearItem.Snowball");
    ClearItemMessageClearPre = Objects.requireNonNull(Config.getString("ClearItem.Message.ClearPre")).replaceAll("&", "§");
    ClearItemMessageClearStart = Objects.requireNonNull(Config.getString("ClearItem.Message.ClearStart")).replaceAll("&", "§");
    ClearItemMessageClear = Objects.requireNonNull(Config.getString("ClearItem.Message.Clear")).replaceAll("&", "§");
    ClearItemMessageClearWorld = Objects.requireNonNull(Config.getString("ClearItem.Message.ClearWorld")).replaceAll("&", "§");
    ClearItemMessageClearChunkMaxItems = Objects.requireNonNull(Config.getString("ClearItem.Message.ClearChunkMaxItems")).replaceAll("&", "§");
    CleaningTipsEnable = Config.getBoolean("CleaningTips.Enable");
    BlockBlackList = Config.getStringList("ClearItem.BlockBlackList");
    ShareEnable = Config.getBoolean("ShareInv.Enable");
    ShareAction = Objects.requireNonNull(Config.getString("ShareInv.Action")).replaceAll("&", "§");
    ShareName= Objects.requireNonNull(Config.getString("ShareInv.Name")).replaceAll("&", "§");
    ShareSize=Config.getInt("ShareInv.Size");
    SharePre = Objects.requireNonNull(Config.getString("ShareInv.PrePageName")).replaceAll("&", "§");
    SharePreInfo = Objects.requireNonNull(Config.getString("ShareInv.PrePageDes")).replaceAll("&", "§");
    ShareNext= Objects.requireNonNull(Config.getString("ShareInv.NextPageName")).replaceAll("&", "§");
    ShareNextInfo = Objects.requireNonNull(Config.getString("ShareInv.NextPageDes")).replaceAll("&", "§");
    ShareClearTime = Config.getInt("ShareInv.ClearTime");
    ShareClearMessagePre= Objects.requireNonNull(Config.getString("ShareInv.Message.ClearMessagePre")).replaceAll("&", "§");
    ShareClearMessageStart= Objects.requireNonNull(Config.getString("ShareInv.Message.ClearMessageStart")).replaceAll("&", "§");
    ShareClearMessageEnd= Objects.requireNonNull(Config.getString("ShareInv.Message.ClearMessageEnd")).replaceAll("&", "§");
    ShareButtonMessage= Objects.requireNonNull(Config.getString("ShareInv.Message.ButtonMessage")).replaceAll("&", "§");
    ShareButtonInfo= Objects.requireNonNull(Config.getString("ShareInv.Message.ButtonInfo")).replaceAll("&", "§");
    loadDenseDropConfig();
  }

  /**
   * 读取密集掉落物检测的配置。
   * 旧版配置文件没有这一段，全部键缺失时使用内置默认值，保证升级后可直接启动。
   */
  private static void loadDenseDropConfig() {
    DenseDropEnable = !Config.isSet("DenseDrop.Enable") || Config.getBoolean("DenseDrop.Enable");
    DenseDropThreshold = Config.isSet("DenseDrop.Threshold") ? Config.getInt("DenseDrop.Threshold") : 1000;
    DenseDropScanInterval = Config.isSet("DenseDrop.ScanInterval") ? Config.getInt("DenseDrop.ScanInterval") : 60;
    DenseDropAlertCooldown = Config.isSet("DenseDrop.AlertCooldown") ? Config.getInt("DenseDrop.AlertCooldown") : 180;
    DenseDropNearbyRadius = Config.isSet("DenseDrop.NearbyRadius") ? Config.getInt("DenseDrop.NearbyRadius") : 32;
    DenseDropMessageAlert = denseDropMessage("DenseDrop.Message.Alert",
            "&4[警告] &b世界：%world% &e坐标：%X% %Y% %Z% &4附近掉落物达到 &e%count% &4件，附近玩家：&f%players%");
    DenseDropMessageTpButton = denseDropMessage("DenseDrop.Message.TpButton", " &b[&e点此传送&b]");
    DenseDropMessageTpInfo = denseDropMessage("DenseDrop.Message.TpInfo", "传送到该位置(仅管理员)");
    DenseDropMessageCleanButton = denseDropMessage("DenseDrop.Message.CleanButton", " &c[&e点此清理&c]");
    DenseDropMessageCleanInfo = denseDropMessage("DenseDrop.Message.CleanInfo", "清理该区块的掉落物并放进公共垃圾箱(仅管理员)");
    DenseDropMessageNoPermission = denseDropMessage("DenseDrop.Message.NoPermission", "&c权限不足，只有管理员可以执行该操作");
    DenseDropMessageNoNearby = denseDropMessage("DenseDrop.Message.NoNearby", "无");
    DenseDropMessageBadTarget = denseDropMessage("DenseDrop.Message.BadTarget", "&c目标位置无效");
    DenseDropMessageTpSuccess = denseDropMessage("DenseDrop.Message.TpSuccess", "&a已传送到目标位置");
    DenseDropMessagePurgeResult = denseDropMessage("DenseDrop.Message.PurgeResult",
            "&b[清理] &f已清理 &e%world% &f区块 &e%chunkX%,%chunkZ% &f的 &e%count% &f件掉落物，其中 &e%dustbin% &f件放入公共垃圾箱");
  }

  private static String denseDropMessage(String path, String fallback) {
    String value = Config.isSet(path) ? Config.getString(path) : fallback;
    return value == null ? "" : value.replaceAll("&", "§");
  }
}
