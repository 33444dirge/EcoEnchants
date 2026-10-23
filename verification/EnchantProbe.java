package verification;

import com.willfp.eco.core.display.*;
import com.willfp.eco.core.fast.FastItemStack;
import com.willfp.ecoenchants.EcoEnchantsPlugin;
import com.willfp.ecoenchants.display.EnchantDisplay;
import com.willfp.ecoenchants.enchant.*;
import com.willfp.ecoenchants.target.*;
import com.willfp.libreforge.slot.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import java.lang.invoke.*;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.management.ThreadMXBean;

/** Isolated oracle and allocation checks; never shipped inside EcoEnchants. */
public final class EnchantProbe extends JavaPlugin {
    EcoEnchantsPlugin plugin;
    DisplayModule oldDisplay;
    Object oldTargets, oldFinder;
    MethodHandle oldEnchantable, newEnchantable, oldMax, newMax, oldFind, newFind, oldSlot, newSlot;
    List<EcoEnchant> custom;
    volatile Object sink;
    final AtomicInteger checks = new AtomicInteger();
    public void onEnable() {
        try {
            plugin = (EcoEnchantsPlugin) Bukkit.getPluginManager().getPlugin("EcoEnchants");
            ClassLoader parent = EnchantDisplay.class.getClassLoader();
            var loader = new URLClassLoader(new URL[]{new java.io.File(System.getProperty("enchants.baseline")).toURI().toURL()}, parent) {
                protected Class<?> loadClass(String n, boolean resolve) throws ClassNotFoundException {
                    if (!(n.startsWith("com.willfp.ecoenchants.target.EnchantmentTargets") ||
                          n.startsWith("com.willfp.ecoenchants.target.EnchantFinder") ||
                          n.equals("com.willfp.ecoenchants.target.ConfiguredEnchantmentTarget") ||
                          n.equals("com.willfp.ecoenchants.display.EnchantDisplay"))) return super.loadClass(n, resolve);
                    synchronized(getClassLoadingLock(n)) {
                        Class<?> c=findLoadedClass(n); if(c==null)c=findClass(n);if(resolve)resolveClass(c);return c;
                    }
                }
            };
            Class<?> t=loader.loadClass(EnchantmentTargets.class.getName()); oldTargets=t.getField("INSTANCE").get(null);
            Class<?> f=loader.loadClass(EnchantFinder.class.getName()); oldFinder=f.getField("INSTANCE").get(null);
            oldDisplay=(DisplayModule)loader.loadClass(EnchantDisplay.class.getName()).getField("INSTANCE").get(null);
            var lookup=MethodHandles.publicLookup();
            var bool=MethodType.methodType(boolean.class,ItemStack.class); var integer=MethodType.methodType(int.class,ItemStack.class);
            var list=MethodType.methodType(List.class,ItemStack.class); var slot=MethodType.methodType(boolean.class,EcoEnchantLevel.class,SlotType.class);
            oldEnchantable=lookup.findVirtual(t,"isEnchantable",bool).bindTo(oldTargets);
            newEnchantable=lookup.findVirtual(EnchantmentTargets.class,"isEnchantable",bool).bindTo(EnchantmentTargets.INSTANCE);
            oldMax=lookup.findVirtual(t,"getMaxEnchants",integer).bindTo(oldTargets);
            newMax=lookup.findVirtual(EnchantmentTargets.class,"getMaxEnchants",integer).bindTo(EnchantmentTargets.INSTANCE);
            oldFind=lookup.findVirtual(f,"find",list).bindTo(oldFinder);
            newFind=lookup.findVirtual(EnchantFinder.class,"find",list).bindTo(EnchantFinder.INSTANCE);
            oldSlot=lookup.findVirtual(f,"isValidInSlot",slot).bindTo(oldFinder);
            newSlot=lookup.findVirtual(EnchantFinder.class,"isValidInSlot",slot).bindTo(EnchantFinder.INSTANCE);
            custom=new ArrayList<>();for(Enchantment e: Registry.ENCHANTMENT)if(e instanceof EcoEnchant x)custom.add(x);
            var ruleClasses=new TreeMap<String,Integer>();for(var target:EnchantmentTargets.INSTANCE.values())if(!target.getId().equals("all"))for(var rule:target.getItems())ruleClasses.merge(rule.getClass().getName(),1,Integer::sum);
            getLogger().info("[EE] RULE_CLASSES "+ruleClasses);
            getLogger().info("[EE] READY custom="+custom.size()+" targets="+EnchantmentTargets.INSTANCE.values().size());
        } catch(Throwable e){fail(e);}
    }
    void fail(Throwable e){getLogger().severe("[EE] FAIL "+e);e.printStackTrace();}
    void check(boolean b,String m){if(!b)throw new AssertionError(m);checks.incrementAndGet();}
    public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(sender instanceof Player p)p.getScheduler().run(this,task->{try{
            check(Bukkit.isOwnedByCurrentRegion(p),"player owner");
            if(args.length>0 && args[0].equals("bench")){
                // No real players/world objects cross this boundary: benchmark fixtures are detached item clones.
                var fixtures=fixtures();new Thread(()->{try{bench(fixtures);}catch(Throwable e){fail(e);}},"ee-allocation-platform").start();
            } else behavior(p);
        }catch(Throwable e){fail(e);}},()->fail(new IllegalStateException("player retired")));
        return true;
    }
    ItemStack item(Material m,boolean rich,int mode,boolean hidden,boolean craft){
        ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();
        if(meta!=null){
            if(rich){
                var lore=new ArrayList<String>();for(int n=0;n<24;n++)lore.add("Unrelated lore line "+n+" with component text");meta.setLore(lore);
                for(int n=0;n<16;n++)meta.getPersistentDataContainer().set(new NamespacedKey(this,"payload_"+n),PersistentDataType.STRING,"unrelated-data-"+n);
                meta.setCustomModelData(124);
            }
            if(hidden)meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            if((mode&1)!=0){meta.addEnchant(Enchantment.UNBREAKING,3,true);meta.addEnchant(Enchantment.MENDING,1,true);}
            if((mode&2)!=0){for(var e:custom.subList(0,Math.min(3,custom.size())))meta.addEnchant(e.getEnchantment(),Math.min(2,e.getMaximumLevel()),true);}
            if(meta instanceof EnchantmentStorageMeta s){
                s.addStoredEnchant(Enchantment.EFFICIENCY,4,true);
                if((mode&2)!=0 && !custom.isEmpty())s.addStoredEnchant(custom.getFirst().getEnchantment(),1,true);
            }
            i.setItemMeta(meta);
        }
        return craft?CraftItemStack.asCraftCopy(i):i;
    }
    List<String> levels(List<?> list){return list.stream().map(x->{var l=(EcoEnchantLevel)x;return l.getEnchant().getEnchantmentKey()+":"+l.getLevel();}).sorted().toList();}
    boolean same(ItemStack a,ItemStack b){return a.equals(b);}
    void compare(ItemStack base,Player player,String label)throws Throwable{
        boolean a=(boolean)oldEnchantable.invokeExact(base),b=(boolean)newEnchantable.invokeExact(base);check(a==b,"enchantable "+label);
        check((int)oldMax.invokeExact(base)==(int)newMax.invokeExact(base),"max "+label);
        List<?> oa=(List<?>)oldFind.invokeExact(base),nb=(List<?>)newFind.invokeExact(base);check(levels(oa).equals(levels(nb)),"find "+label);
        for(Object x:oa)for(SlotType s:SlotTypes.INSTANCE.values()){
            var level=(EcoEnchantLevel)x;check((boolean)oldSlot.invokeExact(level,s)==(boolean)newSlot.invokeExact(level,s),"slot "+label);
        }
        var x=base.clone();var y=base.clone();Object[] ax=oldDisplay.generateVarArgs(x),ay=EnchantDisplay.INSTANCE.generateVarArgs(y);
        check(Arrays.equals(ax,ay),"display args "+label);
        oldDisplay.display(x,player,new DisplayProperties(true,false,base),ax);
        EnchantDisplay.INSTANCE.display(y,player,new DisplayProperties(true,false,base),ay);
        check(same(x,y),"display output "+label+" old="+x+" new="+y);
        oldDisplay.revert(x);EnchantDisplay.INSTANCE.revert(y);check(same(x,y),"revert output "+label);
        // The framework removes generated lore before invoking module reverts; test the complete framework too.
        var full=Display.display(base.clone(),player);var reverted=Display.revert(full);
        check(base.getAmount()==reverted.getAmount() && base.getEnchantments().equals(reverted.getEnchantments()),"framework amount/enchant conservation "+label);
    }
    void behavior(Player p)throws Throwable{
        p.setGameMode(GameMode.CREATIVE);p.setInvulnerable(true);p.setGravity(false);
        p.setHealth(p.getName().equals("Enchant2")?4:20);
        check(custom.size()>100,"FX custom registrations present");
        var config=plugin.getConfigYml();String[] keys={"display.require-enchantable","display.collapse.enabled","display.descriptions.enabled","display.enchantments-below-lore","display.capacity.enabled"};
        Object[] saved=Arrays.stream(keys).map(config::get).toArray();
        try{
            int n=0;
            for(int setting=0;setting<8;setting++){
                config.set(keys[0],(setting&1)==0);config.set(keys[1],(setting&2)==0);config.set(keys[2],(setting&4)==0);
                config.set(keys[3],(setting&2)!=0);config.set(keys[4],(setting&4)==0);
                for(Material material:new Material[]{Material.DIAMOND_SWORD,Material.DIAMOND_CHESTPLATE,Material.ENCHANTED_BOOK,Material.BOOK,Material.STONE,Material.BRUSH})
                    for(boolean rich:new boolean[]{false,true})for(int mode=0;mode<4;mode++)for(boolean hidden:new boolean[]{false,true}){
                        var i=item(material,rich,mode,hidden,(n++&1)==0);compare(i,p,"matrix-"+setting+"-"+n);
                    }
            }
            // Null-player packet display and changing item content use the same real predicates.
            var i=item(Material.DIAMOND_SWORD,true,3,false,true);compare(i,null,"null-player");i.setType(Material.STONE);compare(i,null,"changed-material");
            i.setType(Material.BRUSH);var meta=i.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey(this,"changed"),PersistentDataType.INTEGER,1);i.setItemMeta(meta);compare(i,p,"changed-components");
            p.getInventory().setItemInMainHand(item(Material.DIAMOND_SWORD,true,3,false,true));
            check(!EnchantFinder.INSTANCE.find(p.getInventory().getItemInMainHand()).isEmpty(),"owner equipment custom effects found");
            p.getInventory().setItemInMainHand(new ItemStack(Material.STONE));check(EnchantFinder.INSTANCE.find(p.getInventory().getItemInMainHand()).isEmpty(),"owner equipment removed");
            for(int j=0;j<keys.length;j++)config.set(keys[j],saved[j]);
            for(var enchant:custom)for(int level:new int[]{1,enchant.getMaximumLevel()}){
                var each=item(Material.DIAMOND_SWORD,true,0,false,true);each.addUnsafeEnchantment(enchant.getEnchantment(),Math.max(1,level));
                compare(each,p,"registered-"+enchant.getEnchantmentKey()+"-"+level);
            }
            EnchantmentTargets.update();oldTargets.getClass().getMethod("update").invoke(null);
            compare(item(Material.BRUSH,true,3,false,true),p,"targets-reload");
            getLogger().info("[EE] BEHAVIOR_DONE player="+p.getName()+" checks="+checks.get()+" matrix="+n+" custom="+custom.size()+" health="+p.getHealth());
        }finally{for(int j=0;j<keys.length;j++)config.set(keys[j],saved[j]);}
    }
    record Fixture(String name,ItemStack item){}
    List<Fixture> fixtures(){
        return List.of(new Fixture("plain-sword",item(Material.DIAMOND_SWORD,false,0,false,true)),
            new Fixture("rich-sword",item(Material.DIAMOND_SWORD,true,0,false,true)),
            new Fixture("rich-mixed",item(Material.DIAMOND_SWORD,true,3,false,true)),
            new Fixture("rich-book",item(Material.ENCHANTED_BOOK,true,3,false,true)),
            new Fixture("rich-stone",item(Material.STONE,true,0,false,true)),
            new Fixture("bukkit-rich",item(Material.DIAMOND_SWORD,true,3,false,false)));
    }
    Object operation(String op,boolean old,ItemStack base,Object[] args,DisplayProperties props)throws Throwable{
        return switch(op){
            case "enchantable" -> (boolean)(old?oldEnchantable:newEnchantable).invokeExact(base);
            case "max" -> (int)(old?oldMax:newMax).invokeExact(base);
            case "find" -> (List<?>)(old?oldFind:newFind).invokeExact(base);
            case "display" -> {var i=base.clone();(old?oldDisplay:EnchantDisplay.INSTANCE).display(i,null,props,args);yield i;}
            case "revert" -> {var i=base.clone();(old?oldDisplay:EnchantDisplay.INSTANCE).revert(i);yield i;}
            default -> throw new AssertionError(op);
        };
    }
    void bench(List<Fixture> fixtures)throws Throwable{
        ThreadMXBean bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();check(!Thread.currentThread().isVirtual(),"platform allocation thread");
        bean.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().threadId();check(bean.getThreadAllocatedBytes(id)>=0,"allocation supported");
        for(Fixture fixture:fixtures)for(String op:List.of("enchantable","max","find","display","revert")){
            ItemStack base=fixture.item();Object[] args=EnchantDisplay.INSTANCE.generateVarArgs(base);var props=new DisplayProperties(true,false,base);
            if(op.equals("revert")){base=base.clone();EnchantDisplay.INSTANCE.display(base,null,props,args);}
            int count=op.equals("display")?500:2000;
            for(int round=-8;round<12;round++)for(boolean old:round%2==0?new boolean[]{true,false}:new boolean[]{false,true}){
                long bytes=bean.getThreadAllocatedBytes(id),time=System.nanoTime();
                for(int k=0;k<count;k++)sink=operation(op,old,base,args,props);
                time=System.nanoTime()-time;bytes=bean.getThreadAllocatedBytes(id)-bytes;
                if(round>=0)getLogger().info("[EE] METRIC fixture="+fixture.name()+" op="+op+" version="+(old?"old":"new")+" round="+round+" count="+count+" bytes="+bytes+" nanos="+time);
            }
        }
        getLogger().info("[EE] BENCH_DONE checks="+checks.get());
    }
}
