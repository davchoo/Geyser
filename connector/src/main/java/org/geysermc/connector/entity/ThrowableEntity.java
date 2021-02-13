/*
 * Copyright (c) 2019-2021 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.connector.entity;

import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.protocol.bedrock.data.LevelEventType;
import com.nukkitx.protocol.bedrock.data.entity.EntityFlag;
import com.nukkitx.protocol.bedrock.packet.LevelEventPacket;
import com.nukkitx.protocol.bedrock.packet.MoveEntityDeltaPacket;
import org.geysermc.connector.entity.type.EntityType;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.world.block.BlockTranslator;

/**
 * Used as a class for any object-like entity that moves as a projectile
 */
public class ThrowableEntity extends Entity implements Tickable {

    protected Vector3f lastJavaPosition;

    /**
     * Store the last position sent to Bedrock.
     * Used to determine which axis' should be sent.
     */
    private Vector3f lastBedrockPosition = Vector3f.ZERO;

    /**
     * Store the last rotation sent to Bedrock.
     * Used to determine which axis' should be sent.
     */
    private Vector3f lastBedrockRotation = Vector3f.ZERO;

    public ThrowableEntity(long entityId, long geyserId, EntityType entityType, Vector3f position, Vector3f motion, Vector3f rotation) {
        super(entityId, geyserId, entityType, position, motion, rotation);
        this.lastJavaPosition = position;
    }

    /**
     * Updates the position for the Bedrock client.
     *
     * Java clients assume the next positions of moving items. Bedrock needs to be explicitly told positions
     */
    @Override
    public void tick(GeyserSession session) {
        moveAbsoluteImmediate(session, position.add(motion), rotation, onGround, false);
        float drag = getDrag(session);
        float gravity = getGravity();
        motion = motion.mul(drag).down(gravity);
    }

    protected void moveAbsoluteImmediate(GeyserSession session, Vector3f position, Vector3f rotation, boolean isOnGround, boolean teleported) {
        MoveEntityDeltaPacket moveEntityDeltaPacket = new MoveEntityDeltaPacket();
        moveEntityDeltaPacket.setRuntimeEntityId(geyserId);

        if (isOnGround) {
            moveEntityDeltaPacket.getFlags().add(MoveEntityDeltaPacket.Flag.ON_GROUND);
        }
        setOnGround(isOnGround);

        if (teleported) {
            moveEntityDeltaPacket.getFlags().add(MoveEntityDeltaPacket.Flag.TELEPORTING);
        }

        if (position.getX() != lastBedrockPosition.getX()) {
            moveEntityDeltaPacket.getFlags().add(MoveEntityDeltaPacket.Flag.HAS_X);
            moveEntityDeltaPacket.setX(position.getX());
        }
        if (position.getY() != lastBedrockPosition.getY()) {
            moveEntityDeltaPacket.getFlags().add(MoveEntityDeltaPacket.Flag.HAS_Y);
            moveEntityDeltaPacket.setY(position.getY());
        }
        if (position.getZ() != lastBedrockPosition.getZ()) {
            moveEntityDeltaPacket.getFlags().add(MoveEntityDeltaPacket.Flag.HAS_Z);
            moveEntityDeltaPacket.setZ(position.getZ());
        }
        setPosition(position);
        lastBedrockPosition = position;

        setRotation(rotation);
        rotation = getBedrockRotation();
        if (rotation.getX() != lastBedrockRotation.getX()) {
            moveEntityDeltaPacket.getFlags().add(MoveEntityDeltaPacket.Flag.HAS_PITCH);
            moveEntityDeltaPacket.setPitch(rotation.getX());
        }
        if (rotation.getY() != lastBedrockRotation.getY()) {
            moveEntityDeltaPacket.getFlags().add(MoveEntityDeltaPacket.Flag.HAS_HEAD_YAW);
            moveEntityDeltaPacket.setHeadYaw(rotation.getY());
        }
        if (rotation.getZ() != lastBedrockRotation.getZ()) {
            moveEntityDeltaPacket.getFlags().add(MoveEntityDeltaPacket.Flag.HAS_YAW);
            moveEntityDeltaPacket.setYaw(rotation.getZ());
        }
        lastBedrockRotation = rotation;

        if (!moveEntityDeltaPacket.getFlags().isEmpty()) {
            session.sendUpstreamPacket(moveEntityDeltaPacket);
        }
    }

    /**
     * Get the gravity of this entity type. Used for applying gravity while the entity is in motion.
     *
     * @return the amount of gravity to apply to this entity while in motion.
     */
    protected float getGravity() {
        if (metadata.getFlags().getFlag(EntityFlag.HAS_GRAVITY)) {
            switch (entityType) {
                case THROWN_POTION:
                case LINGERING_POTION:
                    return 0.05f;
                case THROWN_EXP_BOTTLE:
                    return 0.07f;
                case FIREBALL:
                    return 0;
                case SNOWBALL:
                case THROWN_EGG:
                case THROWN_ENDERPEARL:
                    return 0.03f;
                case LLAMA_SPIT:
                    return 0.06f;
            }
        }
        return 0;
    }

    /**
     * @param session the session of the Bedrock client.
     * @return the drag that should be multiplied to the entity's motion
     */
    protected float getDrag(GeyserSession session) {
        if (isInWater(session)) {
            return 0.8f;
        } else {
            switch (entityType) {
                case THROWN_POTION:
                case LINGERING_POTION:
                case THROWN_EXP_BOTTLE:
                case SNOWBALL:
                case THROWN_EGG:
                case THROWN_ENDERPEARL:
                case LLAMA_SPIT:
                    return 0.99f;
                case FIREBALL:
                case SMALL_FIREBALL:
                case DRAGON_FIREBALL:
                    return 0.95f;
            }
        }
        return 1;
    }

    /**
     * @param session the session of the Bedrock client.
     * @return true if this entity is currently in water.
     */
    protected boolean isInWater(GeyserSession session) {
        if (session.getConnector().getConfig().isCacheChunks()) {
            if (0 <= position.getFloorY() && position.getFloorY() <= 255) {
                int block = session.getConnector().getWorldManager().getBlockAt(session, position.toInt());
                String javaIdentifier = BlockTranslator.getJavaIdBlockMap().inverse().get(block);
                return javaIdentifier.startsWith("minecraft:water");
            }
        }
        return false;
    }

    @Override
    public boolean despawnEntity(GeyserSession session) {
        if (entityType == EntityType.THROWN_ENDERPEARL) {
            LevelEventPacket particlePacket = new LevelEventPacket();
            particlePacket.setType(LevelEventType.PARTICLE_TELEPORT);
            particlePacket.setPosition(position);
            session.sendUpstreamPacket(particlePacket);
        }
        return super.despawnEntity(session);
    }

    @Override
    public void moveRelative(GeyserSession session, double relX, double relY, double relZ, Vector3f rotation, boolean isOnGround) {
        moveAbsoluteImmediate(session, lastJavaPosition.add(relX, relY, relZ), rotation, isOnGround, false);
        lastJavaPosition = position;
    }

    @Override
    public void moveAbsolute(GeyserSession session, Vector3f position, Vector3f rotation, boolean isOnGround, boolean teleported) {
        moveAbsoluteImmediate(session, position, rotation, isOnGround, teleported);
        lastJavaPosition = position;
    }
}
