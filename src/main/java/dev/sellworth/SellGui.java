package dev.sellworth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class SellGui implements CommandExecutor, Listener {
    private static final int SELL_SLOT = 53; // bottom right

    private static class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private final SellWorthPlugin plugin;
    private final PriceManager prices;
    private final WorthLore lore;

    public SellGui(SellWorthPlugin plugin, PriceManager prices, WorthLore lore) {
        this.plugin = plugin;
        this.prices = prices;
        this.lore = lore;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54, Component.text(Text.smallCaps("place items here to sell")));
        h.inv = inv;
        inv.setItem(SELL_SLOT, sellButton());
        p.openInventory(inv);
        return true;
    }

    private ItemStack sellButton() {
        ItemStack b = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta m = b.getItemMeta();
        m.displayName(Component.text(Text.smallCaps("sell"), NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(java.util.List.of(Text.plain("click to sell all items", NamedTextColor.GRAY)));
        b.setItemMeta(m);
        return lore.skip(b);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        boolean inTop = e.getClickedInventory() == top;
        if (inTop && e.getSlot() == SELL_SLOT) {
            e.setCancelled(true);
            sell(p, top);
            return;
        }
        // stop the button pane from being merged into / pulled out
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || (e.isShiftClick() && e.getCurrentItem() != null
                    && e.getCurrentItem().getType() == Material.GREEN_STAINED_GLASS_PANE)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder && e.getRawSlots().contains(SELL_SLOT)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        if (!(inv.getHolder() instanceof Holder) || !(e.getPlayer() instanceof Player p)) return;
        for (int i = 0; i < SELL_SLOT; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            inv.setItem(i, null);
            for (ItemStack left : p.getInventory().addItem(it).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
    }

    private void sell(Player p, Inventory inv) {
        Economy eco = plugin.economy();
        if (eco == null) {
            p.sendMessage(Component.text("No economy plugin found", NamedTextColor.RED));
            return;
        }
        double total = 0;
        Set<Material> blocked = new LinkedHashSet<>();
        for (int i = 0; i < SELL_SLOT; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            PriceManager.Appraisal a = prices.appraise(it);
            if (a.blocker() != null) {
                blocked.add(a.blocker().getType());
                continue; // stays in the GUI, returned on close
            }
            total += a.value();
            inv.setItem(i, null);
        }
        total = Math.round(total * 100.0) / 100.0;

        for (Material m : blocked) Text.sendBoth(p, Text.unsellable(m));
        if (total > 0) {
            eco.depositPlayer(p, total);
            Text.sendBoth(p, Text.sold(total));
        }
    }
}
