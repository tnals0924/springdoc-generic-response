package sample.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sample.response.CommonResponse
import sample.response.CursorSliceResponse
import sample.response.PageWrapper
import sample.response.ProjectSummaryResponse
import sample.response.TestResponse

@RestController
@RequestMapping("/api")
class SampleController {

    // 1-level: CommonResponse<TestResponse>
    @GetMapping("/test")
    fun getTest(): CommonResponse<TestResponse> =
        CommonResponse(200, "OK", "success", TestResponse(1L, "test"))

    // 2-level: CommonResponse<CursorSliceResponse<ProjectSummaryResponse>>
    @GetMapping("/projects")
    fun getProjects(): CommonResponse<CursorSliceResponse<ProjectSummaryResponse>> {
        val slice = CursorSliceResponse(
            content = listOf(ProjectSummaryResponse(1L, "Project A", "Description A")),
            size = 1,
            hasNext = false,
            nextCursorId = null,
            nextCursorValue = null
        )
        return CommonResponse(200, "OK", "success", slice)
    }

    // 3-level: CommonResponse<CursorSliceResponse<PageWrapper<ProjectSummaryResponse>>>
    @GetMapping("/paged-projects")
    fun getPagedProjects(): CommonResponse<CursorSliceResponse<PageWrapper<ProjectSummaryResponse>>> {
        val page = PageWrapper(listOf(ProjectSummaryResponse(1L, "Project A", "Desc")), 1L, 0)
        val slice = CursorSliceResponse(listOf(page), 1, false, null, null)
        return CommonResponse(200, "OK", "success", slice)
    }

    // edge case: data 없는 케이스 → CommonResponseObject
    @GetMapping("/empty")
    fun emptyResponse(): CommonResponse<Any> =
        CommonResponse(200, "OK", "success")
}
