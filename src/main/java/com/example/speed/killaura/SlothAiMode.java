package com.example.speed.killaura;

import com.example.speed.utils.Angle;
import com.example.speed.utils.AngleUtil;
import com.example.speed.utils.RaytracingUtil;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.security.SecureRandom;

public class SlothAiMode extends AngleSmoothMode {

    public SlothAiMode() {
        super("SlothAI");
    }

    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3d vec3d, Entity entity) {
        Angle angleDelta = AngleUtil.calculateDelta(currentAngle, targetAngle);
        float yawDelta = angleDelta.getYaw(), pitchDelta = angleDelta.getPitch();
        float rotationDifference = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));
        KillAura killAura = KillAura.INSTANCE;
        boolean attack = lolikbypass(0);
        boolean pa = RaytracingUtil.rayTrace(killAura.getConfig());

        float yaw = attack ? 0 : (killAura.getTarget() != null) ? (float) (randomLerp(1, 40) * Math.sin(System.currentTimeMillis() / 60D)) : 0;
        float pitch = attack ? 0 : (killAura.getTarget() != null) ? (float) (randomLerp(30, 180) * Math.cos(System.currentTimeMillis() / 40D)) : 0;
        
        float speed = attack ? 1.0f : (lolikbypass(1) ? 0.5f : 0.3f);
        if (attack && !pa) {
            speed = 1.0f;
        }
        float lineYaw = (Math.abs(yawDelta / rotationDifference) * 180);
        float linePitch = (Math.abs(pitchDelta) * 180);
        float moveYaw = MathHelper.clamp(yawDelta, -lineYaw, lineYaw);
        float movePitch = MathHelper.clamp(pitchDelta, -linePitch, linePitch);
        Angle moveAngle = new Angle(currentAngle.getYaw(), currentAngle.getPitch());
        moveAngle.setYaw(MathHelper.lerp(Math.clamp(randomLerp(speed, speed + 0.2F), 0f, 1f), currentAngle.getYaw(), currentAngle.getYaw() + moveYaw) + yaw);
        moveAngle.setPitch(MathHelper.lerp(Math.clamp(randomLerp(speed, speed + 0.2F), 0f, 1f), currentAngle.getPitch(), currentAngle.getPitch() + movePitch) + pitch);
        return moveAngle;
    }

    private boolean lolikbypass(int ticks) {
        KillAura lolikzabustil = KillAura.INSTANCE;
        return lolikzabustil.getTarget() != null && Lumora.getInstance().getAttackPerpetrator().getAttackHandler().canAttack(lolikzabustil.getConfig(), ticks);
    }

    private float randomLerp(float min, float max) {
        return MathHelper.lerp(new SecureRandom().nextFloat(), min, max);
    }

    @Override
    public Vec3d randomValue() {
        return new Vec3d(0, 0, 0);
    }
}
