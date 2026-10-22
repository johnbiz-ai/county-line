package net.johnbiz.countyline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.johnbiz.countyline.R
import net.johnbiz.countyline.core.County
import net.johnbiz.countyline.core.CountyFacts
import net.johnbiz.countyline.core.CountyFactsFormat
import net.johnbiz.countyline.core.OutlineProjection
import net.johnbiz.countyline.core.fullName
import net.johnbiz.countyline.county.CountyDetail
import net.johnbiz.countyline.county.CountyLinks
import net.johnbiz.countyline.county.CurrentPosition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountyDetailScreen(
    state: CountyDetailUiState,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val title = (state as? CountyDetailUiState.Loaded)?.detail?.let {
        "${it.county.fullName(it.facts?.kind)}, ${it.county.stateAbbr}"
    }.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.detail_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        val modifier = Modifier.fillMaxSize().padding(innerPadding)
        when (state) {
            CountyDetailUiState.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            CountyDetailUiState.NotFound -> Column(
                modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.detail_not_found), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.detail_back)) }
            }
            is CountyDetailUiState.Loaded -> CountyDetailContent(state.detail, state.here, onOpenLink, modifier)
        }
    }
}

@Composable
private fun CountyDetailContent(detail: CountyDetail, here: CurrentPosition?, onOpenLink: (String) -> Unit, modifier: Modifier) {
    val county = detail.county
    val kind = detail.facts?.kind
    Column(
        modifier.padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val outlineLabel = stringResource(R.string.detail_outline_description, "${county.fullName(kind)}, ${county.stateAbbr}")
        detail.outline?.let {
            CountyOutline(it, here, Modifier.fillMaxWidth().aspectRatio(4f / 3f).semantics { contentDescription = outlineLabel })
        }
        detail.facts?.let { FactsRow(it) }
        Button(onClick = { onOpenLink(CountyLinks.thingsToDo(county, kind)) }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_things_to_do))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onOpenLink(CountyLinks.wikipedia(county, kind)) }, Modifier.weight(1f)) {
                Text(stringResource(R.string.detail_wikipedia))
            }
            OutlinedButton(onClick = { onOpenLink(CountyLinks.wikivoyage(county, kind)) }, Modifier.weight(1f)) {
                Text(stringResource(R.string.detail_wikivoyage))
            }
        }
        Text(
            stringResource(R.string.detail_attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FactsRow(facts: CountyFacts) {
    val locale = LocalLocale.current.platformLocale
    val items = listOfNotNull(
        facts.population?.let { stringResource(R.string.detail_population) to CountyFactsFormat.count(it, locale) },
        facts.landSqMi?.let {
            stringResource(R.string.detail_land_area) to
                stringResource(R.string.detail_area_value, CountyFactsFormat.measure(it, locale))
        },
        facts.density?.let {
            stringResource(R.string.detail_density) to
                stringResource(R.string.detail_density_value, CountyFactsFormat.measure(it, locale))
        },
    )
    if (items.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for ((label, value) in items) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Draws a county outline in theme colors; even-odd fill keeps holes empty, and [here] adds a ringed
 * dot at the user's position. The path is rebuilt only on resize or when [here] changes.
 */
@Composable
private fun CountyOutline(rings: List<DoubleArray>, here: CurrentPosition?, modifier: Modifier = Modifier) {
    val stroke = MaterialTheme.colorScheme.primary
    val fill = stroke.copy(alpha = 0.25f)
    val halo = MaterialTheme.colorScheme.surface
    Spacer(
        modifier.drawWithCache {
            val padding = 16.dp.toPx()
            val projected = OutlineProjection.fit(rings, size.width, size.height, padding)
            val path = Path().apply { fillType = PathFillType.EvenOdd }
            for (ring in projected) {
                if (ring.size < 4) continue
                path.moveTo(ring[0], ring[1])
                var i = 2
                while (i + 1 < ring.size) {
                    path.lineTo(ring[i], ring[i + 1])
                    i += 2
                }
                path.close()
            }
            val strokeWidth = 2.dp.toPx()
            val dot = here?.let { OutlineProjection.point(rings, it.lng, it.lat, size.width, size.height, padding) }
                ?.let { Offset(it[0], it[1]) }
            val dotRadius = 6.dp.toPx()
            val haloRadius = dotRadius + 2.5.dp.toPx()
            onDrawBehind {
                drawPath(path, fill)
                drawPath(path, stroke, style = Stroke(width = strokeWidth, join = StrokeJoin.Round))
                if (dot != null) {
                    drawCircle(halo, haloRadius, dot)
                    drawCircle(stroke, dotRadius, dot)
                }
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun CountyDetailScreenPreview() {
    CountyLineTheme {
        CountyDetailScreen(
            state = CountyDetailUiState.Loaded(
                CountyDetail(
                    county = County("20091", "Johnson", "20", "Kansas", "KS"),
                    facts = CountyFacts("County", 609863, 473.375),
                    outline = listOf(doubleArrayOf(-95.06, 38.74, -94.61, 38.74, -94.61, 39.04, -95.06, 39.04, -95.06, 38.74)),
                ),
                here = CurrentPosition(lat = 38.88, lng = -94.82, fips = "20091"),
            ),
            onBack = {},
            onOpenLink = {},
        )
    }
}
