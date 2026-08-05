package com.wswensw.woolbox.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed interface UiText {
    data class Resource(
        @param:StringRes val resourceId: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    data class Dynamic(val value: String) : UiText
}

fun uiText(@StringRes resourceId: Int, vararg args: Any): UiText =
    UiText.Resource(resourceId, args.toList())

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Dynamic -> value
    is UiText.Resource -> {
        val resolvedArgs = args.map { argument ->
            if (argument is UiText) argument.asString() else argument
        }.toTypedArray()
        stringResource(resourceId, *resolvedArgs)
    }
}
