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
import com.wswensw.woolbox.record.model.TemplateCadence

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TemplateCadenceField(
    value: TemplateCadence,
    onChange: (TemplateCadence) -> Unit,
    enabled: Boolean,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.formField()) {
        TemplateCadence.entries.forEachIndexed { index, cadence ->
            SegmentedButton(
                selected = value == cadence,
                onClick = { onChange(cadence) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, TemplateCadence.entries.size),
                label = {
                    Text(
                        stringResource(
                            if (cadence == TemplateCadence.MANUAL) {
                                R.string.template_manual
                            } else {
                                R.string.template_daily
                            },
                        ),
                    )
                },
            )
        }
    }
}
