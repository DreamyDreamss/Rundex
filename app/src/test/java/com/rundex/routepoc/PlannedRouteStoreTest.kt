package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlannedRouteStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `저장 후 로드하면 동일한 경로가 나온다`() {
        val store = PlannedRouteStore(tmp.root)
        val route = PlannedRoute(
            points = listOf(LatLngPoint(37.51, 126.99), LatLngPoint(37.52, 127.00)),
            distanceMeters = 1500.0,
        )
        store.save(route)
        assertEquals(route, store.load())
    }

    @Test
    fun `파일이 없으면 null`() {
        assertNull(PlannedRouteStore(tmp.root).load())
    }

    @Test
    fun `파손된 파일이면 null`() {
        File(tmp.root, "planned_route.json").writeText("not json")
        assertNull(PlannedRouteStore(tmp.root).load())
    }
}
