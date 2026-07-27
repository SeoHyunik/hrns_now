package io.hrns_now.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.hrns_now.app.presentation.model.ActionButtonModel
import io.hrns_now.app.presentation.model.CockpitActionItem
import io.hrns_now.app.presentation.model.InfoCardModel
import io.hrns_now.app.presentation.model.StatusChipModel
import io.hrns_now.core.domain.model.UiAction

// 토큰
private val RadiusSm = 8.dp
private val RadiusMd = 12.dp
private val RadiusLg = 18.dp
private val RadiusPill = 999.dp

private data class ToneColors(val container: Color, val text: Color, val dot: Color)

@Composable
private fun toneColors(tone: String): ToneColors {
    val c = LocalHrnsColors.current
    return when (tone.lowercase()) {
        "warning" -> ToneColors(c.warningSoft, c.warning, c.warning)
        "success", "ok" -> ToneColors(c.successSoft, c.success, c.success)
        "danger", "error", "fail" -> ToneColors(c.dangerSoft, c.danger, c.danger)
        "info" -> ToneColors(c.infoSoft, c.info, c.info)
        "accent" -> ToneColors(c.accentSoft, c.accent, c.accent)
        "muted" -> ToneColors(c.surfaceMuted, c.tertiaryText, c.tertiaryText)
        else -> ToneColors(c.surfaceMuted, c.secondaryText, c.tertiaryText)
    }
}

// ─── 상태 칩 ──────────────────────────────────────────────────────────────────

@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: String = "neutral",
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    showDot: Boolean = true,
) {
    val tc = toneColors(tone)
    val container = if (containerColor == Color.Unspecified) tc.container else containerColor
    val content = if (contentColor == Color.Unspecified) tc.text else contentColor

    Box(
        modifier = modifier
            .background(container, RoundedCornerShape(RadiusPill))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showDot) {
                Box(modifier = Modifier.size(6.dp).background(tc.dot, CircleShape))
                Box(modifier = Modifier.width(7.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                fontWeight = FontWeight.Medium,
                color = content,
            )
        }
    }
}

@Composable
fun StatusChip(model: StatusChipModel, modifier: Modifier = Modifier) {
    StatusChip(text = model.displayText(), modifier = modifier, tone = model.tone)
}

// ─── 사이드바 행 ──────────────────────────────────────────────────────────────
//  Card(onClick) 가 Light 모드에서 hover 시 ripple/highlight 가
//  RoundedCornerShape 외곽으로 새는 문제가 있어, Box+clip+clickable 로 교체.
//  자체 hoverable 로 깔끔한 hover bg + 좌측 dot 페이드 효과 구현.

@Composable
fun NavigationButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingGlyph: String? = null,
    trailing: String? = null,
) {
    val colors = LocalHrnsColors.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val container = when {
        selected -> colors.surfaceElevated
        hovered -> colors.surfaceHover
        else -> Color.Transparent
    }
    val content = if (selected) colors.primaryText else colors.secondaryText
    val border = if (selected) {
        BorderStroke(1.dp, colors.chelseaBlue.copy(alpha = 0.30f))
    } else null

    val shadowAlpha by animateFloatAsState(
        targetValue = if (selected || hovered) 0.18f else 0f,
        label = "navShadow",
    )
    val lift by animateDpAsState(
        targetValue = if (hovered && !selected) (-1).dp else 0.dp,
        label = "navLift",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = lift)
            .shadow(
                elevation = if (selected || hovered) 6.dp else 0.dp,
                shape = RoundedCornerShape(RadiusMd),
                ambientColor = colors.chelseaBlue.copy(alpha = shadowAlpha),
                spotColor = colors.chelseaBlue.copy(alpha = shadowAlpha),
            )
            .clip(RoundedCornerShape(RadiusMd))
            .background(container, RoundedCornerShape(RadiusMd))
            .let { if (border != null) it.border1(border, RoundedCornerShape(RadiusMd)) else it }
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            // 선택/hover dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        when {
                            selected -> colors.accent
                            hovered -> colors.accent.copy(alpha = 0.45f)
                            else -> Color.Transparent
                        },
                        CircleShape,
                    ),
            )
            Box(modifier = Modifier.width(12.dp))

            if (leadingGlyph != null) {
                Text(
                    text = leadingGlyph,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.4.sp,
                    ),
                    color = colors.tertiaryText,
                )
                Box(modifier = Modifier.width(12.dp))
            }

            Text(
                text = text,
                color = content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    letterSpacing = (-0.1).sp,
                ),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )

            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.tertiaryText,
                )
            }
        }
    }
}

// 작은 helper: border modifier 를 인라인으로 적용
private fun Modifier.border1(border: BorderStroke, shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.border(border, shape)

// ─── 섹션 카드 (hover lift + chelsea glow) ────────────────────────────────────

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
    eyebrow: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
    hoverable: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = LocalHrnsColors.current
    val container = if (warning) colors.warningSoft else colors.cardBackground
    val borderColor = if (warning) colors.warningBorder else colors.chelseaBlueSoft

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val isHovered = hoverable && hovered

    val elevation by animateDpAsState(
        targetValue = if (isHovered) 14.dp else 4.dp,
        label = "cardElev",
    )
    val lift by animateDpAsState(
        targetValue = if (isHovered) (-3).dp else 0.dp,
        label = "cardLift",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isHovered) 0.45f else 0.18f,
        label = "cardGlow",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = lift)
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(RadiusLg),
                ambientColor = colors.chelseaBlue.copy(alpha = glowAlpha),
                spotColor = colors.chelseaBlue.copy(alpha = glowAlpha),
            )
            .background(container, RoundedCornerShape(RadiusLg))
            .border1(BorderStroke(1.dp, borderColor), RoundedCornerShape(RadiusLg))
            .clip(RoundedCornerShape(RadiusLg))
            .hoverable(interaction),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    if (eyebrow != null) {
                        Text(
                            text = eyebrow.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp, fontSize = 10.5.sp),
                            color = colors.tertiaryText,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Box(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, letterSpacing = (-0.2).sp),
                        fontWeight = FontWeight.SemiBold,
                        color = if (warning) colors.warning else colors.primaryText,
                    )
                }
                if (trailing != null) trailing()
            }
            content()
        }
    }
}

// ─── 정보 카드 (projection) ──────────────────────────────────────────────────

@Composable
fun ProjectionInfoCard(card: InfoCardModel, modifier: Modifier = Modifier, warning: Boolean = false) {
    val colors = LocalHrnsColors.current
    SectionCard(title = card.title, modifier = modifier, warning = warning) {
        Column {
            card.rows.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.borderSubtle))
                    Box(modifier = Modifier.height(14.dp))
                }
                PlaceholderRow(label, value)
                if (index < card.rows.lastIndex) Box(modifier = Modifier.height(14.dp))
            }
        }
    }
}

// ─── 라벨 / 값 행 ────────────────────────────────────────────────────────────

@Composable
fun PlaceholderRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    monospaceValue: Boolean = false,
) {
    val colors = LocalHrnsColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = colors.tertiaryText,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (monospaceValue) FontFamily.Monospace else MaterialTheme.typography.bodyMedium.fontFamily,
                fontSize = 14.5.sp,
                letterSpacing = (-0.1).sp,
            ),
            fontWeight = FontWeight.Medium,
            color = colors.primaryText,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

// ─── 액션 버튼 ───────────────────────────────────────────────────────────────

@Composable
fun PlaceholderActionButton(
    text: String,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = false,
    onClick: () -> Unit = {},
) {
    val colors = LocalHrnsColors.current
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = RoundedCornerShape(RadiusMd),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
                disabledContainerColor = colors.surfaceElevated,
                disabledContentColor = colors.tertiaryText,
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
        ) {
            Text(text = text, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp))
        }
    } else {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = RoundedCornerShape(RadiusMd),
            colors = ButtonDefaults.textButtonColors(
                containerColor = colors.surfaceElevated,
                contentColor = colors.primaryText,
                disabledContainerColor = colors.surfaceMuted,
                disabledContentColor = colors.tertiaryText,
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
        ) {
            Text(text = text, fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp))
        }
    }
}

@Composable
fun PlaceholderActionButton(
    action: ActionButtonModel,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = LocalHrnsColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PlaceholderActionButton(
            text = action.label,
            primary = primary,
            modifier = modifier,
            enabled = action.enabled,
            onClick = onClick,
        )
        action.helperText?.let { helper ->
            Text(
                text = helper,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.tertiaryText,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionButtonGroup(
    actions: List<ActionButtonModel>,
    onAction: (UiAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        actions.forEachIndexed { index, action ->
            PlaceholderActionButton(
                action = action,
                primary = index == 0,
                onClick = { action.action?.let(onAction) },
            )
        }
    }
}

// ─── 인라인 칩 그룹 ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CockpitActionButtonGroup(
    actions: List<CockpitActionItem>,
    onAction: (UiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        actions.forEachIndexed { index, item ->
            PlaceholderActionButton(
                text = item.label,
                primary = index == 0,
                enabled = item.enabled,
                onClick = { onAction(item.action) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InlineChips(chips: List<StatusChipModel>, modifier: Modifier = Modifier, showDot: Boolean = true) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { chip -> StatusChip(text = chip.displayText(), tone = chip.tone, showDot = showDot) }
    }
}

private fun StatusChipModel.displayText(): String =
    if (value.isBlank()) label else "$label · $value"

// ─── 입력 필드 (Registry 등록 폼) ────────────────────────────────────────────

@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
) {
    val colors = LocalHrnsColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp)) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = if (monospace) FontFamily.Monospace else MaterialTheme.typography.bodyMedium.fontFamily,
            fontSize = 13.5.sp,
        ),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.primaryText,
            unfocusedTextColor = colors.primaryText,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.borderStrong,
        ),
    )
}
