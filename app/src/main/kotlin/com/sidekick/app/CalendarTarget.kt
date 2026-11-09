package com.sidekick.app

import android.net.Uri

internal object CalendarTarget {
    fun uri(packageName: String, id: String): String = Uri.Builder().scheme("sidekick").authority(packageName)
        .appendPath("event").appendPath(id).build().toString()
    fun parse(uri: Uri?, packageName: String): String? = uri?.takeIf {
        it.scheme == "sidekick" && it.authority == packageName && it.pathSegments.size == 2 &&
            it.pathSegments[0] == "event" && it.query == null && it.fragment == null
    }?.lastPathSegment?.takeIf { id -> id.length in 1..100 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' } }
}
