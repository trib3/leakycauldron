package com.trib3.server.resources

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
class PingResource {
    @Path("/ping")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    fun ping(): String {
        log.trace("ping/pong")
        return "pong"
    }
}
