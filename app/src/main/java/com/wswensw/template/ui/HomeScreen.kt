package com.wswensw.template.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wswensw.template.R

@Composable
fun HomeScreen(
    onOpenComponents: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenExtension: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TemplatePage(
        title = "Template",
        subtitle = "A compact Compose starter for preferred page shells, cards, and rows.",
        modifier = modifier,
    ) {
        TemplateCard(
            title = "Demo workspace",
            body = "Use this page as the first screen for future tools. Keep the surface quiet, dense, and easy to scan.",
            iconRes = R.drawable.ic_dashboard_24,
        )

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "Component Samples") {
            TemplateListItem(
                title = "Grouped list row",
                subtitle = "Icon, title, subtitle, and a stable full-width row surface.",
                iconRes = R.drawable.ic_view_quilt_24,
                onClick = onOpenComponents,
            )
            TemplateDivider()
            TemplateListItem(
                title = "Tonal hero card",
                subtitle = "A single highlighted card for the page's primary state.",
                iconRes = R.drawable.ic_lightbulb_24,
                onClick = onOpenComponents,
            )
            TemplateDivider()
            TemplateListItem(
                title = "Dynamic color",
                subtitle = "Material 3 color follows the device palette when available.",
                iconRes = R.drawable.ic_color_lens_24,
                onClick = onOpenComponents,
            )
        }

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "Template Rules") {
            TemplateMetricRow(label = "Bottom tabs", value = "2")
            TemplateDivider()
            TemplateMetricRow(label = "Primary pages", value = "Home / Setting")
            TemplateDivider()
            TemplateMetricRow(label = "UI baseline", value = "Material 3")
        }

        Spacer(Modifier.height(12.dp))

        TemplateOutlineCard(
            title = "Extension point",
            body = "Add real features by replacing this content area while preserving the page shell and settings section pattern.",
            onClick = onOpenExtension,
        )

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "Readiness") {
            TemplateListItem(
                title = "Initial template complete",
                subtitle = "The app has a clean shell, two bottom tabs, and reusable style primitives.",
                iconRes = R.drawable.ic_check_24,
                onClick = onOpenRules,
            )
        }
    }
}
