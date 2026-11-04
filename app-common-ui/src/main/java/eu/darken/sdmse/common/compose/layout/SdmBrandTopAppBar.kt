package eu.darken.sdmse.common.compose.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
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
        title = { ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)) { title() } },
        navigationIcon = navigationIcon,
        colors = colors,
        scrollBehavior = scrollBehavior,
        actions = {
            actions()
            Row(Modifier.padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                AndroidView(
                    factory = { context -> android.widget.ImageView(context).apply {
                        runCatching { setImageDrawable(context.packageManager.getApplicationIcon(context.packageName)) }
                            .onFailure { setImageResource(R.drawable.sdcleaner_brand) }
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    } },
                    modifier = Modifier.size(28.dp),
                )
                Text("SDCleaner", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
    )
}
