package net.johnbiz.countyline

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import net.johnbiz.countyline.county.CountyRepository
import net.johnbiz.countyline.county.detailSource
import net.johnbiz.countyline.ui.CountyDetailScreen
import net.johnbiz.countyline.ui.CountyDetailViewModel
import net.johnbiz.countyline.ui.CountyLineTheme

/** Shows the outline, Census facts, and links for one county. Launched with [EXTRA_FIPS]. */
class CountyDetailActivity : ComponentActivity() {

    private val viewModel: CountyDetailViewModel by viewModels {
        viewModelFactory {
            initializer {
                CountyDetailViewModel(
                    fips = intent.getStringExtra(EXTRA_FIPS).orEmpty(),
                    source = CountyRepository.get(applicationContext).detailSource(),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CountyLineTheme {
                val state by viewModel.uiState.collectAsState()
                CountyDetailScreen(state = state, onBack = ::finish, onOpenLink = ::openLink)
            }
        }
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_FIPS = "net.johnbiz.countyline.extra.FIPS"

        fun intent(context: Context, fips: String): Intent =
            Intent(context, CountyDetailActivity::class.java).putExtra(EXTRA_FIPS, fips)
    }
}
