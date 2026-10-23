package com.weatherwise.android.ui.forecast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weatherwise.android.R
import com.weatherwise.android.ui.components.BackButton
import com.weatherwise.android.ui.components.RetryButton
import com.weatherwise.android.ui.components.activityIcon
import com.weatherwise.android.ui.components.activityName
import com.weatherwise.android.ui.components.errorText
import com.weatherwise.android.ui.components.lastUpdatedText
import com.weatherwise.android.ui.components.longDay
import com.weatherwise.android.ui.components.reasonText
import com.weatherwise.android.ui.components.shortWeekday
import com.weatherwise.shared.model.ActivityRanking
import com.weatherwise.shared.model.City
import com.weatherwise.shared.model.DayScore
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

@Composable
fun ForecastScreen(
    city: City,
    onBack: () -> Unit,
    viewModel: ForecastViewModel = koinViewModel { parametersOf(city) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ForecastContent(city = city, state = state, onBack = onBack, onRetry = { viewModel.onRetry() })
}

@Composable
internal fun ForecastContent(
    city: City,
    state: ForecastScreenState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(horizontal = 16.dp)) {
            Header(city, onBack)
            when (state) {
                ForecastScreenState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is ForecastScreenState.Failed -> Column(Modifier.padding(top = 24.dp)) {
                    Text(
                        text = errorText(state.error),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    RetryButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp))
                }
                is ForecastScreenState.Ready -> Rankings(state)
            }
        }
    }
}

@Composable
private fun Header(city: City, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        BackButton(label = stringResource(R.string.back), onClick = onBack)
        Column {
            Text(
                text = city.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            if (city.subtitle.isNotEmpty()) {
                Text(city.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Rankings(ready: ForecastScreenState.Ready) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ready.isFromCache) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                ) {
                    Icon(
                        Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.saved_data, lastUpdatedText(ready.fetchedAtMillis)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        itemsIndexed(ready.rankings, key = { _, ranking -> ranking.activity }) { index, ranking ->
            ActivityCard(rank = index + 1, ranking = ranking)
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ActivityCard(rank: Int, ranking: ActivityRanking) {
    var expanded by rememberSaveable(ranking.activity) { mutableStateOf(false) }
    val name = stringResource(activityName(ranking.activity))
    val score = ranking.weeklyScore
    val noDataDay = ranking.days.filterIsInstance<DayScore.NoData>().firstOrNull()
    val noDataText = if (noDataDay != null) stringResource(reasonText(noDataDay.reason)) else stringResource(R.string.no_data)

    // TalkBack reads one sentence per card instead of every number and chip.
    var description = stringResource(R.string.rank_description_no_data, name, noDataText)
    if (score != null) {
        description = stringResource(R.string.rank_description, rank, name, score)
        val bestDay = ranking.bestDay
        if (bestDay != null) {
            description += ", " + stringResource(R.string.best_day, longDay(bestDay))
        }
    }
    val toggleLabel = stringResource(if (expanded) R.string.hide_days else R.string.show_days)
    val dimmed = score == null

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (dimmed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = toggleLabel, role = Role.Button) { expanded = !expanded },
    ) {
        Column(Modifier.padding(16.dp)) {
            Column(Modifier.clearAndSetSemantics { contentDescription = description }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (dimmed) "–" else rank.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(32.dp),
                    )
                    Icon(
                        activityIcon(ranking.activity),
                        contentDescription = null,
                        tint = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        text = if (score != null) stringResource(R.string.score_out_of, score) else stringResource(R.string.no_data),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                if (score != null) {
                    LinearProgressIndicator(
                        progress = { score / 100f },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    DayChips(ranking)
                } else {
                    Text(
                        text = noDataText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            AnimatedVisibility(visible = expanded) { DayDetails(ranking.days) }
        }
    }
}

@Composable
private fun DayChips(ranking: ActivityRanking) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        ranking.days.forEach { day ->
            val isBest = day.date == ranking.bestDay
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isBest) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (isBest) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isBest) Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(10.dp))
                        Text(shortWeekday(day.date), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(day.shortScore(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DayDetails(days: List<DayScore>) {
    Column(Modifier.padding(top = 12.dp)) {
        days.forEach { day ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(longDay(day.date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    text = when (day) {
                        is DayScore.Scored -> stringResource(R.string.score_out_of, day.points.roundToInt())
                        is DayScore.NotSuitable -> stringResource(reasonText(day.reason))
                        is DayScore.NoData -> stringResource(reasonText(day.reason))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun DayScore.shortScore(): String = when (this) {
    is DayScore.Scored -> points.roundToInt().toString()
    is DayScore.NotSuitable -> "0"
    is DayScore.NoData -> "–"
}
