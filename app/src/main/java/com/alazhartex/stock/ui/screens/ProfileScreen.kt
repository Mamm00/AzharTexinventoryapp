package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.BuildConfig
import com.alazhartex.stock.R
import com.alazhartex.stock.ui.components.LabeledRow
import com.alazhartex.stock.ui.components.LoadingBox
import com.alazhartex.stock.ui.dateTimeString

private data class ProfileState(
    val username: String,
    val role: String,
    val assignedSheds: List<String>,
    val sessionExpiresAt: Long?,
)

@Composable
fun ProfileScreen(app: AlAzharApp) {
    val state by produceState(initialValue = null as ProfileState?, Unit) {
        val account = app.container.readRepository.accountById(ACTOR_ADMIN)
        val sheds = if (account?.role == "ADMIN") {
            app.container.readRepository.shedCards().map { it.shedId }
        } else {
            app.container.readRepository.shedAssignments(ACTOR_ADMIN)
        }
        val expiry = app.container.sessionManager.adminSessionExpiresAtWall()
        value = ProfileState(
            username = account?.username ?: ACTOR_ADMIN,
            role = account?.role ?: "KEEPER",
            assignedSheds = sheds,
            sessionExpiresAt = expiry,
        )
    }

    Scaffold { padding ->
        val s = state
        if (s == null) {
            LoadingBox(stringResource(R.string.loading), Modifier.padding(padding))
            return@Scaffold
        }
        val roleLabelRes = when (s.role) {
            "ADMIN" -> R.string.label_role_admin
            "KEEPER" -> R.string.label_role_keeper
            else -> R.string.label_role_sales_manager
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.label_signed_in_as), style = MaterialTheme.typography.labelLarge)
                    Text(s.username, style = MaterialTheme.typography.headlineSmall)
                    LabeledRow(stringResource(R.string.label_role), stringResource(roleLabelRes))
                    LabeledRow(stringResource(R.string.app_name), BuildConfig.VERSION_NAME)
                }
            }
            Card(colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.label_assigned_sheds), style = MaterialTheme.typography.titleMedium)
                    s.assignedSheds.forEach { shed -> Text("• $shed", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Card(colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.label_session_expiry), style = MaterialTheme.typography.titleMedium)
                    Text(s.sessionExpiresAt?.let { dateTimeString(it) } ?: "—", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
