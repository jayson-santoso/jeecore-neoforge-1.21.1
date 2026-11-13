package net.sonflowers.jeecore.gene.terragolem.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

/**
 * Updates client held-beam sound pulses and pitch drift from synchronized beam state.
 */
@EventBusSubscriber(modid = net.sonflowers.jeecore.JeeCore.MOD_ID, value = Dist.CLIENT)
public final class TerragolemBeamSounds {
    private static final Map<UUID, List<BeamLoop>> LOOPS = new HashMap<>();
    private static final Map<UUID, BeamLoop> CHARGE_LOOPS = new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel previousLevel;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (previousLevel != mc.level) {
            LOOPS.values().forEach(sounds -> { for (BeamLoop sound : sounds) mc.getSoundManager().stop(sound); });
            LOOPS.clear();
            CHARGE_LOOPS.values().forEach(mc.getSoundManager()::stop);
            CHARGE_LOOPS.clear();
            previousLevel = mc.level;
        }
        if (mc.level == null || mc.isPaused()) return;
        CHARGE_LOOPS.entrySet().removeIf(entry -> {
            Player player = mc.level.getPlayerByUUID(entry.getKey());
            if (player != null && player.isAlive() && TerragolemReadyUpRenderer.isReadyUp(entry.getKey())) return false;
            mc.getSoundManager().stop(entry.getValue());
            return true;
        });
        LOOPS.entrySet().removeIf(entry -> {
            Player player = mc.level.getPlayerByUUID(entry.getKey());
            entry.getValue().removeIf(BeamLoop::isStopped);
            if (player != null && player.isAlive() && TerragolemReadyUpRenderer.isHeld(entry.getKey())) return false;
            for (BeamLoop sound : entry.getValue()) mc.getSoundManager().stop(sound);
            return true;
        });
        for (Player player : mc.level.players()) {
            if (player.isAlive() && TerragolemReadyUpRenderer.isReadyUp(player.getUUID())
                    && !CHARGE_LOOPS.containsKey(player.getUUID())) {
                BeamLoop hum = new BeamLoop(player, SoundEvents.BEACON_AMBIENT, 0.5f, 0.6f, false);
                CHARGE_LOOPS.put(player.getUUID(), hum);
                mc.getSoundManager().play(hum);
            }
            if (!player.isAlive() || !TerragolemReadyUpRenderer.isHeld(player.getUUID())) continue;
            List<BeamLoop> sounds = LOOPS.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>());
            if ((sounds.isEmpty() || mc.level.getGameTime() % 14 == 0) && sounds.size() < 8) {
                BeamLoop sound = new BeamLoop(player, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 0.85f, 0.65f, true);
                sounds.add(sound);
                mc.getSoundManager().play(sound);
            }
        }
    }

    private static final class BeamLoop extends AbstractTickableSoundInstance {
        private final Player player;
        private final boolean held;
        private int age;
        private float targetPitch = 0.65f;
        BeamLoop(Player player, SoundEvent sound, float volume, float pitch, boolean held) {
            super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.held = held;
            this.volume = volume;
            this.pitch = pitch;
            if (held) this.pitch = 0.55f + random.nextFloat() * 0.3f;
            looping = !held;
            delay = 0;
            x = player.getX(); y = player.getY(); z = player.getZ();
        }
        @Override public void tick() {
            if (Minecraft.getInstance().level != player.level() || !player.isAlive()
                    || !(held ? TerragolemReadyUpRenderer.isHeld(player.getUUID())
                    : TerragolemReadyUpRenderer.isReadyUp(player.getUUID()))) { stop(); return; }
            x = player.getX(); y = player.getY(); z = player.getZ();
            if (held) {
                // Let each sample become audible while newer pulses overlap it.
                if (++age >= 40) { stop(); return; }
                // Smooth irregular pitch drift keeps the repeating conversion sounding alive.
                if (age % 3 == 0) targetPitch = 0.55f + random.nextFloat() * 0.3f;
                pitch += (targetPitch - pitch) * 0.12f;
            }
        }
    }
}
