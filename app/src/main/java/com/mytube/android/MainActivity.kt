package com.mytube.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class AppTab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    AppTab("Home", Icons.Filled.Home),
    AppTab("Search", Icons.Filled.Search),
    AppTab("Shorts", Icons.Filled.SmartDisplay),
    AppTab("Library", Icons.Filled.VideoLibrary),
    AppTab("Premium", Icons.Filled.Star)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyTubeApp() }
    }
}

@Composable
private fun MyTubeApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var darkMode by remember { mutableStateOf(true) }
    val colors = if (darkMode) darkColorScheme(
        primary = Color(0xFFFF3B5C),
        background = Color(0xFF101114),
        surface = Color(0xFF1B1D22)
    ) else lightColorScheme(
        primary = Color(0xFFE32645),
        background = Color(0xFFF7F7F8),
        surface = Color.White
    )

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("mytube", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("Your video space", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.TextButton(onClick = { darkMode = !darkMode }) {
                        Text(if (darkMode) "Dark" else "Light")
                    }
                }
                Spacer(Modifier.height(20.dp))
                when (selectedTab) {
                    0 -> HomeScreen()
                    1 -> EmptyScreen("Search", "Search integration is not configured yet.")
                    2 -> EmptyScreen("Shorts", "Short-form video discovery is coming in a later milestone.")
                    3 -> EmptyScreen("Your Library", "Your saved and authorized media will appear here.")
                    else -> EmptyScreen("mytube Premium", "All mytube-owned interface features are unlocked. External services may have their own terms.")
                }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Welcome to mytube", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("A native video experience, built step by step.")
                    Spacer(Modifier.height(12.dp))
                    Text("FIRST BUILD • NATIVE COMPOSE", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Text("Discover", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        item {
            EmptyStateCard(
                title = "No videos yet",
                message = "Connect an approved content source to load real video information. No sample or fake results are shown."
            )
        }
        item {
            Text("Playback and downloads", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        item {
            EmptyStateCard(
                title = "Authorized media only",
                message = "The built-in player and offline features will support media you own, license, or are otherwise authorized to access."
            )
        }
    }
}

@Composable
private fun EmptyScreen(title: String, message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        EmptyStateCard(title = "Not configured", message = message)
    }
}

@Composable
private fun EmptyStateCard(title: String, message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
