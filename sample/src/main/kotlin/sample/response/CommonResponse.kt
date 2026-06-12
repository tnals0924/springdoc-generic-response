package sample.response

import io.github.tnals0924.genericresponse.annotation.GenericWrapper

@GenericWrapper(dataField = "data")
data class CommonResponse<T>(
    val status: Int,
    val code: String,
    val message: String,
    val data: T? = null
)
