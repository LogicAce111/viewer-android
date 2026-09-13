package com.legion.viewer.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun CategorySearchBar(state: CategorySearchState, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val interactions = remember { MutableInteractionSource() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state, state.focusRequested) {
        if (state.focusRequested) {
            focus.requestFocus()
            keyboard?.show()
            state.focusHandled()
        }
    }
    DisposableEffect(state) { onDispose { state.focusHandled() } }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = state.query,
            onValueChange = state::updateQuery,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).focusRequester(focus)
                .semantics { contentDescription = "搜索名称或目录" },
            singleLine = true,
            interactionSource = interactions,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); keyboard?.hide() }),
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = state.query,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactions,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text("搜索名称或目录", maxLines = 1) },
                    leadingIcon = { Icon(ViewerIcons.Search, null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) IconButton(onClick = { state.updateQuery("") }) {
                            Icon(ViewerIcons.Clear, "清空关键词")
                        }
                    },
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true,
                            isError = false,
                            interactionSource = interactions,
                            shape = RoundedCornerShape(12.dp),
                        )
                    },
                )
            },
        )
        TextButton(onClick = onClose) { Text("关闭") }
    }
}
