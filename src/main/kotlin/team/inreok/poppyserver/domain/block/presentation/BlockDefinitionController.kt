package team.inreok.poppyserver.domain.block.presentation

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.poppyserver.domain.block.application.BlockDefinitionCatalog
import team.inreok.poppyserver.domain.block.model.BlockDefinition
import team.inreok.poppyserver.domain.block.model.ParameterDefinition
import team.inreok.poppyserver.global.response.ApiResponse

@RestController
@RequestMapping("/api/v1/blocks")
class BlockDefinitionController(
    private val blockDefinitionCatalog: BlockDefinitionCatalog,
) {
    @GetMapping
    fun list(): ApiResponse<BlockDefinitionListResponse> = ApiResponse.success(
        BlockDefinitionListResponse(
            schemaVersion = 1,
            blocks = blockDefinitionCatalog.list().map(BlockDefinition::toResponse),
        ),
    )
}

data class BlockDefinitionListResponse(
    val schemaVersion: Int,
    val blocks: List<BlockDefinitionResponse>,
)

data class BlockDefinitionResponse(
    val type: String,
    val displayName: String,
    val category: String,
    val parameters: List<ParameterDefinitionResponse>,
    val available: Boolean,
    val freeModeAllowed: Boolean,
)

data class ParameterDefinitionResponse(
    val name: String,
    val type: String,
    val unit: String?,
    val required: Boolean,
    val min: Double?,
    val max: Double?,
    val options: List<String>,
)

private fun BlockDefinition.toResponse(): BlockDefinitionResponse = BlockDefinitionResponse(
    type = type.name,
    displayName = displayName,
    category = category.name,
    parameters = parameters.map(ParameterDefinition::toResponse),
    available = available,
    freeModeAllowed = freeModeAllowed,
)

private fun ParameterDefinition.toResponse(): ParameterDefinitionResponse = ParameterDefinitionResponse(
    name = name,
    type = type.name,
    unit = unit,
    required = required,
    min = min,
    max = max,
    options = options,
)
