package com.trib3.server.resources

import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
class PingResource {
    @Path("/ping")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    fun ping(): String {
        return "pong"
    }
}
