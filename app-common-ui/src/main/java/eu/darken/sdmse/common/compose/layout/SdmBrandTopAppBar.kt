package eu.darken.sdmse.common.compose.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.ui.R

/** One header row on every destination, including settings subpages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SdmBrandTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        modifier = modifier,
        title = { ProvideTextStyle(MaterialTheme.typography.titleMedium) { title() } },
        navigationIcon = navigationIcon,
        colors = colors,
        scrollBehavior = scrollBehavior,
        actions = {
            actions()
            Row(Modifier.padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.sdcleaner_brand), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                Text("SDCleaner", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
    )
}
