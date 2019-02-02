package com.trib3.db.paging

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue

/**
 * An opaque token that represents a paging state to be returned to clients and used
 * to requeset the next page of data for a query.
 */
data class PageToken
constructor(
    @get:JsonIgnore @JsonValue val token: String
) {
    constructor(vararg components: Any) : this(components.joinToString(","))
    constructor(components: List<Any>) : this(components.joinToString(","))

    fun split(): List<String> {
        return token.split(",")
    }
}
