package com.ntmdoors.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3D inventory preview renderer for multiblock doors.
 * Normalizes large OBJ models into a standard 1x1x1 bounding box with fullbright lighting
 * (LightmapTextureManager.MAX_LIGHT_COORDINATE) to guarantee clean, visible previews regardless of
 * missing or inverted normals.
 */
@Environment(EnvType.CLIENT)
public final class DoorItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    public static final DoorItemRenderer INSTANCE = new DoorItemRenderer();

    public record ItemBounds(float scale, float cx, float cy, float cz) {}

    public interface DoorItemModelProvider {
        Identifier getModelId(ItemStack stack);
        Identifier getTextureId(ItemStack stack);
        default Identifier getTextureForGroup(ItemStack stack, String groupName) {
            return getTextureId(stack);
        }
        Collection<String> getItemGroups(ItemStack stack);
        float getModelYaw(ItemStack stack);
    }

    private static final Map<Item, DoorItemModelProvider> PROVIDERS = new ConcurrentHashMap<>();
    private static final Map<Identifier, ItemBounds> BOUNDS_CACHE = new ConcurrentHashMap<>();

    private DoorItemRenderer() {}

    public static void registerProvider(Item item, DoorItemModelProvider provider) {
        PROVIDERS.put(item, provider);
    }

    public static void clearCache() {
        BOUNDS_CACHE.clear();
    }

    @FunctionalInterface
    public interface TextureResolver {
        Identifier getTexture(String groupName);
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        DoorItemModelProvider provider = PROVIDERS.get(stack.getItem());
        if (provider == null) return;

        Identifier modelId = provider.getModelId(stack);
        if (modelId == null) return;

        ObjModel model = ObjModel.get(modelId);
        if (model == null) return;

        Collection<String> groups = provider.getItemGroups(stack);
        float yaw = provider.getModelYaw(stack);

        renderNormalizedModel(matrices, vertexConsumers, model, modelId, groups,
                groupName -> provider.getTextureForGroup(stack, groupName), yaw, overlay);
    }

    /**
     * Normalizes and renders the given OBJ model within a 1x1x1 box using fullbright lighting.
     */
    public static void renderNormalizedModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                             ObjModel model, Identifier modelId, Collection<String> groups,
                                             Identifier texture, float yawDegrees, int overlay) {
        renderNormalizedModel(matrices, vertexConsumers, model, modelId, groups,
                groupName -> texture, yawDegrees, overlay);
    }

    /**
     * Normalizes and renders the given OBJ model within a 1x1x1 box using fullbright lighting
     * and per-group texture resolution.
     */
    public static void renderNormalizedModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                             ObjModel model, Identifier modelId, Collection<String> groups,
                                             TextureResolver textureResolver, float yawDegrees, int overlay) {
        if (model == null || textureResolver == null) return;

        Collection<String> targetGroups = (groups == null || groups.isEmpty()) ? model.groups() : groups;
        ItemBounds bounds = (modelId != null)
                ? BOUNDS_CACHE.computeIfAbsent(modelId, id -> computeBounds(model, targetGroups))
                : computeBounds(model, targetGroups);

        if (bounds == null) return;

        matrices.push();

        // Standard 1x1x1 item preview centering
        matrices.translate(0.5f, 0.5f, 0.5f);
        matrices.scale(bounds.scale, bounds.scale, bounds.scale);
        if (yawDegrees != 0.0f) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDegrees));
        }
        matrices.translate(-bounds.cx, -bounds.cy, -bounds.cz);

        // Fullbright preview overrides missing/inverted OBJ normals in inventory
        int previewLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        for (String groupName : targetGroups) {
            ObjModel.MeshGroup group = model.getGroup(groupName);
            if (group != null) {
                Identifier texture = textureResolver.getTexture(groupName);
                if (texture != null) {
                    VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
                    ObjRenderer.renderGroup(matrices, consumer, group, null, null, null,
                            previewLight, overlay, 1.0f, 1.0f, 1.0f, 1.0f);
                }
            }
        }

        matrices.pop();
    }

    /**
     * Computes the scale and center offset needed to normalize an OBJ model into a 1x1x1 unit box.
     */
    public static ItemBounds computeBounds(ObjModel model, Collection<String> groups) {
        if (model == null) return null;

        float[] b = model.bounds(groups);
        if (b == null) {
            b = new float[]{model.minX(), model.minY(), model.minZ(),
                    model.maxX(), model.maxY(), model.maxZ()};
        }
        float sizeX = b[3] - b[0];
        float sizeY = b[4] - b[1];
        float sizeZ = b[5] - b[2];
        float size = Math.max(sizeX, Math.max(sizeY, sizeZ));
        if (size <= 0.0f) return null;

        return new ItemBounds(
                1.0f / size,
                (b[0] + b[3]) * 0.5f,
                (b[1] + b[4]) * 0.5f,
                (b[2] + b[5]) * 0.5f
        );
    }
}
