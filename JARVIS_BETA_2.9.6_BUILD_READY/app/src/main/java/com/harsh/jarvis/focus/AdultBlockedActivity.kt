package com.harsh.jarvis.focus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Activity shown when JARVIS blocks access to protected/adult content.
 *
 * Design goals:
 * - No dependency on JARVIS-specific classes.
 * - No dependency on ViewModels.
 * - No dependency on navigation.
 * - No dependency on permissions.
 * - No dependency on the focus engine.
 * - No nullable state.
 * - No custom theme dependency.
 * - Uses only AndroidX Activity + Jetpack Compose Material3.
 *
 * This makes the file intentionally isolated so that changes elsewhere
 * in the focus system are less likely to break compilation here.
 */
class AdultBlockedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * Explicitly use the Activity receiver.
         *
         * ComponentActivity + androidx.activity.compose.setContent
         * provides the Compose entry point.
         */
        setContent {
            AdultBlockedContent()
        }
    }
}

/**
 * Standalone blocked-content screen.
 *
 * No external application state is required.
 */
@Composable
private fun AdultBlockedContent() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Content Blocked",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Text(
                    text = "JARVIS Focus Protection is currently active.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = "This content has been blocked according to your current protection settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
