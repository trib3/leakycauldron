package com.trib3.testing.server

import org.eclipse.jetty.ee10.servlet.ServletChannel
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.ee10.servlet.ServletContextRequest
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response

/**
 * Bridge from a jetty [Request]/[Response] to a [ServletContextRequest] useful for
 * creating [ServletContextRequest]s that can't be mocked due to assumptions about
 * their wrapped classes, while the jetty classes can more easily be.
 */
class TestServletContextRequest(
    handler: ServletContextHandler,
    servletChannel: ServletChannel,
    request: Request,
    response: Response,
) : ServletContextRequest(
        handler.newServletContextApi(),
        servletChannel,
        request,
        response,
        null,
        handler.servletHandler.getMatchedServlet(request.httpURI.path),
        null,
    )
