package net.johnbiz.countyline

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.johnbiz.countyline.core.County
import net.johnbiz.countyline.county.CountyDetail
import net.johnbiz.countyline.county.CountyDetailSource
import net.johnbiz.countyline.ui.CountyDetailUiState
import net.johnbiz.countyline.ui.CountyDetailViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CountyDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val johnson = CountyDetail(County("20091", "Johnson", "20", "Kansas", "KS"), facts = null, outline = null)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows loading until the dataset is ready, then the county`() = runTest(dispatcher) {
        val gate = CompletableDeferred<CountyDetail?>()
        val vm = CountyDetailViewModel("20091", CountyDetailSource { gate.await() })
        advanceUntilIdle()
        assertEquals(CountyDetailUiState.Loading, vm.uiState.value)

        gate.complete(johnson)
        advanceUntilIdle()
        assertEquals(CountyDetailUiState.Loaded(johnson), vm.uiState.value)
    }

    @Test
    fun `unknown FIPS is not found`() = runTest(dispatcher) {
        val vm = CountyDetailViewModel("", CountyDetailSource { null })
        advanceUntilIdle()
        assertEquals(CountyDetailUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun `a dataset that fails to load shows not found instead of crashing`() = runTest(dispatcher) {
        val vm = CountyDetailViewModel("20091", CountyDetailSource { throw java.io.IOException("counties.geojson") })
        advanceUntilIdle()
        assertEquals(CountyDetailUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun `passes the FIPS through to the source`() = runTest(dispatcher) {
        var requested: String? = null
        CountyDetailViewModel("06037", CountyDetailSource { requested = it; null })
        advanceUntilIdle()
        assertEquals("06037", requested)
    }
}
