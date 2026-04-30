package io.hrns_now.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.hrns_now.core.projection.ActionButtonModel
import io.hrns_now.core.projection.InfoCardModel
import io.hrns_now.core.projection.StatusChipModel

@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: String = "neutral",
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    val colors = LocalHrnsColors.current
    val toneContainer = when (tone) {
        "warning" -> colors.warningBackground
        else -> colors.accentSoft
    }
    val toneBorder = when (tone) {
        "warning" -> colors.warningBorder
        else -> colors.border
    }
    val resolvedContainer = if (containerColor == Color.Unspecified) toneContainer else containerColor
    val resolvedContent = if (contentColor == Color.Unspecified) colors.primaryText else contentColor

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = resolvedContainer,
        contentColor = resolvedContent,
        border = BorderStroke(1.dp, toneBorder),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun StatusChip(
    model: StatusChipModel,
    modifier: Modifier = Modifier,
) {
    StatusChip(
        text = model.displayText(),
        modifier = modifier,
        tone = model.tone,
    )
}

@Composable
fun NavigationButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHrnsColors.current
    val container = if (selected) colors.accentSoft else colors.panelBackground
    val content = if (selected) colors.primaryText else colors.secondaryText

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(
            1.dp,
            if (selected) colors.accent else colors.border,
        ),
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = LocalHrnsColors.current
    val container = if (warning) colors.warningBackground else colors.cardBackground
    val border = if (warning) colors.warningBorder else colors.border

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, border),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText,
            )
            HorizontalDivider(color = border.copy(alpha = 0.65f))
            content()
        }
    }
}

@Composable
fun ProjectionInfoCard(
    card: InfoCardModel,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
) {
    SectionCard(
        title = card.title,
        modifier = modifier,
        warning = warning,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            card.rows.forEach { (label, value) ->
                PlaceholderRow(label, value)
            }
        }
    }
}

@Composable
fun PlaceholderRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHrnsColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.secondaryText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.primaryText,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun PlaceholderActionButton(
    text: String,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = false,
) {
    val colors = LocalHrnsColors.current

    if (primary) {
        androidx.compose.material3.Button(
            onClick = {},
            enabled = enabled,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = colors.accentSoft,
                disabledContentColor = colors.primaryText,
            ),
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(
            onClick = {},
            enabled = enabled,
            modifier = modifier,
            border = BorderStroke(1.dp, colors.border),
            colors = ButtonDefaults.outlinedButtonColors(
                disabledContentColor = colors.secondaryText,
            ),
        ) {
            Text(text)
        }
    }
}

@Composable
fun PlaceholderActionButton(
    action: ActionButtonModel,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PlaceholderActionButton(
            text = action.label,
            primary = primary,
            modifier = modifier,
            enabled = action.enabled,
        )
        action.helperText?.let { helper ->
            Text(
                text = helper,
                style = MaterialTheme.typography.bodySmall,
                color = LocalHrnsColors.current.secondaryText,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionButtonGroup(
    actions: List<ActionButtonModel>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        actions.forEachIndexed { index, action ->
            PlaceholderActionButton(
                action = action,
                primary = index == 0,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InlineChips(
    chips: List<StatusChipModel>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHrnsColors.current

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            val container = when (chip.tone) {
                "warning" -> colors.warningBackground
                else -> colors.accentSoft
            }
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(chip.displayText()) },
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = container,
                    disabledLabelColor = colors.primaryText,
                ),
            )
        }
    }
}

private fun StatusChipModel.displayText(): String =
    if (value.isBlank()) label else "$label: $value"
