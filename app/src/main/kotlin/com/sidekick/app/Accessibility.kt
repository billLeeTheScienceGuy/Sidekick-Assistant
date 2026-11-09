package com.sidekick.app

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Give separately rendered selection controls a usable TalkBack label. */
internal fun Modifier.controlLabel(label: String) = semantics { contentDescription = label }
