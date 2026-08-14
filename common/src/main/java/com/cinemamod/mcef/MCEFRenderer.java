package com.cinemamod.mcef;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.UUID;

public class MCEFRenderer {
    private final boolean transparent;
    private DynamicTexture texture;
    private ResourceLocation textureId;
    private int texWidth;
    private int texHeight;

    protected MCEFRenderer(boolean transparent) {
        this.transparent = transparent;
    }

    public void initialize() {
    }

    /**
     * Retrieves the modern Minecraft ResourceLocation for the rendered CEF browser texture.
     * Use this with GuiGraphics.blit(textureId, ...) or custom shaders.
     *
     * @return The dynamic texture ResourceLocation, or null if uninitialized.
     */
    public ResourceLocation getTextureId() {
        return textureId;
    }

    /**
     * Legacy OpenGL texture ID for backwards compatibility.
     */
    public int getTextureID() {
        return texture != null ? 1 : 0;
    }

    public boolean isTransparent() {
        return transparent;
    }

    protected void cleanup() {
        if (texture != null) {
            if (textureId != null) {
                Minecraft.getInstance().getTextureManager().release(textureId);
                textureId = null;
            }
            texture.close();
            texture = null;
        }
    }

    private static java.lang.reflect.Field pixelsField;
    static {
        try {
            for (java.lang.reflect.Field f : NativeImage.class.getDeclaredFields()) {
                if (f.getType() == long.class) {
                    f.setAccessible(true);
                    pixelsField = f;
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    private static long getPointer(NativeImage image) {
        if (image == null) return 0;
        try {
            if (pixelsField != null) {
                return pixelsField.getLong(image);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    protected void onPaint(ByteBuffer buffer, int width, int height) {
        if (width <= 0 || height <= 0) return;

        if (this.texture == null || this.texWidth != width || this.texHeight != height) {
            if (this.texture != null) {
                if (this.textureId != null) {
                    Minecraft.getInstance().getTextureManager().release(this.textureId);
                }
                this.texture.close();
            }
            this.texWidth = width;
            this.texHeight = height;
            this.texture = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, width, height, false));
            this.textureId = ResourceLocation.fromNamespaceAndPath("mcef", UUID.randomUUID().toString().toLowerCase());
            Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);
        }

        NativeImage image = this.texture.getPixels();
        long dstPtr = getPointer(image);
        if (dstPtr == 0) return;

        long totalBytes = (long) width * (long) height * 4L;
        if (buffer.limit() > 0) {
            long srcPtr = MemoryUtil.memAddress(buffer);
            for (long offset = 0; offset < totalBytes; offset += 4) {
                byte b = MemoryUtil.memGetByte(srcPtr + offset);
                byte g = MemoryUtil.memGetByte(srcPtr + offset + 1);
                byte r = MemoryUtil.memGetByte(srcPtr + offset + 2);
                byte a = MemoryUtil.memGetByte(srcPtr + offset + 3);
                MemoryUtil.memPutByte(dstPtr + offset, r);
                MemoryUtil.memPutByte(dstPtr + offset + 1, g);
                MemoryUtil.memPutByte(dstPtr + offset + 2, b);
                MemoryUtil.memPutByte(dstPtr + offset + 3, a);
            }
            this.texture.upload();
        }
    }

    protected void onPaint(ByteBuffer buffer, int x, int y, int width, int height) {
        if (this.texture == null || width <= 0 || height <= 0) return;

        NativeImage image = this.texture.getPixels();
        long dstPtr = getPointer(image);
        if (dstPtr == 0) return;

        long srcPtr = MemoryUtil.memAddress(buffer);
        int fullWidth = this.texWidth;
        int fullHeight = this.texHeight;

        for (int r = 0; r < height; r++) {
            int curY = y + r;
            if (curY >= fullHeight) break;

            long rowPixelOffset = ((long) curY * (long) fullWidth + (long) x) * 4L;
            long srcRow = srcPtr + rowPixelOffset;
            long dstRow = dstPtr + rowPixelOffset;

            int rowBytes = Math.min(width, fullWidth - x) * 4;
            for (int c = 0; c < rowBytes; c += 4) {
                byte b = MemoryUtil.memGetByte(srcRow + c);
                byte g = MemoryUtil.memGetByte(srcRow + c + 1);
                byte rCol = MemoryUtil.memGetByte(srcRow + c + 2);
                byte a = MemoryUtil.memGetByte(srcRow + c + 3);
                MemoryUtil.memPutByte(dstRow + c, rCol);
                MemoryUtil.memPutByte(dstRow + c + 1, g);
                MemoryUtil.memPutByte(dstRow + c + 2, b);
                MemoryUtil.memPutByte(dstRow + c + 3, a);
            }
        }
        this.texture.upload();
    }
}
