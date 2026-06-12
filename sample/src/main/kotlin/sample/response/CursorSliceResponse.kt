package sample.response

import io.github.tnals0924.genericresponse.annotation.GenericWrapper

@GenericWrapper(dataField = "content")
data class CursorSliceResponse<T>(
    val content: List<T>,
    val size: Int,
    val hasNext: Boolean,
    val nextCursorId: Long?,
    val nextCursorValue: String?
)
