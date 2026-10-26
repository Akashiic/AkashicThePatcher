package com.akashic.hologramfastpath;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;

import com.akashic.thepatcher.module.hologram.HologramFastPathTransformer;
import com.akashic.thepatcher.util.BundleLog;

/**
 * Client-side fast path for HolographicDisplays-style holograms on a 1.7.x client (beta.43).
 *
 * A 1.7 server cannot send armor stands or text displays, so HolographicDisplays 2.x (and
 * every plugin that copied it) builds each line out of real mobs:
 *   text line   EntityHorse with growing age -1700000 (model scale about -34), custom name
 *               always shown, riding an EntityWitherSkull 54.56 blocks above the line; the
 *               negative height brings the name tag back down to the line;
 *   item line   EntityItem (stack size 0) riding a wither skull 0.21 below the line;
 *   touch line  invisible EntitySlime riding a wither skull.
 * The client treats them as mobs and projectiles: every tick each skull ray-traces blocks,
 * queries entities and spawns a smoke particle (300 lines alone keep the particle engine at its
 * 4000 cap), each horse runs the living-entity update; every frame each horse draws a giant
 * inverted horse model plus a name tag with its own GL state changes, and with shaders the
 * shadow pass draws them again.
 *
 * What a 1.21 client does with a hologram (marker armor stand / text display) is a
 * non-ticking, non-colliding entity whose text is drawn as one billboard. This class gives the
 * 1.7 carriers that treatment:
 *   tick    carriers keep their position (the server still teleports them) and nothing else;
 *           item lines keep spinning;
 *   render  horses, skulls and touch slimes leave the entity pipeline (range check false,
 *           RenderManager returns at once, so neither the world pass, the shadow pass nor
 *           EntityCulling draws them) and every visible text line is drawn in one pass at the
 *           end of the entity pass (RenderGlobal.renderEntities, before "blockentities"):
 *           GL state set once, the backgrounds in one Tessellator draw, then the see-through
 *           text and the normal text, nearest last; culled by the camera frustum and by the
 *           vanilla 1.7 name tag rule (the viewer within 64 blocks of the horse, which floats 55
 *           blocks above its line, so the same lines are visible as before).
 * Item lines keep the vanilla item renderer.
 *
 * Package: deliberately outside com.akashic.thepatcher.* (ThePatcher's TransformerExclusions), so Angelica
 * redirects the GL11 calls below to its GLStateManager like it does for every mod (Angelica runs a core
 * profile context: a raw glAlphaFunc there throws "Function is not supported").
 *
 * Kill-switch: -Dakashic.hologramFastPath.disable=true
 * Text range: -Dakashic.hologramFastPath.range=<blocks> measures from the line itself instead, like a
 * 1.8+ client does with armor stands (more lines visible, more to draw); default: the 1.7 rule above.
 */
public final class HologramFastPath {

    /** HolographicDisplays uses -1700000; vanilla babies are -24000 at most. */
    static final int HOLOGRAM_AGE = -1000000;
    static final int SEE_THROUGH_COLOR = 553648127;
    static final float SCALE = 0.016666668F * 1.6F;

    /** Vanilla 1.7 name tag range, measured from the viewer to the horse. */
    static final double VANILLA_RANGE = 64.0;
    /** > 0: range from the line itself (akashic.hologramFastPath.range); 0: the vanilla rule. */
    private static final double RANGE;
    private static boolean firstLog;

    static {
        double range = 0.0;
        final String value = System.getProperty("akashic.hologramFastPath.range");
        if (value != null) {
            try {
                range = Math.max(8.0, Math.min(256.0, Double.parseDouble(value.trim())));
            } catch (NumberFormatException ignored) {
                // keep the vanilla rule
            }
        }
        RANGE = range;
    }

    private static EntityHorse[] lines = new EntityHorse[64];
    private static double[] lineX = new double[64], lineY = new double[64], lineZ = new double[64];
    private static String[] lineText = new String[64];
    /** (float bits of the squared distance << 32) | index, sorted ascending: drawn from the end (far first). */
    private static long[] order = new long[64];
    private static final AxisAlignedBB BOX = AxisAlignedBB.getBoundingBox(0, 0, 0, 0, 0, 0);
    private static int frameLines;
    private static long renderPasses;

    private HologramFastPath() {}

    public static boolean isTextLine(Entity entity) {
        if (!(entity instanceof EntityHorse)) {
            return false;
        }
        final EntityHorse horse = (EntityHorse) entity;
        if (horse.akashic$hologram) {
            return true;
        }
        if (horse.worldObj == null || !horse.worldObj.isRemote || horse.getGrowingAge() > HOLOGRAM_AGE) {
            return false;
        }
        // The age never changes for a hologram (HD locks it), so this is cached for the entity's life.
        horse.akashic$hologram = true;
        return true;
    }

    /** Wither skull carrying a hologram line (text, item or touch slime). */
    public static boolean isLineVehicle(Entity entity) {
        return entity instanceof EntityWitherSkull && entity.worldObj != null && entity.worldObj.isRemote
            && isLine(entity.riddenByEntity);
    }

    private static boolean isLine(Entity entity) {
        return isTextLine(entity) || isItemLine(entity) || isTouchLine(entity);
    }

    public static boolean isItemLine(Entity entity) {
        return entity instanceof EntityItem && entity.ridingEntity instanceof EntityWitherSkull
            && entity.worldObj != null && entity.worldObj.isRemote;
    }

    public static boolean isTouchLine(Entity entity) {
        return entity instanceof EntitySlime && entity.ridingEntity instanceof EntityWitherSkull
            && entity.worldObj != null && entity.worldObj.isRemote;
    }

    /** EntityFireball.onUpdate head: true = the skull carries a line, its tick is done. */
    public static boolean tickVehicle(EntityFireball skull) {
        if (!isLineVehicle(skull)) {
            return false;
        }
        hold(skull);
        return true;
    }

    /** EntityHorse/EntityItem/EntitySlime.onUpdate head: true = hologram line, its tick is done. */
    public static boolean tickLine(Entity entity) {
        if (entity instanceof EntityItem) {
            if (!isItemLine(entity)) {
                return false;
            }
            ((EntityItem) entity).age++;
        } else if (!isTextLine(entity) && !isTouchLine(entity)) {
            return false;
        }
        hold(entity);
        return true;
    }

    private static void hold(Entity entity) {
        entity.ticksExisted++;
        entity.prevPosX = entity.posX;
        entity.prevPosY = entity.posY;
        entity.prevPosZ = entity.posZ;
        entity.prevRotationYaw = entity.rotationYaw;
        entity.prevRotationPitch = entity.rotationPitch;
        entity.motionX = 0.0;
        entity.motionY = 0.0;
        entity.motionZ = 0.0;
    }

    /** Entity.isInRangeToRender3d head and RenderManager.renderEntityWithPosYaw head. */
    public static boolean leavesEntityPipeline(Entity entity) {
        return HologramFastPathTransformer.rendererInstalled && (isTextLine(entity) || isLineVehicle(entity) || isTouchLine(entity));
    }

    /** RendererLivingEntity.passSpecialRender head (EntityCulling calls it for culled entities). */
    public static boolean drawsOwnName(Entity entity) {
        return HologramFastPathTransformer.rendererInstalled && isTextLine(entity);
    }

    /** RenderGlobal.renderEntities, after the entity loop (before "blockentities"). */
    public static void renderLines(ICamera camera, float partialTicks) {
        renderPasses++;
        if (MinecraftForgeClient.getRenderPass() != 0 || !Minecraft.isGuiEnabled()) {
            return;
        }
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        final RenderManager manager = RenderManager.instance;
        final FontRenderer font = manager.getFontRenderer();
        final Entity viewer = manager.livingPlayer;
        if (font == null || viewer == null) {
            return;
        }
        final double rangeSq = RANGE > 0.0 ? RANGE * RANGE : VANILLA_RANGE * VANILLA_RANGE;
        final double vx = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        final double vy = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        final double vz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;

        int count = 0;
        final List<?> entities = mc.theWorld.loadedEntityList;
        for (int i = 0, n = entities.size(); i < n; i++) {
            final Object o = entities.get(i);
            if (!(o instanceof EntityHorse) || !isTextLine((Entity) o)) {
                continue;
            }
            final EntityHorse horse = (EntityHorse) o;
            if (horse.isDead || horse.riddenByEntity != null || !horse.getAlwaysRenderNameTagForRender()
                || horse.isInvisibleToPlayer(mc.thePlayer)) {
                continue;
            }
            final String text = horse.getCustomNameTag();
            if (text == null || text.isEmpty()) {
                continue;
            }
            // Where vanilla draws the tag: the horse's render position + height + 0.5 (height is negative).
            final double x = horse.lastTickPosX + (horse.posX - horse.lastTickPosX) * partialTicks;
            final double y = horse.lastTickPosY + (horse.posY - horse.lastTickPosY) * partialTicks
                + tagOffset(horse);
            final double z = horse.lastTickPosZ + (horse.posZ - horse.lastTickPosZ) * partialTicks;
            final double dx = x - vx, dy = y - vy, dz = z - vz;
            final double distance = dx * dx + dy * dy + dz * dz;
            if (RANGE > 0.0 ? distance > rangeSq : vanillaDistanceSq(horse, viewer) > rangeSq) {
                continue;
            }
            if (camera != null) {
                final double half = font.getStringWidth(text) * SCALE * 0.5 + 0.1;
                BOX.setBounds(x - half, y - 0.3, z - half, x + half, y + 0.3, z + half);
                if (!camera.isBoundingBoxInFrustum(BOX)) {
                    continue;
                }
            }
            if (count == lines.length) {
                grow();
            }
            lines[count] = horse;
            lineX[count] = x - RenderManager.renderPosX;
            lineY[count] = y - RenderManager.renderPosY;
            lineZ[count] = z - RenderManager.renderPosZ;
            order[count] = ((long) Float.floatToIntBits((float) distance) << 32) | count;
            lineText[count] = text;
            count++;
        }
        frameLines = count;
        if (count == 0) {
            return;
        }
        if (!firstLog) {
            firstLog = true;
            BundleLog.info("Hologram fast path: first frame with " + count + " hologram text line(s) drawn in one pass ("
                + (RANGE > 0.0 ? "range " + RANGE + " blocks from the line" : "vanilla 1.7 name tag range") + ").");
        }
        // Far to near, so overlapping translucent backgrounds blend like the vanilla order would.
        Arrays.sort(order, 0, count);
        try {
            draw(font, manager.playerViewY, manager.playerViewX, count);
        } finally {
            for (int i = 0; i < count; i++) {
                lines[i] = null;
                lineText[i] = null;
            }
        }
    }

    /** Render.renderLivingLabel's test: entity.getDistanceSqToEntity(livingPlayer), current positions. */
    static double vanillaDistanceSq(Entity entity, Entity viewer) {
        final double dx = entity.posX - viewer.posX, dy = entity.posY - viewer.posY, dz = entity.posZ - viewer.posZ;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Vanilla's horse height after setScaleForAge(true) (the update that sets it is skipped) + 0.5. */
    static double tagOffset(EntityHorse horse) {
        final int age = horse.getGrowingAge();
        final float size = age >= 0 ? 1.0F : 0.5F + (float) (-24000 - age) / -24000.0F * 0.5F;
        return 1.6F * size + 0.5F;
    }

    private static void draw(FontRenderer font, float viewY, float viewX, int count) {
        // Billboard basis: vanilla's rotate(-viewY, Y) * rotate(viewX, X) * scale(-s, -s, s).
        final double yaw = Math.toRadians(-viewY), pitch = Math.toRadians(viewX);
        final double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        final double rightX = -SCALE * cy, rightY = 0.0, rightZ = SCALE * sy;
        final double downX = -SCALE * sy * sp, downY = -SCALE * cp, downZ = -SCALE * cy * sp;

        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        final Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(0.0F, 0.0F, 0.0F, 0.25F);
        final int[] widths = new int[count];
        for (int k = 0; k < count; k++) {
            final int i = (int) order[count - 1 - k];
            final int half = font.getStringWidth(lineText[i]) / 2;
            widths[i] = half;
            quad(tessellator, lineX[i], lineY[i], lineZ[i], -half - 1, -1, half + 1, 8,
                rightX, rightY, rightZ, downX, downY, downZ);
        }
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        for (int k = 0; k < count; k++) {
            final int i = (int) order[count - 1 - k];
            text(font, i, viewY, viewX, widths[i], SEE_THROUGH_COLOR);
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        for (int k = 0; k < count; k++) {
            final int i = (int) order[count - 1 - k];
            text(font, i, viewY, viewX, widths[i], -1);
        }
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void quad(Tessellator t, double x, double y, double z, double u0, double v0, double u1,
                             double v1, double rx, double ry, double rz, double dx, double dy, double dz) {
        t.addVertex(x + u0 * rx + v0 * dx, y + u0 * ry + v0 * dy, z + u0 * rz + v0 * dz);
        t.addVertex(x + u0 * rx + v1 * dx, y + u0 * ry + v1 * dy, z + u0 * rz + v1 * dz);
        t.addVertex(x + u1 * rx + v1 * dx, y + u1 * ry + v1 * dy, z + u1 * rz + v1 * dz);
        t.addVertex(x + u1 * rx + v0 * dx, y + u1 * ry + v0 * dy, z + u1 * rz + v0 * dz);
    }

    private static void text(FontRenderer font, int i, float viewY, float viewX, int half, int color) {
        GL11.glPushMatrix();
        GL11.glTranslatef((float) lineX[i], (float) lineY[i], (float) lineZ[i]);
        GL11.glRotatef(-viewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(viewX, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(-SCALE, -SCALE, SCALE);
        font.drawString(lineText[i], -half, 0, color);
        GL11.glPopMatrix();
    }

    private static void grow() {
        final int size = lines.length * 2;
        lines = Arrays.copyOf(lines, size);
        lineX = Arrays.copyOf(lineX, size);
        lineY = Arrays.copyOf(lineY, size);
        lineZ = Arrays.copyOf(lineZ, size);
        lineText = Arrays.copyOf(lineText, size);
        order = Arrays.copyOf(order, size);
    }

    /** Calls from RenderGlobal.renderEntities (diagnostics, gates). */
    public static long renderPasses() {
        return renderPasses;
    }

    /** Lines drawn in the last frame (diagnostics). */
    public static int lastFrameLines() {
        return frameLines;
    }
}
