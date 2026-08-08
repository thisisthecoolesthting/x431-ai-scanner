package com.caseforge.scanner.planb.body



import com.caseforge.scanner.planb.gateway.GatewaySession

import org.junit.Assert.assertEquals

import org.junit.Assert.assertTrue

import org.junit.Assert.fail

import org.junit.Test



class BodyReadSessionTest {



    @Test

    fun stubSession_returnsEmptySuccess() {

        val session = BodyReadSession()

        val dtcs = session.readDtcs("ecu:body:0")

        val live = session.readLiveData("ecu:body:0", listOf("0xF190"))

        assertTrue(dtcs.isSuccess)

        assertEquals(emptyList<BodyDtc>(), dtcs.getOrNull())

        assertTrue(live.isSuccess)

        assertEquals(emptyList<BodyLiveDatum>(), live.getOrNull())

    }



    @Test

    fun planbBodyRead_routesDtcsThroughGatewayStub() {

        val gw = GatewaySession()

        val session = BodyReadSession(planbBodyRead = true, gateway = gw)

        val dtcs = session.readDtcs("pcm")

        assertTrue(dtcs.isSuccess)

        assertEquals(emptyList(), dtcs.getOrNull())

    }



    @Test

    fun gatewaySession_readDtcsWithoutConnect_fails() {

        val gw = GatewaySession()

        gw.readDtcs().fold(

            onSuccess = { fail("Expected failure without connect") },

            onFailure = { assertTrue(it is IllegalStateException) },

        )

    }

}

