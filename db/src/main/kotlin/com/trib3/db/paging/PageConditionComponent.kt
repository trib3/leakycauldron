package com.trib3.db.paging

import com.trib3.db.paging.PageConditionComponent.Companion.getPageCondition
import org.jooq.Condition
import org.jooq.Field

/**
 * Enum to specify the direction a query's results should be sorted in
 */
enum class SortDirection {
    DESC,
    ASC,
}

/**
 * Metadata about the state of paging.  Can be generated from a page token element [value],
 * the [field] that the page token element came from, and an [extractor] that can get a
 * properly typed instance from the String [value].  Used to generate the comparison [Condition]s
 * in [getPageCondition]
 */
data class PageConditionComponent<T>(
    val field: Field<T>,
    val value: String,
    val extractor: ((String) -> T),
) {
    fun eq(): Condition {
        return field.eq(extractor.invoke(value))
    }

    fun lt(): Condition {
        return field.lt(extractor.invoke(value))
    }

    fun gt(): Condition {
        return field.gt(extractor.invoke(value))
    }

    companion object {
        /**
         * Generates a jooq [Condition] from the current paging state and desired sort direction.
         */
        fun getPageCondition(
            sortDirection: SortDirection,
            vararg pagingState: PageConditionComponent<out Any>,
        ): Condition {
            return getPageCondition(sortDirection, pagingState.asList())
        }

        /**
         * Generates a jooq [Condition] from the current paging state and desired sort direction.
         * When the sort is [SortDirection.DESC], elements that satisfy the condition will be < the paging state.
         * To satisfy that condition, they will be < the first paging state component OR == the first and < the
         * second, OR == the first and second and < the third, etc etc
         * When the sort is [SortDirection.ASC], elements that satisfy the condition will be > the paging state.
         * To satisfy that condition, they will be > the first paging state component OR == the first and > the
         * second, OR == the first and second and > the third, etc etc
         */
        fun getPageCondition(
            sortDirection: SortDirection,
            pagingState: List<PageConditionComponent<out Any>>,
        ): Condition {
            return pagingState.map {
                when (sortDirection) {
                    SortDirection.DESC -> it.lt()
                    SortDirection.ASC -> it.gt()
                }
            }.reduceIndexed { listIndex, conditionSoFar, nextCondition ->
                val priorEqualityCondition =
                    pagingState.subList(0, listIndex)
                        .map(PageConditionComponent<out Any>::eq)
                        .reduce(Condition::and)
                conditionSoFar.or(priorEqualityCondition.and(nextCondition))
            }
        }
    }
}
