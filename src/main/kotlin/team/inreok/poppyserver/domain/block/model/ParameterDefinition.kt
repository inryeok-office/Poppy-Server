package team.inreok.poppyserver.domain.block.model

data class ParameterDefinition(
    val name: String,
    val type: ParameterType,
    val unit: String?,
    val required: Boolean,
    val min: Double? = null,
    val max: Double? = null,
    val options: List<String> = emptyList(),
)
