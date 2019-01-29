package com.trib3.db

import com.fasterxml.jackson.annotation.JsonValue

class PageToken {
    @JsonValue
    val token: String

    constructor(vararg components: Any) {
        token = components.joinToString(",")
    }

    constructor(value: String) {
        token = value
    }
}

data class PagedResult<T>(
    val data: List<T>,
    val pageToken: PageToken
)
