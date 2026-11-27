package com.mcsyr.clearitem;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class command implements TabExecutor {

  private static final Pattern pattern = Pattern.compile("^[-\\+]?[\\d]*$");
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
    if (sender instanceof Player) {
      if (args.length == 1) {
        Player player = (Player)sender;
        if(player.hasPermission("ClearItem.open")){
          if (args[0].equalsIgnoreCase("open")||args[0].equalsIgnoreCase("o")) {
            if (Main.PublicDustbinEnable) {
              ((Player)sender).openInventory(Dustbin.DustbinList.get(0));
              sender.sendMessage(Main.PublicDustbinAction + Main.PublicDustbinName);
            } else {
              sender.sendMessage( "公共垃圾箱已被服务器禁用!");
            }

            return true;
          }
        }

        if(player.hasPermission("ClearItem.share")) {
          if (args[0].equalsIgnoreCase("share") || args[0].equalsIgnoreCase("s")) {
            if (Main.ShareEnable) {
              ((Player) sender).openInventory(Share.ShareList.get(0));
              sender.sendMessage(Main.ShareAction + Main.ShareName);
            } else {
              sender.sendMessage("共享空间已被服务器禁用!");
            }
            return true;
          }
        }

        if(player.hasPermission("ClearItem.drop")) {
          if (args[0].equalsIgnoreCase("drop")) {
            if (Main.DropEnable) {
              if (Main.PlayerDropLock.get(player)) {
                Main.PlayerDropLock.put(player, false);
                Main.PlayerDropLockTime.put(player, new Date());
                player.sendMessage(Main.DropMessageClose);
              } else {
                Main.PlayerDropLock.put(player, true);
                player.sendMessage(Main.DropMessageOpen);
              }
            } else {
              sender.sendMessage("§c防丢弃功能服务器已禁用");
            }
            return true;
          }
        }

        if(player.hasPermission("ClearItem.discard")) {
          if (args[0].equalsIgnoreCase("discard") || args[0].equalsIgnoreCase("d")) {
            if (Main.PrivateDustbinEnable) {
              player.openInventory(Main.PlayerPrivateDustbin.get(player));
              sender.sendMessage("打开了" + Main.PrivateDustbinName);
            } else {
              sender.sendMessage("私人垃圾箱已被服务器禁用!");
            }

            return true;
          }
        }

        if(player.hasPermission("ClearItem.help")){
          if((args[0].equalsIgnoreCase("help"))){
            this.showHelp(sender);
            return true;
          }
        }

        if(player.hasPermission("ClearItem.Op.Command.reload")) {
          if (args[0].equalsIgnoreCase("reload")) {
            Main.loadConfig();
            DenseDrop.start();
            sender.sendMessage("§b[ClearItem] §f插件重载成功!");
            return true;
          }
        }

        if(player.hasPermission("ClearItem.Op.Command.type")) {
          if (args[0].equalsIgnoreCase("type")) {
            sender.sendMessage("§b[ClearItem] §fType: " + player.getItemInHand().getType().name());
            return true;
          }
        }

        if(player.hasPermission("ClearItem.Op.Command.PublicClear")) {
          if (args[0].equalsIgnoreCase("PublicClear")||args[0].equalsIgnoreCase("PClear")) {
            sender.sendMessage("§b[ClearItem] §f世界清理中");
            tools.clearWorld();
            return true;
          }
        }

        if(player.hasPermission("ClearItem.Op.Command.PublicClean")) {
          if (args[0].equalsIgnoreCase("PublicClean")||args[0].equalsIgnoreCase("PClean")) {
            sender.sendMessage("§b[ClearItem] §f公共垃圾桶清理中");
            tools.cleanPublicDustbin();
            Dustbin.page();
            return true;
          }
        }

        if(player.hasPermission("ClearItem.Op.Command.ShareClean")) {
          if (args[0].equalsIgnoreCase("ShareClean")||args[0].equalsIgnoreCase("SClean")) {
            sender.sendMessage("§b[ClearItem] §f共享空间清理中");
            tools.cleanShareInv();
            Dustbin.page();
            return true;
          }
        }
      } else if (args.length == 5 && args[0].equalsIgnoreCase("tp")) {
        return handleTeleport((Player) sender, args);
      } else if (args.length == 4 && args[0].equalsIgnoreCase("purge")) {
        return handlePurge((Player) sender, args);
      }else if(args.length==2){
        Player player = (Player)sender;
        if(player.hasPermission("ClearItem.Open")){
          if ((args[0].equalsIgnoreCase("open")||args[0].equalsIgnoreCase("o"))&&Main.PublicDustbinEnable) {
            if(pattern.matcher(args[1]).matches()){

              player.openInventory(Dustbin.DustbinList.get((Integer.parseInt(args[1])-1)%5));
              sender.sendMessage(Main.PublicDustbinAction + Main.PublicDustbinName);
            }else {
              sender.sendMessage( "错误的参数!");
            }

            return true;
          }
        }

        if(player.hasPermission("ClearItem.share")){
          if ((args[0].equalsIgnoreCase("share")||args[0].equalsIgnoreCase("o"))&&Main.ShareEnable) {
            if(pattern.matcher(args[1]).matches()){

              player.openInventory(Share.ShareList.get((Integer.parseInt(args[1])-1)%5));
              sender.sendMessage(Main.ShareAction + Main.ShareName);
            }else {
              sender.sendMessage( "错误的参数!");
            }

            return true;
          }
        }

      }

    } else if (sender.isOp()||sender.hasPermission("ClearItem.Op.Command.reload")) {
      if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
        Main.loadConfig();
        sender.sendMessage("§b[ClearItem] §f插件重载成功!");
        return true;
      }
    }

    return false;
  }


  /**
   * 传送指令：/citem tp &lt;世界&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;
   * 只由密集掉落物提醒里的悬浮窗触发，需要管理员权限。
   */
  private boolean handleTeleport(Player player, String[] args) {
    if (!player.hasPermission("ClearItem.Op.Command.DenseDrop")) {
      player.sendMessage(Main.DenseDropMessageNoPermission);
      return true;
    }

    World world = Bukkit.getWorld(args[1]);
    if (world == null) {
      player.sendMessage(Main.DenseDropMessageBadTarget);
      return true;
    }

    Location target;
    try {
      target = new Location(world,
              Double.parseDouble(args[2]),
              Double.parseDouble(args[3]),
              Double.parseDouble(args[4]));
    } catch (NumberFormatException exception) {
      player.sendMessage(Main.DenseDropMessageBadTarget);
      return true;
    }

    player.teleport(target);
    player.sendMessage(Main.DenseDropMessageTpSuccess);
    return true;
  }

  /**
   * 清理指令：/citem purge &lt;世界&gt; &lt;区块X&gt; &lt;区块Z&gt;
   * 清理该区块的全部掉落物并放入公共垃圾箱，需要管理员权限。
   */
  private boolean handlePurge(Player player, String[] args) {
    if (!player.hasPermission("ClearItem.Op.Command.DenseDrop")) {
      player.sendMessage(Main.DenseDropMessageNoPermission);
      return true;
    }

    World world = Bukkit.getWorld(args[1]);
    if (world == null) {
      player.sendMessage(Main.DenseDropMessageBadTarget);
      return true;
    }

    int chunkX;
    int chunkZ;
    try {
      chunkX = Integer.parseInt(args[2]);
      chunkZ = Integer.parseInt(args[3]);
    } catch (NumberFormatException exception) {
      player.sendMessage(Main.DenseDropMessageBadTarget);
      return true;
    }

    DenseDrop.Result result = DenseDrop.purge(world, chunkX, chunkZ, true, false);
    String message = Main.DenseDropMessagePurgeResult
            .replace("%count%", String.valueOf(result.removed))
            .replace("%dustbin%", String.valueOf(result.dustbin))
            .replace("%world%", tools.IncludeWorldAlias(world.getName()))
            .replace("%chunkX%", String.valueOf(chunkX))
            .replace("%chunkZ%", String.valueOf(chunkZ));
    player.sendMessage(message);
    return true;
  }

  private void showHelp(CommandSender sender) {
    sender.sendMessage("§7======================" + Main.PublicDustbinName + "§7======================");
    sender.sendMessage("§7");
    sender.sendMessage(Main.PublicDustbinName + " §f打开公共垃圾箱");
    sender.sendMessage(Main.PublicDustbinName + " §f§l/citem open | o +可选数字参数");
    sender.sendMessage("§7");
    sender.sendMessage(Main.PublicDustbinName + " §f打开共享空间");
    sender.sendMessage(Main.PublicDustbinName + " §f§l/citem share | s +可选数字参数");
    sender.sendMessage("§7");
    sender.sendMessage(Main.PublicDustbinName + " §f打开私人垃圾箱");
    sender.sendMessage(Main.PublicDustbinName + " §f§l/citem discard | d");
    sender.sendMessage("§7");
    sender.sendMessage(Main.PublicDustbinName + " §f切换防丢弃模式");
    sender.sendMessage(Main.PublicDustbinName + " §f§l/citem drop");
    sender.sendMessage("§7");
    if (sender.isOp()) {
      sender.sendMessage(Main.PublicDustbinName + " §f查询手上物品的Type");
      sender.sendMessage(Main.PublicDustbinName + " §f§l/citem type");
      sender.sendMessage("§7");
      sender.sendMessage(Main.PublicDustbinName + " §f重载插件");
      sender.sendMessage(Main.PublicDustbinName + " §f§l/citem reload");
      sender.sendMessage("§7");
      sender.sendMessage(Main.PublicDustbinName + " §f手动清理公共垃圾桶");
      sender.sendMessage(Main.PublicDustbinName + " §f§l/citem PublicClear | PClear");
      sender.sendMessage("§7");
      sender.sendMessage(Main.PublicDustbinName + " §f清空公共垃圾桶");
      sender.sendMessage(Main.PublicDustbinName + " §f§l/citem PublicClean | PClean");
      sender.sendMessage("§7");
      sender.sendMessage(Main.PublicDustbinName + " §f清空共享空间");
      sender.sendMessage(Main.PublicDustbinName + " §f§l/citem ShareClean | SClean");
      sender.sendMessage("§7");
      sender.sendMessage(Main.PublicDustbinName + " §f密集掉落物提醒里的传送与清理按钮");
      sender.sendMessage(Main.PublicDustbinName + " §f§l需要权限 ClearItem.Op.Command.DenseDrop");
    }

    sender.sendMessage("§7======================§b" + Main.Version + "§7======================");
  }

  public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
    if(args.length==1){
      List<String> TabList = new ArrayList<>(Main.Arg1_TabCommand);
      if(sender.isOp())
        TabList.addAll(Main.Arg1_Op_TabCommand);
      TabList.removeIf(s -> !s.toLowerCase().startsWith(args[0].toLowerCase()));
      return TabList;
    }else {
      return null;
    }

  }
}
