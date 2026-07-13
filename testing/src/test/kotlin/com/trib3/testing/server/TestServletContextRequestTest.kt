package com.trib3.testing.server

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.trib3.testing.LeakyMock
import org.easymock.EasyMock
import org.eclipse.jetty.ee10.servlet.ServletChannel
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.http.HttpURI
import org.eclipse.jetty.server.ConnectionMetaData
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.testng.annotations.Test

class TestServletContextRequestTest {
    @Test
    fun testMockRequest() {
        val req = LeakyMock.niceMock<Request>()
        val res = LeakyMock.niceMock<Response>()
        val connectionMetaData = LeakyMock.niceMock<ConnectionMetaData>()
        EasyMock.expect(connectionMetaData.httpConfiguration).andReturn(HttpConfiguration()).anyTimes()
        EasyMock.expect(req.httpURI).andReturn(HttpURI.from("/test")).anyTimes()
        EasyMock.expect(req.connectionMetaData).andReturn(connectionMetaData).anyTimes()
        EasyMock.replay(req, res, connectionMetaData)
        val handler = ServletContextHandler()
        val channel = ServletChannel(handler, req)
        val contextRequest =
            TestServletContextRequest(
                handler,
                channel,
                req,
                res,
            )
        assertThat(contextRequest.httpURI).isEqualTo(HttpURI.from("/test"))
    }
}
