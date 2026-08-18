package com.wswensw.woolbox.record.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.TaskRepeatMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskRepeatModeField(
    value: TaskRepeatMode,
    onChange: (TaskRepeatMode) -> Unit,
    enabled: Boolean,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.formField()) {
        TaskRepeatMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = value == mode,
                onClick = { onChange(mode) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, TaskRepeatMode.entries.size),
                label = {
                    Text(stringResource(if (mode == TaskRepeatMode.ONCE) R.string.task_once else R.string.task_daily))
                },
            )
        }
    }
}
