package me.rerere.rikkahub.ui.pages.workbench

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Rocket01
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.subagent.SubAgentRun
import me.rerere.rikkahub.subagent.SubAgentStatus
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController

@Composable
fun WorkbenchContent(
    vm: WorkbenchVM,
    onNavigateDetail: (String) -> Unit,
    onCreateMission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val missions by vm.missions.collectAsStateWithLifecycle()
    val activeMissions by vm.activeMissions.collectAsStateWithLifecycle()
    val reviewMissions by vm.reviewMissions.collectAsStateWithLifecycle()
    val recentDone by vm.recentDoneMissions.collectAsStateWithLifecycle()
    val doneToday by vm.doneToday.collectAsStateWithLifecycle()
    val activeSubAgents by vm.activeSubAgents.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ② Hero stats
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp,
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatBlock(
                    value = activeMissions.size.toString(),
                    label = stringResource(R.string.workbench_stat_active),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Box(modifier = Modifier.weight(1f)) {
                    StatBlock(
                        value = reviewMissions.size.toString(),
                        label = stringResource(R.string.workbench_stat_review),
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = reviewMissions.isNotEmpty()) {
                                onNavigateDetail(reviewMissions.first().id)
                            },
                    )
                }
                StatBlock(
                    value = doneToday.toString(),
                    label = stringResource(R.string.workbench_stat_done_today),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ③ Active sub-agents
        if (activeSubAgents.isNotEmpty()) {
            Text(
                text = stringResource(R.string.workbench_active_subagents),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                activeSubAgents.forEach { run ->
                    SubAgentCard(run = run)
                }
            }
        }

        // ④ Mission groups / ⑤ empty state
        if (missions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Rocket01,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = stringResource(R.string.workbench_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.workbench_empty_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                FilledTonalButton(onClick = onCreateMission) {
                    Text(text = stringResource(R.string.workbench_new_mission))
                }
            }
        } else {
            if (activeMissions.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.workbench_section_active),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                activeMissions.forEach { mission ->
                    MissionCard(
                        mission = mission,
                        onClick = { onNavigateDetail(mission.id) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (reviewMissions.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.workbench_section_review),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                reviewMissions.forEach { mission ->
                    MissionCard(
                        mission = mission,
                        onClick = { onNavigateDetail(mission.id) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (recentDone.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.workbench_section_recent_done),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                recentDone.forEach { mission ->
                    MissionCard(
                        mission = mission,
                        onClick = { onNavigateDetail(mission.id) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }

        // ⑥ Quick entries
        CardGroup(
            modifier = Modifier.padding(horizontal = 16.dp),
            title = { Text(text = stringResource(R.string.workbench_quick_title)) },
        ) {
            item(
                onClick = { navController.navigate(Screen.Stats) },
                leadingContent = { Icon(HugeIcons.ChartColumn, contentDescription = null) },
                headlineContent = { Text(text = stringResource(R.string.workbench_quick_stats)) },
            )
        }
    }
}

@Composable
private fun SubAgentCard(run: SubAgentRun) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = run.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    if (run.status == SubAgentStatus.RUNNING) {
                        R.string.workbench_subagent_running
                    } else {
                        R.string.workbench_subagent_pending
                    }
                ) + " · " + relativeTime(run.startedAtMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
