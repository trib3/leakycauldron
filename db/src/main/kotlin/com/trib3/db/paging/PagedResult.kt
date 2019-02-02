package com.trib3.db.paging

/**
 * A Result object that contains data elements as well as an optional [PageToken] that can
 * be used to request the next page of data elements
 */
data class PagedResult<T>(
    val data: List<T>,
    val pageToken: PageToken?
) {
    companion object {
        /**
         * Generates a [PagedResult] from a list of [data] objects and a callback to
         * create a [PageToken] from the last element in the result
         *
         * @param data list of objects to include in the result
         * @param genToken callback to generate a [PageToken] that will be passed the last element in the list
         */
        fun <T> getPagedResult(data: List<T>, genToken: (T) -> PageToken?): PagedResult<T> {
            val last = when (data.isEmpty()) {
                true -> null
                false -> data.last()
            }
            val token = when (last) {
                null -> null
                else -> genToken(last)
            }
            return PagedResult(data, token)
        }
    }
}
