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
import net.johnbiz.countyline.county.CurrentPosition
import net.johnbiz.countyline.county.CurrentPositionSource
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
    private val inJohnson = CurrentPosition(lat = 38.88, lng = -94.82, fips = "20091")
    private val inJackson = CurrentPosition(lat = 39.01, lng = -94.35, fips = "29095")
    private val noFix = CurrentPositionSource { null }

    private fun loadedJohnson(position: CurrentPositionSource) =
        CountyDetailViewModel("20091", CountyDetailSource { johnson }, position)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows loading until the dataset is ready, then the county`() = runTest(dispatcher) {
        val gate = CompletableDeferred<CountyDetail?>()
        val vm = CountyDetailViewModel("20091", CountyDetailSource { gate.await() }, noFix)
        advanceUntilIdle()
        assertEquals(CountyDetailUiState.Loading, vm.uiState.value)

        gate.complete(johnson)
        advanceUntilIdle()
        assertEquals(CountyDetailUiState.Loaded(johnson), vm.uiState.value)
    }

    @Test
    fun `unknown FIPS is not found`() = runTest(dispatcher) {
        val vm = CountyDetailViewModel("", CountyDetailSource { null }, noFix)
        advanceUntilIdle()
        assertEquals(CountyDetailUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun `a dataset that fails to load shows not found instead of crashing`() = runTest(dispatcher) {
        val vm = CountyDetailViewModel("20091", CountyDetailSource { throw java.io.IOException("counties.geojson") }, noFix)
        advanceUntilIdle()
        assertEquals(CountyDetailUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun `passes the FIPS through to the source`() = runTest(dispatcher) {
        var requested: String? = null
        CountyDetailViewModel("06037", CountyDetailSource { requested = it; null }, noFix)
        advanceUntilIdle()
        assertEquals("06037", requested)
    }

    @Test
    fun `refreshing inside the county shows where you are`() = runTest(dispatcher) {
        val vm = loadedJohnson { inJohnson }
        vm.refreshPosition()
        advanceUntilIdle()
        assertEquals(CountyDetailUiState.Loaded(johnson, here = inJohnson), vm.uiState.value)
    }

    @Test
    fun `being in another county shows no position`() = runTest(dispatcher) {
        val vm = loadedJohnson { inJackson }
        vm.refreshPosition()
        advanceUntilIdle()
        assertEquals(CountyDetailUiState.Loaded(johnson, here = null), vm.uiState.value)
    }

    @Test
    fun `no fix or a failing fix shows no position`() = runTest(dispatcher) {
        for (source in listOf(noFix, CurrentPositionSource { throw SecurityException("no permission") })) {
            val vm = loadedJohnson(source)
            vm.refreshPosition()
            advanceUntilIdle()
            assertEquals(CountyDetailUiState.Loaded(johnson, here = null), vm.uiState.value)
        }
    }

    @Test
    fun `a fix that arrives before the county loads is shown once it does`() = runTest(dispatcher) {
        val gate = CompletableDeferred<CountyDetail?>()
        val vm = CountyDetailViewModel("20091", CountyDetailSource { gate.await() }) { inJohnson }
        vm.refreshPosition()
        advanceUntilIdle()
        assertEquals(CountyDetailUiState.Loading, vm.uiState.value)

        gate.complete(johnson)
        advanceUntilIdle()
        assertEquals(CountyDetailUiState.Loaded(johnson, here = inJohnson), vm.uiState.value)
    }

    @Test
    fun `refreshing again replaces the position`() = runTest(dispatcher) {
        val fixes = ArrayDeque(listOf(inJohnson, inJackson))
        val vm = loadedJohnson { fixes.removeFirst() }
        vm.refreshPosition()
        advanceUntilIdle()
        assertEquals(CountyDetailUiState.Loaded(johnson, here = inJohnson), vm.uiState.value)

        vm.refreshPosition()
        advanceUntilIdle()
        assertEquals(CountyDetailUiState.Loaded(johnson, here = null), vm.uiState.value)
    }
}
