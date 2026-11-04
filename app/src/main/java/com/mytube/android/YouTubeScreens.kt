package com.mytube.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun YouTubeHomeFeed(apiKey: String) {
    var videos by remember { mutableStateOf(emptyList<YouTubeVideo>()) }
    var status by remember { mutableStateOf("YouTube API key সেট করুন Settings-এ, তারপর পাবলিক ট্রেন্ডিং ভিডিও দেখুন।") }
    var selected by remember { mutableStateOf<YouTubeVideo?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        if (apiKey.isBlank()) { status = "Settings থেকে YouTube Data API key যোগ করুন।"; return }
        scope.launch {
            loading = true
            status = "বাংলাদেশের ট্রেন্ডিং ভিডিও লোড হচ্ছে…"
            try {
                videos = YouTubeDataApi.trending(apiKey)
                status = if (videos.isEmpty()) "এখন কোনো ভিডিও পাওয়া যায়নি।" else "বাংলাদেশ • " + videos.size + "টি ভিডিও"
            } catch (e: Exception) { status = e.message ?: "ভিডিও লোড করা যায়নি।" }
            loading = false
        }
    }

    LaunchedEffect(apiKey) { if (apiKey.isNotBlank()) load() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row {
            Text("YouTube • Trending", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { load() }, enabled = !loading) { Text(if (loading) "Loading…" else "Refresh") }
        }
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (videos.isNotEmpty()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(440.dp)) {
                items(videos, key = { it.id }) { video -> YouTubeVideoCard(video) { selected = video } }
            }
        }
    }
    selected?.let { video -> YouTubeVideoInfoDialog(video = video, onDismiss = { selected = null }) }
}

@Composable
fun YouTubeSearchFeed(apiKey: String) {
    var query by remember { mutableStateOf("") }
    var videos by remember { mutableStateOf(emptyList<YouTubeVideo>()) }
    var status by remember { mutableStateOf("YouTube-এর পাবলিক ভিডিও খুঁজুন।") }
    var selected by remember { mutableStateOf<YouTubeVideo?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Search YouTube", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("ভিডিওর নাম বা বিষয়") })
        Button(onClick = {
            if (apiKey.isBlank()) { status = "Settings থেকে YouTube API key যোগ করুন।"; return@Button }
            if (query.isBlank()) { status = "সার্চের শব্দ লিখুন।"; return@Button }
            scope.launch {
                loading = true
                status = "YouTube-এ খোঁজা হচ্ছে…"
                try {
                    videos = YouTubeDataApi.search(apiKey, query)
                    status = videos.size.toString() + "টি ফলাফল"
                } catch (e: Exception) { status = e.message ?: "সার্চ ব্যর্থ হয়েছে।" }
                loading = false
            }
        }, enabled = !loading) { Text(if (loading) "Searching…" else "Search") }
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(470.dp)) {
            items(videos, key = { it.id }) { video -> YouTubeVideoCard(video) { selected = video } }
        }
    }
    selected?.let { video -> YouTubeVideoInfoDialog(video = video, onDismiss = { selected = null }) }
}

@Composable
fun YouTubeShortsFeed(apiKey: String) {
    var videos by remember { mutableStateOf(emptyList<YouTubeVideo>()) }
    var status by remember { mutableStateOf("Shorts খুঁজতে YouTube API key প্রয়োজন।") }
    var selected by remember { mutableStateOf<YouTubeVideo?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        if (apiKey.isBlank()) { status = "Settings থেকে YouTube API key যোগ করুন।"; return }
        scope.launch {
            loading = true
            status = "ছোট ভিডিও খোঁজা হচ্ছে…"
            try {
                videos = YouTubeDataApi.search(apiKey, "#shorts", shortOnly = true)
                status = videos.size.toString() + "টি short-duration ফলাফল। YouTube API-এর short filter মানেই নিশ্চিত Shorts classification নয়।"
            } catch (e: Exception) { status = e.message ?: "লোড করা যায়নি।" }
            loading = false
        }
    }
    LaunchedEffect(apiKey) { if (apiKey.isNotBlank()) load() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row {
            Text("YouTube Shorts discovery", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { load() }, enabled = !loading) { Text("Refresh") }
        }
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(500.dp)) {
            items(videos, key = { it.id }) { video -> YouTubeVideoCard(video) { selected = video } }
        }
    }
    selected?.let { video -> YouTubeVideoInfoDialog(video = video, onDismiss = { selected = null }) }
}

@Composable
private fun YouTubeVideoCard(video: YouTubeVideo, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column {
            if (video.thumbnail.isNotBlank()) {
                AsyncImage(model = video.thumbnail, contentDescription = video.title, modifier = Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Crop)
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(video.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(video.channel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(listOf(video.duration, video.publishedAt, if (video.views.isNotBlank()) video.views + " views" else "").filter { it.isNotBlank() }.joinToString(" • "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun YouTubeVideoInfoDialog(video: YouTubeVideo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(video.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(video.channel, fontWeight = FontWeight.SemiBold)
                Text(video.description.take(500))
                Text("ভিডিও ID: " + video.id, style = MaterialTheme.typography.labelSmall)
                Text("এই build-এ YouTube metadata দেখা যায়; অনুমোদিত Native playback stream এখনও সংযুক্ত হয়নি।")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("বন্ধ করুন") } }
    )
}
