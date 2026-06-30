package com.wswensw.template.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.wswensw.template.R

@Composable
fun ComponentsRoute(
    onBack: () -> Unit,
    onOpenAction: () -> Unit,
) {
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

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "Actions") {
            TemplateListItem(
                title = "Adaptive primary action",
                subtitle = "Test the right-bottom action with scrolling content and input fields.",
                iconRes = R.drawable.ic_edit_24,
                onClick = onOpenAction,
            )
        }
    }
}

@Composable
fun ActionRoute(onBack: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("Template action") }
    var note by rememberSaveable { mutableStateOf("Input stays usable while the action avoids the keyboard area.") }
    var clickCount by rememberSaveable { mutableIntStateOf(0) }
    val showErrorState = clickCount % 2 == 1

    TemplateRouteScaffold(
        title = "Primary Action",
        onBack = onBack,
        mainAction = TemplatePageAction(
            text = if (showErrorState) "Error $clickCount" else "Apply $clickCount",
            iconRes = if (showErrorState) R.drawable.ic_error_24 else R.drawable.ic_check_24,
            containerColor = if (showErrorState) MaterialTheme.colorScheme.error else Color(0xFF0B57D0),
            contentColor = if (showErrorState) MaterialTheme.colorScheme.onError else Color.White,
            onClick = {
                val nextCount = clickCount + 1
                clickCount = nextCount
                val nextState = if (nextCount % 2 == 1) "Error" else "Ready"
                note = "$nextState state after $nextCount click${if (nextCount == 1) "" else "s"}."
            },
        ),
    ) {
        TemplateCard(
            title = "Adaptive action",
            body = "At the page top the button shows icon and text. Away from the top it keeps the same action as a compact icon button.",
            iconRes = R.drawable.ic_edit_24,
        )

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "Input") {
            Spacer(Modifier.height(14.dp))
            TemplateInputField(
                value = title,
                onValueChange = { title = it },
                label = "Action title",
                supportingText = "Use the keyboard to verify the bottom action keeps clear of input.",
            )
            Spacer(Modifier.height(12.dp))
            TemplateInputField(
                value = note,
                onValueChange = { note = it },
                label = "Action note",
            )
            Spacer(Modifier.height(14.dp))
        }

        Spacer(Modifier.height(12.dp))

        TemplateSection(title = "State") {
            TemplateMetricRow(label = "Button clicks", value = clickCount.toString())
            TemplateDivider()
            TemplateMetricRow(label = "Title length", value = title.length.toString())
        }

        repeat(6) { index ->
            Spacer(Modifier.height(12.dp))
            TemplateOutlineCard(
                title = "Scrollable block ${index + 1}",
                body = "Additional content keeps the page scrollable so the primary action can switch between top and non-top forms.",
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
