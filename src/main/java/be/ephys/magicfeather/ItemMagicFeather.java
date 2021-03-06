package be.ephys.magicfeather;

import be.ephys.cookiecore.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.*;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;

import javax.annotation.Nullable;
import java.util.List;
import java.util.WeakHashMap;

public class ItemMagicFeather extends Item {

    public static final String NAME = "magicfeather";
    private static final WeakHashMap<PlayerEntity, MagicFeatherData> playerData = new WeakHashMap<>();

    @Config(name = "item.requires_curios", description = "If curios is installed, the magic feather will need to be installed in its charm slot to function.")
    @Config.BooleanDefault(value = true)
    public static ForgeConfigSpec.BooleanValue looseRequiresCurios;

    public ItemMagicFeather() {
        super(
          new Item.Properties()
            .maxStackSize(1)
            .group(ItemGroup.TRANSPORTATION)
        );

        setRegistryName(NAME);
    }

    public int getEntityLifespan(ItemStack itemStack, World world) {
        return Integer.MAX_VALUE;
    }

    private static void setMayFly(PlayerEntity player, boolean mayFly) {

        if (player.abilities.allowFlying == mayFly) {
            return;
        }

        player.abilities.allowFlying = mayFly;
        player.sendPlayerAbilities();
    }

    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    private static boolean requiresCurios() {
        return isCuriosInstalled() && looseRequiresCurios.get();
    }

    private static boolean isCuriosInstalled() {
        return ModList.get().isLoaded("curios");
    }

    private static boolean isCuriosEquipped(PlayerEntity player, Item item) {
        return CuriosApi.getCuriosHelper().findEquippedCurio(item, player).isPresent();
    }

    private static boolean hasItem(PlayerEntity player, Item item) {
        if (isCuriosInstalled()) {
            if (isCuriosEquipped(player, item)) {
                return true;
            }

            // if requireCurios is false, we'll check the main inventory
            if (looseRequiresCurios.get()) {
                return false;
            }
        }

        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (item.equals(stack.getItem())) {
                return true;
            }
        }

        return false;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);

        PlayerEntity player = Minecraft.getInstance().player;
        if (player != null) {
            if (requiresCurios() && !isCuriosEquipped(player, ModItems.magicFeather)) {
                tooltip.add(
                  new TranslationTextComponent(getTranslationKey(stack) + ".tooltip.requires_curios")
                    .func_230530_a_(Style.field_240709_b_.func_240712_a_(TextFormatting.GRAY))
                );
            }

            if (!BeaconRangeCalculator.isInBeaconRange(player)) {
                tooltip.add(
                  new TranslationTextComponent(getTranslationKey(stack) + ".tooltip.out_of_beacon_range")
                    .func_230530_a_(Style.field_240709_b_.func_240712_a_(TextFormatting.GRAY))
                );
            }
        }
    }

    public Entity createEntity(World world, Entity entity, ItemStack itemstack) {
        entity.setInvulnerable(true);

        return null;
    }

    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side != LogicalSide.SERVER) {
            return;
        }

        PlayerEntity player = event.player;

        MagicFeatherData data = ItemMagicFeather.playerData.get(player);
        // if the player instance changes, we have to rebuild this.
        if (data == null || data.player != player) {
            data = new MagicFeatherData(player);
            ItemMagicFeather.playerData.put(player, data);
        }

        data.onTick();
    }

    private static class MagicFeatherData {
        private final PlayerEntity player;
        private boolean isSoftLanding = false;
        private boolean wasGrantedFlight = false;

        private int checkTick = 0;
        private boolean beaconInRangeCache;

        public MagicFeatherData(PlayerEntity player) {
            this.player = player;
            this.beaconInRangeCache = player.abilities.allowFlying;
        }

        public void onTick() {
            if (player.isSpectator()) {
                return;
            }

            boolean hasItem = hasItem(player, ModItems.magicFeather);
            boolean mayFly = player.abilities.isCreativeMode || (hasItem && checkBeaconInRange(player));

            if (mayFly) {
                setMayFly(player, true);
                isSoftLanding = false;
            } else {
                // we only remove the fly ability if we are the one who granted it.
                if (wasGrantedFlight) {
                    isSoftLanding = true;
                }
            }

            if (isSoftLanding) {
                if (this.softLand()) {
                    isSoftLanding = false;
                }
            }

            wasGrantedFlight = mayFly;
        }

        private boolean softLand() {
            // SOFT LANDING:
            // on item removal, we disable flying until the player hits the ground
            // and only then do we remove the creative flight ability

            // func_233570_aj_ = player.isOnGround()
            boolean isPlayerOnGround = player.func_233570_aj_() && player.fallDistance < 1F;

            if (isPlayerOnGround) {
                setMayFly(player, false);

                // softland complete
                return true;
            } else {
                if (player.abilities.isFlying) {
                    player.abilities.isFlying = false;
                    player.sendPlayerAbilities();
                }

                // softland in progress
                return false;
            }
        }

        private boolean checkBeaconInRange(PlayerEntity player) {

            if (checkTick++ % 40 != 0) {
                return beaconInRangeCache;
            }

            beaconInRangeCache = BeaconRangeCalculator.isInBeaconRange(player);

            return beaconInRangeCache;
        }
    }
}
