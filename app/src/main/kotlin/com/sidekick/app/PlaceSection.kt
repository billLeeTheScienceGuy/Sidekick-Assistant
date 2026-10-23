package com.sidekick.app

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay

@Composable
internal fun PlaceSection(name: String, address: String, onName: (String) -> Unit, onAddress: (String) -> Unit) {
    val context = LocalContext.current
    val settings = (context.applicationContext as SidekickApplication).guidance
    val automatic by settings.automaticPlaces.collectAsState()
    val city by settings.city.collectAsState()
    val locationMode by settings.placeContext.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var results by remember(name, city, locationMode, automatic) { mutableStateOf(PlaceResults()) }
    var working by remember { mutableStateOf(false) }
    var manualRequest by remember(name, city, locationMode, address.isBlank()) { mutableIntStateOf(0) }
    LaunchedEffect(name, city, locationMode, automatic, address.isBlank(), manualRequest) {
        results = PlaceResults()
        if ((automatic || manualRequest > 0) && name.isNotBlank() && address.isBlank()) {
            delay(700)
            working = true
            try { results = PlaceLookup(context).suggest(name, city, locationMode == PlaceContext.PHONE) }
            finally { working = false }
        }
    }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("optional-place")) { Text("Place (optional): ${name.ifBlank { "Unknown" }}") }
    if (address.isNotBlank()) Text(address)
    if (expanded) {
        Text("Changing the venue clears its old address. Confirm the new address separately.")
        OutlinedTextField(name, { if (it.length <= 200) onName(it) }, label = { Text("Place as mentioned") }, placeholder = { Text("Unknown") }, modifier = Modifier.testTag("place-name"))
        OutlinedTextField(address, { if (it.length <= 500) onAddress(it) }, label = { Text("Address, if known") }, placeholder = { Text("Unknown") }, modifier = Modifier.testTag("place-address"))
        TextButton(onClick = { onName(""); onAddress("") }) { Text("Place unknown") }
    }
    if (working) Text("Looking for possible addresses…")
    if (!automatic && name.isNotBlank() && address.isBlank())
        TextButton(enabled = !working, onClick = { manualRequest++ }) { Text("Look up address") }
    if (results.note.isNotBlank()) Text(results.note)
    results.addresses.forEach { candidate ->
        Text(candidate)
        TextButton(onClick = { onAddress(candidate) }) { Text("Use this address") }
    }
    if (name.isNotBlank()) TextButton(onClick = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, PlaceLookup.mapsUri(address.ifBlank { name }, if (address.isBlank() && locationMode == PlaceContext.CITY) city else ""))) }
            .onFailure { results = PlaceResults(note = "No Maps app or browser could open this place.") }
    }) { Text("Check in Google Maps") }
}
