package dev.sellworth;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class SellWorthPlugin extends JavaPlugin {
    private PriceManager prices;

    @Override
    public void onEnable() {
        prices = new PriceManager(this);
        prices.load();

        WorthLore lore = new WorthLore(this, prices);
        lore.registerPackets(this);
        getServer().getPluginManager().registerEvents(lore, this);

        SellGui sell = new SellGui(this, prices, lore);
        getServer().getPluginManager().registerEvents(sell, this);
        getCommand("sell").setExecutor(sell);

        WorthGui worth = new WorthGui(prices, lore);
        getServer().getPluginManager().registerEvents(worth, this);
        getCommand("worth").setExecutor(worth);
        getCommand("worth").setTabCompleter(worth);

        getCommand("sellreload").setExecutor((sender, cmd, label, args) -> {
            prices.load();
            sender.sendMessage("§aprices.yml reloaded (" + prices.all().size() + " items).");
            return true;
        });
    }

    /** Looked up lazily so the economy plugin can register after us. */
    public Economy economy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }
}
