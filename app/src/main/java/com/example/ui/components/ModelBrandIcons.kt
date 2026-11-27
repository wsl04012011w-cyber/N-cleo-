package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ícones autênticos de cada família de modelos (Liquid AI LFM, Qwen, Google Gemma, Meta Llama, Microsoft Phi).
 */
@Composable
fun ModelBrandIcon(
    family: String,
    size: Dp = 20.dp,
    tintFallback: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    when {
        family.equals("LFM", ignoreCase = true) -> LiquidPrismIcon(color = tintFallback, size = size, modifier = modifier)
        family.equals("Qwen", ignoreCase = true) -> QwenStarIcon(size = size, modifier = modifier)
        family.equals("Gemma", ignoreCase = true) -> GoogleGIcon(size = size, modifier = modifier)
        family.equals("Llama", ignoreCase = true) -> MetaInfinityIcon(size = size, modifier = modifier)
        family.equals("Phi", ignoreCase = true) -> MicrosoftGridIcon(size = size, modifier = modifier)
        else -> NeutralModelChipIcon(color = tintFallback, size = size, modifier = modifier)
    }
}

@Composable
fun NeutralModelChipIcon(
    color: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.40f
        val hex = Path().apply {
            for (i in 0..5) {
                val angle = Math.toRadians((-90 + i * 60).toDouble())
                val x = cx + (r * cos(angle)).toFloat()
                val y = cy + (r * sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(
            path = hex,
            color = color,
            style = Stroke(width = w * 0.105f, join = StrokeJoin.Round)
        )
        drawCircle(
            color = color,
            radius = w * 0.12f,
            center = Offset(cx, cy)
        )
    }
}

@Composable
fun LiquidPrismIcon(
    color: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val outer = Path().apply {
            moveTo(w * 0.50f, h * 0.06f)
            lineTo(w * 0.86f, h * 0.58f)
            lineTo(w * 0.50f, h * 0.94f)
            lineTo(w * 0.14f, h * 0.58f)
            close()
        }
        drawPath(
            path = outer,
            color = color,
            style = Stroke(width = w * 0.10f, join = StrokeJoin.Round)
        )
        drawLine(
            color = color,
            start = Offset(w * 0.50f, h * 0.08f),
            end = Offset(w * 0.50f, h * 0.92f),
            strokeWidth = w * 0.085f
        )
        drawLine(
            color = color,
            start = Offset(w * 0.16f, h * 0.58f),
            end = Offset(w * 0.50f, h * 0.44f),
            strokeWidth = w * 0.08f
        )
        drawLine(
            color = color,
            start = Offset(w * 0.84f, h * 0.58f),
            end = Offset(w * 0.50f, h * 0.44f),
            strokeWidth = w * 0.08f
        )
    }
}

@Composable
fun QwenStarIcon(
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.42f
        val purple = Color(0xFF7C6BFF)

        val tri1 = Path().apply {
            for (i in 0..2) {
                val angle = Math.toRadians((-90 + i * 120).toDouble())
                val x = cx + (r * cos(angle)).toFloat()
                val y = cy + (r * sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        val tri2 = Path().apply {
            for (i in 0..2) {
                val angle = Math.toRadians((90 + i * 120).toDouble())
                val x = cx + (r * cos(angle)).toFloat()
                val y = cy + (r * sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        drawPath(
            path = tri1,
            color = purple,
            style = Stroke(width = w * 0.12f, join = StrokeJoin.Round)
        )
        drawPath(
            path = tri2,
            color = purple.copy(alpha = 0.88f),
            style = Stroke(width = w * 0.12f, join = StrokeJoin.Round)
        )
        rotate(degrees = 30f, pivot = Offset(cx, cy)) {
            drawCircle(
                color = purple.copy(alpha = 0.28f),
                radius = w * 0.13f,
                center = Offset(cx, cy)
            )
        }
    }
}

@Composable
fun GoogleGIcon(
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val strokeWidth = w * 0.21f
        val inset = strokeWidth / 2f + w * 0.05f
        val arcRect = Rect(inset, inset, w - inset, w - inset)

        // Vermelho (topo)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = -145f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Amarelo (esquerda)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 135f,
            sweepAngle = 82f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Verde (base)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 42f,
            sweepAngle = 96f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Azul (direita inferior + barra do G)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -8f,
            sweepAngle = 54f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(w * 0.52f, w * 0.50f),
            end = Offset(w * 0.92f, w * 0.50f),
            strokeWidth = strokeWidth * 0.92f,
            cap = StrokeCap.Butt
        )
    }
}

@Composable
fun MetaInfinityIcon(
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val metaBlue = Color(0xFF0082FB)
        val path = Path().apply {
            moveTo(w * 0.50f, h * 0.50f)
            cubicTo(w * 0.34f, h * 0.18f, w * 0.08f, h * 0.28f, w * 0.10f, h * 0.56f)
            cubicTo(w * 0.12f, h * 0.80f, w * 0.36f, h * 0.78f, w * 0.50f, h * 0.50f)
            cubicTo(w * 0.64f, h * 0.22f, w * 0.88f, h * 0.20f, w * 0.90f, h * 0.44f)
            cubicTo(w * 0.92f, h * 0.72f, w * 0.66f, h * 0.82f, w * 0.50f, h * 0.50f)
        }
        drawPath(
            path = path,
            color = metaBlue,
            style = Stroke(width = w * 0.125f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun MicrosoftGridIcon(
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val gap = w * 0.08f
        val pad = w * 0.08f
        val cell = (w - pad * 2f - gap) / 2f

        drawRect(color = Color(0xFFF25022), topLeft = Offset(pad, pad), size = Size(cell, cell))
        drawRect(color = Color(0xFF7FBA00), topLeft = Offset(pad + cell + gap, pad), size = Size(cell, cell))
        drawRect(color = Color(0xFF00A4EF), topLeft = Offset(pad, pad + cell + gap), size = Size(cell, cell))
        drawRect(color = Color(0xFFFFB900), topLeft = Offset(pad + cell + gap, pad + cell + gap), size = Size(cell, cell))
    }
}
