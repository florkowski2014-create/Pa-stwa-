package pl.azurite.panstwa;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class Listeners implements Listener {

    private final PanstwaPlugin plugin;

    public Listeners(PanstwaPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- helpery ----------

    private boolean canBuild(Player p, Store.Town owner) {
        return owner == null
                || owner.members.contains(p.getUniqueId())
                || p.hasPermission("panstwa.admin");
    }

    private boolean atWarWith(Player p, Store.Town owner) {
        Store.Town pt = plugin.store.townOf(p.getUniqueId());
        if (pt == null || owner == null || pt.nation == null || owner.nation == null) return false;
        return plugin.store.atWar(pt.nation, owner.nation);
    }

    private boolean isWarBanner(Material m) {
        return m == Material.RED_BANNER || m == Material.RED_WALL_BANNER;
    }

    private void deny(Player p) {
        p.sendMessage("§6[Państwa] §cTo jest teren innego miasta!");
    }

    private Player playerFrom(Entity damager) {
        if (damager instanceof Player pl) return pl;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player ps) return ps;
        return null;
    }

    // ---------- budowanie / niszczenie ----------

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();

        // niszczenie czerwonego banera - przerywa przejmowanie
        WarManager.Capture cap = plugin.war.captureAt(b);
        if (cap != null) {
            Store.Town pt = plugin.store.townOf(p.getUniqueId());
            boolean side = pt != null && pt.nation != null
                    && (pt.nation.equalsIgnoreCase(cap.attNation) || pt.nation.equalsIgnoreCase(cap.defNation));
            if (side) {
                e.setDropItems(false);
                plugin.war.stop(cap, "§6[Państwa] §a" + p.getName()
                        + " zniszczył czerwony baner! Przejmowanie chunka przerwane.");
            } else {
                e.setCancelled(true);
                deny(p);
            }
            return;
        }

        Store.Town owner = plugin.store.townAt(b.getChunk());
        if (!canBuild(p, owner)) {
            e.setCancelled(true);
            deny(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlockPlaced();
        Store.Town owner = plugin.store.townAt(b.getChunk());
        if (canBuild(p, owner)) return;

        // czerwony baner postawiony w chunku wroga podczas wojny = start przejmowania
        if (isWarBanner(b.getType()) && atWarWith(p, owner)) {
            if (plugin.war.captureIn(b.getChunk()) != null) {
                e.setCancelled(true);
                p.sendMessage("§6[Państwa] §cTen chunk jest już przejmowany.");
                return;
            }
            plugin.war.start(b, plugin.store.townOf(p.getUniqueId()), owner);
            return;
        }

        e.setCancelled(true);
        deny(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_BLOCK && a != Action.PHYSICAL) return;
        Block b = e.getClickedBlock();
        if (b == null) return;

        Player p = e.getPlayer();
        Store.Town owner = plugin.store.townAt(b.getChunk());
        if (canBuild(p, owner)) return;

        // pozwól postawić czerwony baner na zwykłym bloku podczas wojny
        ItemStack hand = e.getItem();
        if (a == Action.RIGHT_CLICK_BLOCK && hand != null && hand.getType() == Material.RED_BANNER
                && !b.getType().isInteractable() && atWarWith(p, owner)) {
            return;
        }

        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        Store.Town owner = plugin.store.townAt(e.getBlock().getChunk());
        if (!canBuild(e.getPlayer(), owner)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        Store.Town owner = plugin.store.townAt(e.getBlock().getChunk());
        if (!canBuild(e.getPlayer(), owner)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    // ---------- ramki, obrazy, armor standy ----------

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Entity en = e.getRightClicked();
        if (!(en instanceof Hanging) && !(en instanceof ArmorStand)) return;
        Store.Town owner = plugin.store.townAt(en.getLocation().getChunk());
        if (!canBuild(e.getPlayer(), owner)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        Store.Town owner = plugin.store.townAt(e.getRightClicked().getLocation().getChunk());
        if (!canBuild(e.getPlayer(), owner)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Store.Town owner = plugin.store.townAt(e.getEntity().getLocation().getChunk());
        if (owner == null) return;
        Player p = playerFrom(e.getRemover());
        if (p == null) {
            if (!owner.explosions) e.setCancelled(true);
        } else if (!canBuild(p, owner)) {
            e.setCancelled(true);
            deny(p);
        }
    }

    // ---------- PvP i zwierzęta ----------

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Player attacker = playerFrom(e.getDamager());
        if (attacker == null) return;
        Entity victim = e.getEntity();
        Store.Town owner = plugin.store.townAt(victim.getLocation().getChunk());
        if (owner == null) return;

        if (victim instanceof Player v) {
            if (attacker.equals(v) || owner.pvp) return;
            Store.Town at = plugin.store.townOf(attacker.getUniqueId());
            Store.Town vt = plugin.store.townOf(v.getUniqueId());
            // w czasie wojny gracze wrogich państw mogą się bić
            if (at != null && vt != null && plugin.store.atWar(at.nation, vt.nation)) return;
            e.setCancelled(true);
            attacker.sendMessage("§6[Państwa] §cPvP jest tu wyłączone.");
        } else if (!(victim instanceof Enemy) && !canBuild(attacker, owner)) {
            e.setCancelled(true);
            deny(attacker);
        }
    }

    // ---------- moby ----------

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof Enemy)) return;
        switch (e.getSpawnReason()) {
            case NATURAL, PATROL, RAID, REINFORCEMENTS -> {
                Store.Town t = plugin.store.townAt(e.getLocation().getChunk());
                if (t != null && !t.mobs) e.setCancelled(true);
            }
            default -> {
            }
        }
    }

    // ---------- ogień ----------

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        Store.Town t = plugin.store.townAt(e.getBlock().getChunk());
        if (t != null && !t.fire) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        Store.Town t = plugin.store.townAt(e.getBlock().getChunk());
        if (t == null) return;
        Player p = e.getPlayer();
        if (p != null) {
            if (!canBuild(p, t)) {
                e.setCancelled(true);
                deny(p);
            }
            return;
        }
        if (!t.fire) e.setCancelled(true);
    }

    // ---------- wybuchy ----------

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> {
            Store.Town t = plugin.store.townAt(b.getChunk());
            return t != null && !t.explosions;
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> {
            Store.Town t = plugin.store.townAt(b.getChunk());
            return t != null && !t.explosions;
        });
    }

    // ---------- tłoki i płyny z zewnątrz ----------

    private boolean crosses(Block piston, List<Block> blocks, BlockFace dir) {
        Store.Town po = plugin.store.townAt(piston.getChunk());
        for (Block b : blocks) {
            Block[] check = {b, b.getRelative(dir), b.getRelative(dir.getOppositeFace())};
            for (Block x : check) {
                Store.Town o = plugin.store.townAt(x.getChunk());
                if (o != null && o != po) return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (crosses(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (crosses(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        Block f = e.getBlock();
        Block t = e.getToBlock();
        if ((f.getX() >> 4) == (t.getX() >> 4) && (f.getZ() >> 4) == (t.getZ() >> 4)) return;
        Store.Town to = plugin.store.townAt(t.getChunk());
        if (to != null && to != plugin.store.townAt(f.getChunk())) e.setCancelled(true);
    }
}
