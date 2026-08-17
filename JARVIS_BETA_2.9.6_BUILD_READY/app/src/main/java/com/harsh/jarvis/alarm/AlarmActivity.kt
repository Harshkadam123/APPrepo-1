package com.harsh.jarvis.alarm

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra("TASK_TITLE")
            ?: "Jarvis task"

        val description = intent.getStringExtra("TASK_DESCRIPTION")
            ?: ""

        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "🔔 JARVIS TASK ALARM",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Text(
                        title,
                        modifier = Modifier.padding(top = 24.dp),
                        style = MaterialTheme.typography.headlineMedium
                    )

                    if (description.isNotBlank()) {
                        Text(
                            description,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }

                    Button(
                        onClick = {
                            finish()
                        },
                        modifier = Modifier.padding(top = 32.dp)
                    ) {
                        Text("DONE")
                    }
                }
            }
        }
    }
}
