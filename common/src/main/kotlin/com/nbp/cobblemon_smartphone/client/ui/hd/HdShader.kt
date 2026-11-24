package com.nbp.cobblemon_smartphone.client.ui.hd

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.nio.FloatBuffer

/**
 * Self-contained OpenGL 3.2 Core (`#version 150 core`) SDF & textured quad batch renderer.
 * Renders rounded rectangles, gradients, anti-aliased borders, soft drop shadows,
 * circular/rounded textures, and alpha-atlas text/icons at physical monitor resolution.
 */
object HdBatch {

    const val MODE_ROUNDED_RECT = 0f
    const val MODE_DROP_SHADOW = 1f
    const val MODE_TEXTURED_ROUNDED = 2f
    const val MODE_ALPHA_ATLAS = 3f

    private const val MAX_QUADS = 2048
    private const val FLOATS_PER_VERTEX = 28
    private const val VERTICES_PER_QUAD = 4
    private const val INDICES_PER_QUAD = 6
    private const val STRIDE_BYTES = FLOATS_PER_VERTEX * java.lang.Float.BYTES

    private var initialized = false
    private var initFailed = false
    private var programId = 0
    private var vaoId = 0
    private var vboId = 0
    private var eboId = 0
    private var uProjectionLoc = -1
    private var uTextureLoc = -1

    private var vertexBuffer: FloatBuffer? = null
    private var projBuffer: FloatBuffer? = null
    private var quadCount = 0
    private var currentTextureId = 0
    private var activeWindowW = 1
    private var activeWindowH = 1

    private val VERTEX_SHADER = """
        #version 150 core

        uniform mat4 uProjection;

        in vec2 aPos;
        in vec2 aLocalNorm;
        in vec2 aTexUv;
        in vec2 aRectSize;
        in vec4 aRadii;
        in vec4 aParams;
        in vec4 aColorTop;
        in vec4 aColorBot;
        in vec4 aBorderCol;

        out vec2 vLocalNorm;
        out vec2 vTexUv;
        out vec2 vRectSize;
        out vec4 vRadii;
        out vec4 vParams;
        out vec4 vColorTop;
        out vec4 vColorBot;
        out vec4 vBorderCol;

        void main() {
            gl_Position = uProjection * vec4(aPos, 0.0, 1.0);
            vLocalNorm = aLocalNorm;
            vTexUv = aTexUv;
            vRectSize = aRectSize;
            vRadii = aRadii;
            vParams = aParams;
            vColorTop = aColorTop;
            vColorBot = aColorBot;
            vBorderCol = aBorderCol;
        }
    """.trimIndent()

    private val FRAGMENT_SHADER = """
        #version 150 core

        uniform sampler2D uTexture;

        in vec2 vLocalNorm;
        in vec2 vTexUv;
        in vec2 vRectSize;
        in vec4 vRadii;
        in vec4 vParams;
        in vec4 vColorTop;
        in vec4 vColorBot;
        in vec4 vBorderCol;

        out vec4 fragColor;

        float sdRoundedBox(vec2 p, vec2 b, vec4 r) {
            vec2 topOrBot = (p.x > 0.0) ? vec2(r.y, r.z) : vec2(r.x, r.w);
            float rad = (p.y > 0.0) ? topOrBot.y : topOrBot.x;
            vec2 q = abs(p) - b + rad;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - rad;
        }

        void main() {
            float borderW = vParams.x;
            float softness = max(vParams.y, 0.001);
            float mode = vParams.z;

            if (mode > 2.5) {
                float coverage = texture(uTexture, vTexUv).a;
                if (coverage <= 0.002) discard;
                fragColor = vec4(vColorTop.rgb, vColorTop.a * coverage);
                return;
            }

            vec2 halfSize = vRectSize * 0.5;
            vec2 p = (vLocalNorm - 0.5) * vRectSize;
            float maxRad = min(halfSize.x, halfSize.y);
            vec4 radii = clamp(vRadii, vec4(0.0), vec4(maxRad));

            if (mode > 1.5) {
                vec4 tex = texture(uTexture, vTexUv) * vColorTop;
                if (max(max(radii.x, radii.y), max(radii.z, radii.w)) > 0.0 || borderW > 0.0) {
                    float d = sdRoundedBox(p, halfSize, radii);
                    float outerAlpha = 1.0 - smoothstep(-0.65, 0.65, d);
                    if (outerAlpha <= 0.001) discard;
                    if (borderW > 0.0) {
                        float borderMask = smoothstep(-borderW - 0.65, -borderW + 0.65, d);
                        tex = mix(tex, vBorderCol, borderMask);
                    }
                    fragColor = vec4(tex.rgb, tex.a * outerAlpha);
                } else {
                    fragColor = tex;
                }
                return;
            }

            if (mode > 0.5) {
                vec2 innerHalf = max(halfSize - vec2(softness), vec2(1.0));
                vec4 shadowRadii = clamp(vRadii, vec4(0.0), vec4(min(innerHalf.x, innerHalf.y)));
                float d = sdRoundedBox(p, innerHalf, shadowRadii);
                float t = clamp(1.0 - smoothstep(-softness * 0.35, softness, d), 0.0, 1.0);
                float alpha = vColorTop.a * (t * t);
                if (alpha <= 0.002) discard;
                fragColor = vec4(vColorTop.rgb, alpha);
                return;
            }

            float d = sdRoundedBox(p, halfSize, radii);
            float outerAlpha = 1.0 - smoothstep(-0.65, 0.65, d);
            if (outerAlpha <= 0.001) discard;

            vec4 fillCol = mix(vColorTop, vColorBot, clamp(vLocalNorm.y, 0.0, 1.0));
            if (borderW > 0.0) {
                float borderMask = smoothstep(-borderW - 0.65, -borderW + 0.65, d);
                if (fillCol.a <= 0.001) {
                    fillCol.rgb = vBorderCol.rgb;
                }
                vec4 col = mix(fillCol, vBorderCol, borderMask);
                fragColor = vec4(col.rgb, col.a * outerAlpha);
            } else {
                fragColor = vec4(fillCol.rgb, fillCol.a * outerAlpha);
            }
        }
    """.trimIndent()

    private fun ensureInit(): Boolean {
        if (initialized) return true
        if (initFailed) return false
        return try {
            val vert = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER)
            val frag = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
            val prog = GL20.glCreateProgram()
            GL20.glAttachShader(prog, vert)
            GL20.glAttachShader(prog, frag)

            GL20.glBindAttribLocation(prog, 0, "aPos")
            GL20.glBindAttribLocation(prog, 1, "aLocalNorm")
            GL20.glBindAttribLocation(prog, 2, "aTexUv")
            GL20.glBindAttribLocation(prog, 3, "aRectSize")
            GL20.glBindAttribLocation(prog, 4, "aRadii")
            GL20.glBindAttribLocation(prog, 5, "aParams")
            GL20.glBindAttribLocation(prog, 6, "aColorTop")
            GL20.glBindAttribLocation(prog, 7, "aColorBot")
            GL20.glBindAttribLocation(prog, 8, "aBorderCol")

            GL20.glLinkProgram(prog)
            if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                val log = GL20.glGetProgramInfoLog(prog)
                GL20.glDeleteShader(vert)
                GL20.glDeleteShader(frag)
                GL20.glDeleteProgram(prog)
                throw IllegalStateException("HD UI shader link failed: $log")
            }
            GL20.glDeleteShader(vert)
            GL20.glDeleteShader(frag)

            programId = prog
            uProjectionLoc = GL20.glGetUniformLocation(prog, "uProjection")
            uTextureLoc = GL20.glGetUniformLocation(prog, "uTexture")

            vaoId = GL30.glGenVertexArrays()
            vboId = GL15.glGenBuffers()
            eboId = GL15.glGenBuffers()

            GlStateManager._glBindVertexArray(vaoId)
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId)
            GL15.glBufferData(
                GL15.GL_ARRAY_BUFFER,
                (MAX_QUADS * VERTICES_PER_QUAD * STRIDE_BYTES).toLong(),
                GL15.GL_STREAM_DRAW
            )

            val indices = MemoryUtil.memAllocInt(MAX_QUADS * INDICES_PER_QUAD)
            try {
                for (q in 0 until MAX_QUADS) {
                    val base = q * 4
                    indices.put(base + 0)
                    indices.put(base + 1)
                    indices.put(base + 2)
                    indices.put(base + 2)
                    indices.put(base + 3)
                    indices.put(base + 0)
                }
                indices.flip()
                GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId)
                GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW)
            } finally {
                MemoryUtil.memFree(indices)
            }

            fun attrib(index: Int, size: Int, offsetFloats: Int) {
                GL20.glEnableVertexAttribArray(index)
                GL20.glVertexAttribPointer(
                    index, size, GL11.GL_FLOAT, false, STRIDE_BYTES,
                    (offsetFloats * java.lang.Float.BYTES).toLong()
                )
            }
            attrib(0, 2, 0)
            attrib(1, 2, 2)
            attrib(2, 2, 4)
            attrib(3, 2, 6)
            attrib(4, 4, 8)
            attrib(5, 4, 12)
            attrib(6, 4, 16)
            attrib(7, 4, 20)
            attrib(8, 4, 24)

            GlStateManager._glBindVertexArray(0)
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0)
            BufferUploader.invalidate()

            vertexBuffer = MemoryUtil.memAllocFloat(MAX_QUADS * VERTICES_PER_QUAD * FLOATS_PER_VERTEX)
            projBuffer = MemoryUtil.memAllocFloat(16)
            initialized = true
            true
        } catch (t: Throwable) {
            initFailed = true
            CobblemonSmartphone.LOGGER.error("Failed to initialize HD UI shader", t)
            false
        }
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GL20.glCreateShader(type)
        GL20.glShaderSource(shader, src)
        GL20.glCompileShader(shader)
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            val log = GL20.glGetShaderInfoLog(shader)
            GL20.glDeleteShader(shader)
            throw IllegalStateException("Shader compile error: $log")
        }
        return shader
    }

    fun beginFrame(windowWidth: Int, windowHeight: Int) {
        activeWindowW = windowWidth.coerceAtLeast(1)
        activeWindowH = windowHeight.coerceAtLeast(1)
        quadCount = 0
        currentTextureId = 0
        vertexBuffer?.clear()
    }

    fun pushQuad(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        u0: Float = 0f,
        v0: Float = 0f,
        u1: Float = 1f,
        v1: Float = 1f,
        rTL: Float = 0f,
        rTR: Float = 0f,
        rBR: Float = 0f,
        rBL: Float = 0f,
        borderW: Float = 0f,
        softness: Float = 0f,
        mode: Float = MODE_ROUNDED_RECT,
        colorTop: Int,
        colorBottom: Int = colorTop,
        borderColor: Int = 0,
        textureId: Int = 0
    ) {
        if (w <= 0f || h <= 0f) return
        if (!ensureInit()) return

        if (textureId != currentTextureId && quadCount > 0) {
            flush()
        }
        if (quadCount >= MAX_QUADS) {
            flush()
        }
        currentTextureId = textureId

        val buf = vertexBuffer ?: return
        val topR = ((colorTop ushr 16) and 0xFF) / 255f
        val topG = ((colorTop ushr 8) and 0xFF) / 255f
        val topB = (colorTop and 0xFF) / 255f
        val topA = ((colorTop ushr 24) and 0xFF) / 255f

        val botR = ((colorBottom ushr 16) and 0xFF) / 255f
        val botG = ((colorBottom ushr 8) and 0xFF) / 255f
        val botB = (colorBottom and 0xFF) / 255f
        val botA = ((colorBottom ushr 24) and 0xFF) / 255f

        val brdR = ((borderColor ushr 16) and 0xFF) / 255f
        val brdG = ((borderColor ushr 8) and 0xFF) / 255f
        val brdB = (borderColor and 0xFF) / 255f
        val brdA = ((borderColor ushr 24) and 0xFF) / 255f

        fun putVertex(vx: Float, vy: Float, lx: Float, ly: Float, u: Float, v: Float) {
            buf.put(vx).put(vy)
            buf.put(lx).put(ly)
            buf.put(u).put(v)
            buf.put(w).put(h)
            buf.put(rTL).put(rTR).put(rBR).put(rBL)
            buf.put(borderW).put(softness).put(mode).put(0f)
            buf.put(topR).put(topG).put(topB).put(topA)
            buf.put(botR).put(botG).put(botB).put(botA)
            buf.put(brdR).put(brdG).put(brdB).put(brdA)
        }

        putVertex(x, y, 0f, 0f, u0, v0)
        putVertex(x + w, y, 1f, 0f, u1, v0)
        putVertex(x + w, y + h, 1f, 1f, u1, v1)
        putVertex(x, y + h, 0f, 1f, u0, v1)
        quadCount++
    }

    fun flush() {
        if (quadCount == 0 || !initialized) return
        val vBuf = vertexBuffer ?: return
        val pBuf = projBuffer ?: return

        vBuf.flip()

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableDepthTest()
        RenderSystem.disableCull()

        // Always route program, texture, VAO, and buffer binds through Blaze3D's GlStateManager
        // so Minecraft's cached GL state (used by FontRenderer, Iris, Sodium, ImmediatelyFast,
        // and 3D entity/Pokémon rendering) never goes out of sync with the real OpenGL driver.
        GlStateManager._glUseProgram(programId)

        val ortho = Matrix4f().setOrtho(
            0f,
            activeWindowW.toFloat(),
            activeWindowH.toFloat(),
            0f,
            -1f,
            1f
        )
        pBuf.clear()
        ortho.get(pBuf)
        GL20.glUniformMatrix4fv(uProjectionLoc, false, pBuf)
        GL20.glUniform1i(uTextureLoc, 0)

        GlStateManager._activeTexture(GL13.GL_TEXTURE0)
        GlStateManager._bindTexture(currentTextureId)

        GlStateManager._glBindVertexArray(vaoId)
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId)
        GL15.glBufferData(
            GL15.GL_ARRAY_BUFFER,
            (MAX_QUADS * VERTICES_PER_QUAD * STRIDE_BYTES).toLong(),
            GL15.GL_STREAM_DRAW
        )
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, vBuf)

        GL11.glDrawElements(GL11.GL_TRIANGLES, quadCount * INDICES_PER_QUAD, GL11.GL_UNSIGNED_INT, 0L)

        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0)
        GlStateManager._glBindVertexArray(0)
        GlStateManager._glUseProgram(0)
        GlStateManager._bindTexture(0)
        RenderSystem.enableDepthTest()
        RenderSystem.enableCull()
        BufferUploader.invalidate()

        vBuf.clear()
        quadCount = 0
    }
}
