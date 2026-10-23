package com.weatherwise.android.ui.search

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DownhillSkiing
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Surfing
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weatherwise.android.R
import com.weatherwise.android.ui.components.BackButton
import com.weatherwise.android.ui.components.RetryButton
import com.weatherwise.android.ui.components.errorText
import com.weatherwise.shared.model.City
import com.weatherwise.shared.model.RecentCity
import org.koin.androidx.compose.koinViewModel

@Composable
fun SearchScreen(
    onCitySelected: (City) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleStartEffect(Unit) {
        viewModel.onScreenShown()
        onStopOrDispose { }
    }

    SearchContent(
        state = state,
        onQueryChanged = { text -> viewModel.onQueryChanged(text) },
        onSearchOpened = { viewModel.onSearchOpened() },
        onSearchClosed = { viewModel.onSearchClosed() },
        onRetry = { viewModel.onRetry() },
        onCitySelected = onCitySelected,
    )
}

@Composable
internal fun SearchContent(
    state: SearchScreenState,
    onQueryChanged: (String) -> Unit,
    onSearchOpened: () -> Unit,
    onSearchClosed: () -> Unit,
    onRetry: () -> Unit,
    onCitySelected: (City) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val close = {
        focusManager.clearFocus()
        onSearchClosed()
    }
    BackHandler(enabled = state.isExpanded, onBack = close)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.safeDrawingPadding()) {
            // The search box sits about a quarter down the screen, then slides up into the top bar.
            val topOffset by animateDpAsState(if (state.isExpanded) 8.dp else maxHeight * 0.22f, label = "searchOffset")

            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(topOffset))
                AnimatedVisibility(visible = !state.isExpanded) { Hero() }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(visible = state.isExpanded) {
                        BackButton(label = stringResource(R.string.search_close), onClick = close)
                    }
                    SearchField(
                        query = state.query,
                        onQueryChange = onQueryChanged,
                        onFocused = {
                            if (!state.isExpanded) onSearchOpened()
                        },
                        focusRequester = focusRequester,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (state.isExpanded) {
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    ExpandedResults(state = state, onRetry = onRetry, onCitySelected = onCitySelected)
                } else {
                    RecentChips(state.recents, onCitySelected)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.attribution),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Hero() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf(Icons.Rounded.WbSunny, Icons.Rounded.DownhillSkiing, Icons.Rounded.Surfing).forEach { icon ->
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.hero_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.hero_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocused: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear))
                }
            }
        } else {
            null
        },
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.primary,
            focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
            unfocusedTrailingIconColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier
            .heightIn(min = 56.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() },
    )
}

@Composable
private fun RecentChips(recents: List<RecentCity>, onCitySelected: (City) -> Unit) {
    if (recents.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 16.dp),
    ) {
        Text(stringResource(R.string.recent_title), style = MaterialTheme.typography.labelLarge)
        recents.forEach { recent ->
            AssistChip(
                onClick = { onCitySelected(recent.city) },
                label = { Text(recent.city.name) },
                leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.primary,
                    leadingIconContentColor = MaterialTheme.colorScheme.primary,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun ExpandedResults(
    state: SearchScreenState,
    onRetry: () -> Unit,
    onCitySelected: (City) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        when (val results = state.results) {
            SearchResults.Idle -> RecentList(state.recents, onCitySelected)
            SearchResults.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
            is SearchResults.Found -> LazyColumn {
                items(results.cities, key = { it.id }) { city ->
                    CityRow(icon = Icons.Rounded.LocationOn, title = city.name, subtitle = city.subtitle, onClick = { onCitySelected(city) })
                    HorizontalDivider()
                }
            }
            is SearchResults.NoMatch -> StatusMessage(stringResource(R.string.no_match, results.query))
            is SearchResults.Failed -> {
                StatusMessage(errorText(results.error))
                RetryButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun RecentList(recents: List<RecentCity>, onCitySelected: (City) -> Unit) {
    if (recents.isEmpty()) return
    Text(
        text = stringResource(R.string.recent_title).uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp).semantics { heading() },
    )
    recents.forEach { recent ->
        val updated = DateUtils.getRelativeTimeSpanString(
            recent.fetchedAtMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
        CityRow(
            icon = Icons.Rounded.History,
            title = recent.city.name,
            subtitle = stringResource(R.string.recent_updated, updated),
            onClick = { onCitySelected(recent.city) },
        )
        HorizontalDivider()
    }
}

@Composable
private fun CityRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun StatusMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(vertical = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}
