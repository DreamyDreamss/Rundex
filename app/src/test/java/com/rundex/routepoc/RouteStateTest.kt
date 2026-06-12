package com.rundex.routepoc

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteStateTest {

    private val a = LatLngPoint(37.55, 126.98)
    private val b = LatLngPoint(37.56, 126.99)
    private val c = LatLngPoint(37.57, 127.00)
    private val legAB = Leg(listOf(a, b), 1500.0)
    private val legBC = Leg(listOf(b, c), 700.0)

    @Test
    fun `경유점과 구간을 추가하면 총거리가 합산된다`() {
        val s = RouteState()
        s.addWaypoint(a)
        s.addWaypoint(b)
        s.addLeg(legAB)
        s.addWaypoint(c)
        s.addLeg(legBC)
        assertEquals(2200.0, s.totalDistanceMeters(), 1e-9)
    }

    @Test
    fun `fullPath는 모든 구간의 좌표를 이어 붙인다`() {
        val s = RouteState()
        s.addWaypoint(a)
        s.addWaypoint(b)
        s.addLeg(legAB)
        s.addWaypoint(c)
        s.addLeg(legBC)
        assertEquals(listOf(a, b, b, c), s.fullPath())
    }

    @Test
    fun `undo는 마지막 경유점과 구간을 함께 제거한다`() {
        val s = RouteState()
        s.addWaypoint(a)
        s.addWaypoint(b)
        s.addLeg(legAB)
        s.undo()
        assertEquals(listOf(a), s.waypoints)
        assertEquals(0.0, s.totalDistanceMeters(), 1e-9)
    }

    @Test
    fun `라우팅 실패 롤백 - 구간 없이 추가된 경유점만 제거한다`() {
        val s = RouteState()
        s.addWaypoint(a)
        s.addWaypoint(b) // 라우팅 요청 전에 추가됨
        s.undo()         // 라우팅 실패 → 롤백
        assertEquals(listOf(a), s.waypoints)
        assertEquals(emptyList<Leg>(), s.legs)
    }

    @Test
    fun `빈 상태에서 undo해도 안전하다`() {
        val s = RouteState()
        s.undo()
        assertEquals(emptyList<LatLngPoint>(), s.waypoints)
    }
}
