package sample.response

import io.github.tnals0924.genericresponse.annotation.GenericWrapper

@GenericWrapper(dataField = "items")
data class PageWrapper<T>(
    val items: List<T>,
    val totalCount: Long,
    val pageNumber: Int
)
