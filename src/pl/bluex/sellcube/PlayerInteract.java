package pl.bluex.sellcube;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.inventory.ItemStack;

import com.griefcraft.model.Protection;
import com.nijikokun.register.payment.Method;
import com.nijikokun.register.payment.Method.MethodAccount;
import com.nijikokun.register.payment.Methods;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

public class PlayerInteract extends PlayerListener {
	private SellCube plugin;
	
	public PlayerInteract(SellCube instance){
		plugin = instance;
	}
	
    @Override
	public void onPlayerInteract(PlayerInteractEvent event) {
		if(event.isCancelled()) return;
		Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        Player player = event.getPlayer();

        if (!(block.getState() instanceof Sign)) return;
        

    	/*Matcher ma = Pattern.compile("[0-9]*\\.?[0-9]+").matcher(sign.getLine(3));
    	if(!ma.find()) return;
		float price = Float.parseFloat(ma.group());*/

        try {
			ResultSet rs = plugin.getAd(block);
			rs = (rs.next()) ? rs : null;

	        if (action == Action.LEFT_CLICK_BLOCK) {
	    		if(plugin.newAdsRN.containsKey(player)) {
                    if(plugin.newAdsRN.get(player) != null)
                        addAction(player, block);
                    else
                        statusAction(player, block);
	    		}
                else if(plugin.newAdsID.containsKey(player)) {
                    copyAction(player, block);
                }
                else if(rs != null) { // if(znak_w_bazie)
					infoAction(player, block);
	        	}
	        }
	        else if (action == Action.RIGHT_CLICK_BLOCK) {
	        	buyAction(player, block);
                event.setCancelled(true);
	        }
        } catch (SQLException e) {
			plugin.severe("SQL exception: " + e.getMessage());
		}
    }

    protected void addAction(Player player, Block block) throws SQLException {
        ResultSet rs = plugin.getAd(block);
        String regionName = plugin.newAdsRN.get(player);
        float price = plugin.newAdsP.get(player);
        boolean lwc_pass = plugin.newAdsLWC.get(player);
        if(rs.next()) {
            player.sendMessage(ChatColor.RED + "Ten znak jest juz ogloszeniem");
        } else {
            plugin.addAd(player.getName(), regionName, price, block, lwc_pass);
            plugin.newAdsRN.remove(player);
            plugin.newAdsP.remove(player);
            plugin.newAdsLWC.remove(player);
            player.sendMessage(ChatColor.BLUE + "Ogloszenie utworzone");
        }
    }

    protected void infoAction(Player player, Block block) throws SQLException {
        ResultSet rs = plugin.getAd(block);
        if(rs.next() && rs.getBoolean("active")) {
            if(SellCube.checkPermission(player, "sellcube.sell", false))
                player.sendMessage(ChatColor.BLUE + "ID: " + ChatColor.DARK_AQUA + rs.getString("id"));
            String owner = rs.getString("owner");
            player.sendMessage(ChatColor.BLUE + "Sprzedajacy: " + plugin.getPlayerGroupColor(plugin.getServer().getPlayer(owner)) + owner);
            player.sendMessage(ChatColor.BLUE + "Cena: " + ChatColor.DARK_AQUA + rs.getString("price"));
        }
    }

    protected void copyAction(Player player, Block block) throws SQLException {
        ResultSet rs = plugin.getAd(block);
        if(rs.next()) {
            player.sendMessage(ChatColor.RED + "Ten znak jest juz ogloszeniem");
            return;
        }
        rs = plugin.getAd(plugin.newAdsID.get(player));
        rs.next();
        plugin.addAd(rs.getString("owner"), rs.getString("region"), rs.getFloat("price"), block, rs.getBoolean("lwc_pass"));
        player.sendMessage(ChatColor.BLUE + "Ogloszenie utworzone");
        plugin.newAdsID.remove(player);
    }
    
    protected void statusAction(Player player, Block block) throws SQLException {
        ResultSet rs = plugin.getAd(block);
        if(rs.next()) {
            player.sendMessage(ChatColor.RED + "Ten znak jest juz ogloszeniem");
            return;
        }
        String playerName = player.getName();
        plugin.addAd(player.getName(), null, 0, block, true, false);
        Sign sign = (Sign) block.getState();
        sign.setLine(0, "Gracz:");
        sign.setLine(1, plugin.getPlayerGroupColor(player) + playerName);
        sign.setLine(2, "Ostatnio byl:");
        sign.update(true);
        plugin.updateSign(sign, playerName);
        plugin.newAdsRN.remove(player);
        player.sendMessage(ChatColor.BLUE + "Informacja utworzona");
    }

    protected void buyAction(Player player, Block block) throws SQLException {
        ResultSet rs = plugin.getAd(block);
        if(rs.next() && rs.getBoolean("active") == true && SellCube.checkPermission(player, "sellcube.buy")) {
            String buyerName = player.getName();
            String sellerName = rs.getString("owner");
            String regionName = rs.getString("region");
            boolean active = rs.getBoolean("active");
            boolean lwc_pass = rs.getBoolean("lwc_pass");
            float price = Float.valueOf(rs.getString("price")).floatValue();

            // Check region
            RegionManager manager = SellCube.wg.getGlobalRegionManager().get(player.getWorld());
            ProtectedRegion region = manager.getRegion(regionName);
            if(active) {
                if(region == null || !(region.getOwners().getPlayers().contains(sellerName) ||
                        SellCube.checkPermission(plugin.getServer().getPlayer(sellerName), "sellcube.sell_all", false))) {
                    Protection protection = SellCube.lwc.findProtection(block);
                    if(protection != null) {
                        protection.remove();
                        protection.save();
                    }
                    plugin.removeAd(block);
                    block.setType(Material.AIR);
                    block.getWorld().dropItemNaturally(block.getLocation(),
                            new ItemStack(Material.SIGN, 1));
                    player.sendMessage(ChatColor.RED + "Ogloszenie nieaktualne");
                    return;
                }
            }

            // Check accounts
            Method m = Methods.getMethod();
            if(!m.hasAccount(buyerName) || !m.hasAccount(sellerName)) {
                player.sendMessage(ChatColor.RED + "Blad konta");
                return;
            }
            MethodAccount buyerMA = m.getAccount(buyerName);
            MethodAccount sellerMA = m.getAccount(sellerName);
            if(!buyerMA.hasEnough(price)) {
                player.sendMessage(ChatColor.RED + "Nie masz wystarczajacej liczby coinow");
                return;
            }

            // Transfer money
            buyerMA.subtract(price);
            sellerMA.add(price);
            player.sendMessage(ChatColor.GREEN + "Pobrano " +
                    ChatColor.DARK_AQUA + price +
                    ChatColor.GREEN + "c z twojego konta (stan " +
                    ChatColor.DARK_AQUA + buyerMA.balance() +
                    ChatColor.GREEN + "c)");
            Player seller = plugin.getServer().getPlayer(sellerName);
            if(seller != null) {
                seller.sendMessage(ChatColor.GREEN + "Przelano " +
                        ChatColor.DARK_AQUA + price +
                        ChatColor.GREEN + "c na twoje konto (stan " +
                        ChatColor.DARK_AQUA + sellerMA.balance() +
                        ChatColor.GREEN + "c)");
            }

            // Change region owner
            try {
                //region.getOwners().removePlayer(sellerName);
                Set<String> owners = region.getOwners().getPlayers();
                for(String s : owners)
                    region.getOwners().removePlayer(s);
                region.getOwners().addPlayer(buyerName);
                manager.save();
            } catch (IOException e) {
                plugin.warning("Region save error: " +  e.getMessage());
                return;
            }

            // Update sign
            Sign sign = (Sign) block.getState();
            sign.setLine(0, "Wlasciciel:");
            sign.setLine(1, plugin.getPlayerGroupColor(player) + buyerName);
            sign.setLine(2, "Ostatnio byl:");
            /*int n = regionName.length();
            sign.setLine(2, regionName.substring(0, (n > 15) ? 15 : n));*/
            sign.update(true);
            plugin.updateSign(sign, buyerName);
            plugin.deactivateAd(block, buyerName);

            // Change sign owner
            if(lwc_pass) {
                Protection protection = SellCube.lwc.findProtection(block);
                if(protection != null) {
                    protection.setOwner(buyerName);
                    protection.save();
                }
            }
        }
    }
}
