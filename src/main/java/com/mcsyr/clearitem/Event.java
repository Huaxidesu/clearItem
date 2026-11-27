package com.mcsyr.clearitem;

import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class Event implements Listener {

  public Event() {
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    tools.initPlayerData(event.getPlayer());
  }

  @EventHandler(
          priority = EventPriority.HIGHEST
  )
  public void onPlayerDropItem(PlayerDropItemEvent event) {
    if (Main.DropEnable && Main.PlayerDropLock.get(event.getPlayer())) {
      event.setCancelled(true);
      event.getPlayer().sendMessage(Main.DropMessageDiscardInOpen);
    }

  }

  /**
   * 掉落事件只做一次常数开销的计数，不做实体扫描。
   * 旧实现每次掉落都调用 getNearbyEntities 遍历周边实体，掉落物上万时本身就是卡服来源，
   * 现改为交给 DenseDrop：达到阈值时才复核一次，并负责提醒与清理。
   */
  @EventHandler
  public void onItemSpawn(ItemSpawnEvent event) {
    DenseDrop.onItemSpawn(event.getEntity());
  }

  @EventHandler(
          priority = EventPriority.HIGHEST
  )
  public void onInventoryClick(InventoryClickEvent event) {
    ItemStack itemStack = event.getCurrentItem();
    if (itemStack != null) {
      itemStack.getType();
      if (!"AIR".equals(itemStack.getType().name())) {
        String Title = event.getView().getTitle();
        if(event.getView().getTitle()!=null){
          if (Title.equals(Main.PublicDustbinName) && Main.DustbinLock) {
            event.getWhoClicked().sendMessage(Main.PublicDustbinName + "垃圾箱已被锁住，请稍等1秒后操作...");
            event.setCancelled(true);
          } else if (Main.DropEnable && Title.equals(Main.PrivateDustbinName)) {
            String name = Objects.requireNonNull(itemStack.getItemMeta()).getDisplayName();
            if (tools.isIncludedString(Main.PrivateDustbinWhiteListName, name)) {
              event.setCancelled(true);
            }

            if (itemStack.getItemMeta() == null) {
              return;
            }

            List<String> lores = itemStack.getItemMeta().getLore();
            if (lores == null) {
              return;
            }

            for (String lore : lores) {
              if (tools.isIncludedString(Main.PrivateDustbinWhiteListLore, lore)) {
                event.setCancelled(true);
                return;
              }
            }
          }
          else {
            onPageClick(event);
          }
        }
      }
    }
  }

  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    if(event.getView().getTitle()!=null) {
      if (Main.PrivateDustbinEnable && event.getView().getTitle().equals(Main.PrivateDustbinName)) {
        Player player = (Player) event.getPlayer();
        Inventory inventory = Main.PlayerPrivateDustbin.get(player);
        ItemStack[] itemStacks = inventory.getContents();
        int clear = 0;
        int preserve = 0;

        for (ItemStack itemStack : itemStacks) {
          if (itemStack != null) {
            if (Dustbin.addItem(itemStack)) {
              inventory.remove(itemStack);
              ++clear;
            } else {
              ++preserve;
            }
          }
        }

        if (clear > 0 || preserve > 0) {
          player.sendMessage(Main.PrivateDustbinMessageClear.replaceAll("%clear%", String.valueOf(clear)).replaceAll("%preserve%", String.valueOf(preserve)));
        }
      }
    }

  }

//  上下页(标题相同)
  public void onPageClick(InventoryClickEvent event){
    if(event.getView().getTitle().startsWith(Main.PublicDustbinName)){
      if(Objects.requireNonNull(event.getCurrentItem()).hasItemMeta() && Objects.requireNonNull(event.getCurrentItem().getItemMeta()).hasDisplayName()){
        if(event.getCurrentItem().getItemMeta().getDisplayName().equals(Main.PublicDustbinPrePageName)){
          event.setCancelled(true);
          int count=Integer.parseInt(event.getView().getTitle().substring(event.getView().getTitle().length()-2,event.getView().getTitle().length()-1))-1;
          if(count>0){
            count--;
          }else {
            count=Dustbin.DustbinList.size()-1;
          }
          Player player = (Player) event.getWhoClicked();
          player.closeInventory();
          player.openInventory(Dustbin.DustbinList.get(count));
        }else if(event.getCurrentItem().getItemMeta().getDisplayName().equals(Main.PublicDustbinNextPageName)){
          event.setCancelled(true);
          int count=Integer.parseInt(event.getView().getTitle().substring(event.getView().getTitle().length()-2,event.getView().getTitle().length()-1))-1;
          if(count<Dustbin.DustbinList.size()-1){
            count++;
          }else
            count=0;
          Player player = (Player) event.getWhoClicked();
          player.closeInventory();
          player.openInventory(Dustbin.DustbinList.get(count));
        }
      }
    }else if(event.getView().getTitle().startsWith(Main.ShareName)){
      if(Objects.requireNonNull(event.getCurrentItem()).hasItemMeta() && Objects.requireNonNull(event.getCurrentItem().getItemMeta()).hasDisplayName()){
        if(event.getCurrentItem().getItemMeta().getDisplayName().equals(Main.SharePre)){
          event.setCancelled(true);
          int count=Integer.parseInt(event.getView().getTitle().substring(event.getView().getTitle().length()-2,event.getView().getTitle().length()-1))-1;
          if(count>0){
            count--;
          }else {
            count=Share.ShareList.size()-1;
          }
          Player player = (Player) event.getWhoClicked();
          player.closeInventory();
          player.openInventory(Share.ShareList.get(count));
        }else if(event.getCurrentItem().getItemMeta().getDisplayName().equals(Main.ShareNext)){
          event.setCancelled(true);
          int count=Integer.parseInt(event.getView().getTitle().substring(event.getView().getTitle().length()-2,event.getView().getTitle().length()-1))-1;
          if(count<Share.ShareList.size()-1){
            count++;
          }else
            count=0;
          Player player = (Player) event.getWhoClicked();
          player.closeInventory();
          player.openInventory(Share.ShareList.get(count));
        }
      }
    }
  }
}
