package com.wswensw.template.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wswensw.template.R

@Composable
fun ComponentsRoute(onBack: () -> Unit) {
    TemplateRouteScaffold(
        title = "Components",
        onBack = onBack,
    ) {
        TemplateSection(title = "Rows") {
            TemplateListItem(
                title = "Full-row interaction",
                subtitle = "Rows expose the whole surface as the touch target when an action exists.",
                iconRes = R.drawable.ic_view_quilt_24,
            )
            TemplateDivider()
            TemplateListItem(
                title = "Trailing controls",
                subtitle = "Switches, buttons, and values stay inside the same row rhythm.",
                iconRes = R.drawable.ic_tune_24,
            )
        }

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "Dialogs") {
            TemplateListItem(
                title = "Single choice",
                subtitle = "Use for small mutually exclusive option sets.",
                iconRes = R.drawable.ic_check_24,
            )
            TemplateDivider()
            TemplateListItem(
                title = "Text input",
                subtitle = "Use for short template names, labels, and user-editable values.",
                iconRes = R.drawable.ic_edit_24,
            )
            TemplateDivider()
            TemplateListItem(
                title = "Information",
                subtitle = "Use for version, about, and static reference details.",
                iconRes = R.drawable.ic_info_24,
            )
        }
    }
}

@Composable
fun RulesRoute(onBack: () -> Unit) {
    TemplateRouteScaffold(
        title = "Template Rules",
        onBack = onBack,
    ) {
        TemplateSection(title = "Navigation") {
            TemplateMetricRow(label = "Top tabs", value = "Home / Setting")
            TemplateDivider()
            TemplateMetricRow(label = "Child pages", value = "Sibling routes")
            TemplateDivider()
            TemplateMetricRow(label = "Transition", value = "Horizontal slide")
        }

        Spacer(Modifier.height(12.dp))

        TemplateOutlineCard(
            title = "Page rule",
            body = "Root screens keep the bottom bar. Child pages use a top bar, back action, and the same content rhythm.",
        )
    }
}

@Composable
fun ExtensionRoute(onBack: () -> Unit) {
    TemplateRouteScaffold(
        title = "Extension",
        onBack = onBack,
    ) {
        TemplateCard(
            title = "Feature placeholder",
            body = "Replace this route with a real tool page while keeping the shared scaffold, rows, dialogs, and route transition.",
            iconRes = R.drawable.ic_dashboard_24,
        )

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "Next additions") {
            TemplateListItem(
                title = "Empty state",
                subtitle = "Add a shared blank-state component when a real feature needs it.",
                iconRes = R.drawable.ic_article_24,
            )
            TemplateDivider()
            TemplateListItem(
                title = "Page action",
                subtitle = "Add a page-level primary action when a workflow needs one.",
                iconRes = R.drawable.ic_lightbulb_24,
            )
        }
    }
}

@Composable
fun SettingsGuideRoute(onBack: () -> Unit) {
    TemplateRouteScaffold(
        title = "Settings Pattern",
        onBack = onBack,
    ) {
        TemplateSection(title = "Rows") {
            TemplateListItem(
                title = "Tap target",
                subtitle = "Each setting row opens a dialog, toggles a value, or navigates to detail.",
                iconRes = R.drawable.ic_view_quilt_24,
            )
            TemplateDivider()
            TemplateListItem(
                title = "Dialog choice",
                subtitle = "Use dialogs for compact local edits inside settings.",
                iconRes = R.drawable.ic_info_24,
            )
        }
    }
}
