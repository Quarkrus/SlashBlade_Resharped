package mods.flammpfeil.slashblade.util;

import com.google.common.collect.Lists;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.ability.ArrowReflector;
import mods.flammpfeil.slashblade.ability.TNTExtinguisher;
import mods.flammpfeil.slashblade.capability.concentrationrank.ConcentrationRankCapabilityProvider;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.entity.IShootable;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;

import java.util.List;
import java.util.function.Consumer;

public class AttackManager {
    static public void areaAttack(LivingEntity playerIn, Consumer<LivingEntity> beforeHit) {
        areaAttack(playerIn, beforeHit, 1.0f, true, true, false);
    }

    static public EntitySlashEffect doSlash(LivingEntity playerIn, float roll) {
        return doSlash(playerIn, roll, false);
    }

    static public EntitySlashEffect doSlash(LivingEntity playerIn, float roll, boolean mute) {
        return doSlash(playerIn, roll, mute, false);
    }

    static public EntitySlashEffect doSlash(LivingEntity playerIn, float roll, boolean mute, boolean critical) {
        return doSlash(playerIn, roll, mute, critical, 1.0);
    }

    static public EntitySlashEffect doSlash(LivingEntity playerIn, float roll, boolean mute, boolean critical,
            double damage) {
        return doSlash(playerIn, roll, Vec3.ZERO, mute, critical, damage);
    }

    static public EntitySlashEffect doSlash(LivingEntity playerIn, float roll, Vec3 centerOffset, boolean mute,
            boolean critical, double damage) {
        return doSlash(playerIn, roll, centerOffset, mute, critical, damage, KnockBacks.cancel);
    }

    static public EntitySlashEffect doSlash(LivingEntity playerIn, float roll, Vec3 centerOffset, boolean mute,
            boolean critical, double damage, KnockBacks knockback) {

        int colorCode = playerIn.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> state.getColorCode()).orElseGet(() -> 0xFFFFFF);

        return doSlash(playerIn, roll, colorCode, centerOffset, mute, critical, damage, knockback);
    }

    static public EntitySlashEffect doSlash(LivingEntity playerIn, float roll, int colorCode, Vec3 centerOffset,
            boolean mute, boolean critical, double damage, KnockBacks knockback) {

        if (playerIn.level().isClientSide())
            return null;
        ItemStack blade = playerIn.getMainHandItem();
		if(!blade.getCapability(ItemSlashBlade.BLADESTATE).isPresent())
        	return null;
        if (MinecraftForge.EVENT_BUS.post(new SlashBladeEvent.DoSlashEvent(blade, 
        		blade.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(NullPointerException::new),
        		playerIn, roll, critical, damage, knockback)))
			return null;
        Vec3 pos = playerIn.position().add(0.0D, (double) playerIn.getEyeHeight() * 0.75D, 0.0D)
                .add(playerIn.getLookAngle().scale(0.3f));

        pos = pos.add(VectorHelper.getVectorForRotation(-90.0F, playerIn.getViewYRot(0)).scale(centerOffset.y))
                .add(VectorHelper.getVectorForRotation(0, playerIn.getViewYRot(0) + 90).scale(centerOffset.z))
                .add(playerIn.getLookAngle().scale(centerOffset.z));

        EntitySlashEffect jc = new EntitySlashEffect(SlashBlade.RegistryEvents.SlashEffect, playerIn.level());
        jc.setPos(pos.x, pos.y, pos.z);
        jc.setOwner(playerIn);
        jc.setRotationRoll(roll);
        jc.setYRot(playerIn.getYRot());
        jc.setXRot(0);

        jc.setColor(colorCode);

        jc.setMute(mute);
        jc.setIsCritical(critical);

        jc.setDamage(damage);

        jc.setKnockBack(knockback);

        if (playerIn != null)
            playerIn.getCapability(ConcentrationRankCapabilityProvider.RANK_POINT)
                    .ifPresent(rank -> jc.setRank(rank.getRankLevel(playerIn.level().getGameTime())));

        playerIn.level().addFreshEntity(jc);

        return jc;
    }

    public static void doVoidSlashAttack(LivingEntity living) {
        if (living.level().isClientSide())
            return;

        Vec3 pos = living.position().add(0.0D, (double) living.getEyeHeight() * 0.75D, 0.0D)
                .add(living.getLookAngle().scale(0.3f));

        pos = pos.add(VectorHelper.getVectorForRotation(-90.0F, living.getViewYRot(0)).scale(Vec3.ZERO.y))
                .add(VectorHelper.getVectorForRotation(0, living.getViewYRot(0) + 90).scale(Vec3.ZERO.z))
                .add(living.getLookAngle().scale(Vec3.ZERO.z));

        EntitySlashEffect jc = new EntitySlashEffect(SlashBlade.RegistryEvents.SlashEffect, living.level()) {

            @Override
            public double getDamage() {
                return 0;
            }

            @Override
            public SoundEvent getSlashSound() {
                return SoundEvents.BLAZE_HURT;
            }

            @Override
            protected void tryDespawn() {
                if (!this.level().isClientSide()) {
                    if (getLifetime() < this.tickCount) {
                        this.level().playSound((Player) null, this.getX(), this.getY(), this.getZ(),
                                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F,
                                0.625F + 0.1f * this.random.nextFloat());
                        ((ServerLevel) this.level()).sendParticles(ParticleTypes.ENCHANTED_HIT, this.getX(),
                                this.getY(), this.getZ(), 16, 0.7, 0.7, 0.7, 0.02);
                        this.getAlreadyHits().forEach(entity -> {

                            if (entity.isAlive()) {
                                entity.addDeltaMovement(new Vec3(
                                        (double) (-Math.sin(getOwner().getYRot() * (float) Math.PI / 180.0F) * 0.5),
                                        0.05D,
                                        (double) (Math.cos(getOwner().getYRot() * (float) Math.PI / 180.0F) * 0.5)));
                                doAttackWith(this.damageSources().indirectMagic(this, this.getShooter()),
                                        (float) (living.getAttributeValue(Attributes.ATTACK_DAMAGE) * 2F), entity, true,
                                        true);
                            }
                        });
                        this.remove(RemovalReason.DISCARDED);
                    }
                }
            }
        };
        jc.setPos(pos.x, pos.y, pos.z);
        jc.setOwner(living);

        jc.setRotationRoll(180);
        jc.setYRot(living.getYRot() - 22.5F);
        jc.setXRot(0);

        int colorCode = living.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> state.getColorCode()).orElseGet(() -> 0xFFFFFF);
        jc.setColor(colorCode);

        jc.setMute(false);
        jc.setIsCritical(false);

        jc.setDamage(0D);

        jc.setKnockBack(KnockBacks.cancel);

        if (living != null)
            living.getCapability(ConcentrationRankCapabilityProvider.RANK_POINT)
                    .ifPresent(rank -> jc.setRank(rank.getRankLevel(living.level().getGameTime())));

        jc.setLifetime(36);

        living.level().addFreshEntity(jc);
    }

    static public List<Entity> areaAttack(LivingEntity playerIn, Consumer<LivingEntity> beforeHit, float ratio,
            boolean forceHit, boolean resetHit, boolean mute) {
        return areaAttack(playerIn, beforeHit, ratio, forceHit, resetHit, mute, null);
    }

    static public List<Entity> areaAttack(LivingEntity playerIn, Consumer<LivingEntity> beforeHit, float ratio,
            boolean forceHit, boolean resetHit, boolean mute, List<Entity> exclude) {
        List<Entity> founds = Lists.newArrayList();
        float modifiedRatio = (1.0F + EnchantmentHelper.getSweepingDamageRatio(playerIn) * 0.5f) * ratio;
        AttributeModifier am = new AttributeModifier("SweepingDamageRatio", modifiedRatio,
                AttributeModifier.Operation.MULTIPLY_BASE);

        if (!playerIn.level().isClientSide()) {
            try {
                playerIn.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(am);

                founds = TargetSelector.getTargettableEntitiesWithinAABB(playerIn.level(), playerIn);

                if (exclude != null)
                    founds.removeAll(exclude);

                for (Entity entity : founds) {
                    if (entity instanceof LivingEntity living)
                        beforeHit.accept(living);

                    doMeleeAttack(playerIn, entity, forceHit, resetHit);
                }

            } finally {
                playerIn.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(am);
            }
        }

        if (!mute)
            playerIn.level().playSound((Player) null, playerIn.getX(), playerIn.getY(), playerIn.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F,
                    0.4F / (playerIn.getRandom().nextFloat() * 0.4F + 0.8F));

        return founds;
    }

    static public <E extends Entity & IShootable> List<Entity> areaAttack(E owner, Consumer<LivingEntity> beforeHit,
            double reach, boolean forceHit, boolean resetHit) {
        return areaAttack(owner, beforeHit, reach, forceHit, resetHit, null);
    }

    static public <E extends Entity & IShootable> List<Entity> areaAttack(E owner, Consumer<LivingEntity> beforeHit,
            double reach, boolean forceHit, boolean resetHit, List<Entity> exclude) {
        List<Entity> founds = Lists.newArrayList();

        // AABB bb = owner.getBoundingBox();
        // bb = bb.grow(3.0D, 3D, 3.0D);

        if (!owner.level().isClientSide()) {
            founds = TargetSelector.getTargettableEntitiesWithinAABB(owner.level(), reach, owner);

            if (exclude != null)
                founds.removeAll(exclude);

            for (Entity entity : founds) {

                if (entity instanceof LivingEntity living)
                    beforeHit.accept(living);

                float baseAmount = (float) owner.getDamage();
                doAttackWith(owner.damageSources().indirectMagic(owner, owner.getShooter()), baseAmount, entity,
                        forceHit, resetHit);
            }
        }

        return founds;
    }

    static public void doManagedAttack(Consumer<Entity> attack, Entity target, boolean forceHit, boolean resetHit) {
        if (forceHit)
            target.invulnerableTime = 0;

        attack.accept(target);

        if (resetHit)
            target.invulnerableTime = 0;
    }

    static public void doAttackWith(DamageSource src, float amount, Entity target, boolean forceHit, boolean resetHit) {
        if (target instanceof EntityAbstractSummonedSword)
            return;

        doManagedAttack((t) -> {
            t.hurt(src, amount);
        }, target, forceHit, resetHit);
    }

    static public void doMeleeAttack(LivingEntity attacker, Entity target, boolean forceHit, boolean resetHit) {
        if (attacker instanceof Player) {
            doManagedAttack((t) -> {
                attacker.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).ifPresent((state) -> {

                    IConcentrationRank.ConcentrationRanks rankBonus = attacker
                            .getCapability(ConcentrationRankCapabilityProvider.RANK_POINT)
                            .map(rp -> rp.getRank(attacker.getCommandSenderWorld().getGameTime()))
                            .orElse(IConcentrationRank.ConcentrationRanks.NONE);

                    float modifiedRatio = rankBonus.level / 2.0f;
                    if (attacker instanceof Player
                            && IConcentrationRank.ConcentrationRanks.S.level <= rankBonus.level) {
                        int level = ((Player) attacker).experienceLevel;
                        modifiedRatio = Math.max(modifiedRatio, Math.min(level, state.getRefine()));
                    }

                    AttributeModifier am = new AttributeModifier("RankDamageBonus", modifiedRatio,
                            AttributeModifier.Operation.ADDITION);
                    try {
                        state.setOnClick(true);
                        attacker.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(am);

                        ((Player) attacker).attack(t);

                    } finally {
                        attacker.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(am);
                        state.setOnClick(false);
                    }
                });
            }, target, forceHit, resetHit);
        } else {
            float baseAmount = (float) attacker.getAttribute(Attributes.ATTACK_DAMAGE).getValue();
            doAttackWith(attacker.damageSources().mobAttack(attacker), baseAmount, target, forceHit, resetHit);
        }

        ArrowReflector.doReflect(target, attacker);
        TNTExtinguisher.doExtinguishing(target, attacker);
    }

    public static void playQuickSheathSoundAction(LivingEntity entity) {
    	if(entity.level().isClientSide())
    		return ;
    	entity.level().playSound((Player)null, entity.getX(), entity.getY(), entity.getZ(), 
    			SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static Vec3 genRushOffset(LivingEntity entityIn) {
        return new Vec3(entityIn.getRandom().nextFloat() - 0.5f, entityIn.getRandom().nextFloat() - 0.5f, 0).scale(2.0);
    }
}
