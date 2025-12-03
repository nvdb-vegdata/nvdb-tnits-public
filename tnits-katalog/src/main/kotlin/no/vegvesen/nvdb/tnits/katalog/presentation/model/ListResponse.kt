package no.vegvesen.nvdb.tnits.katalog.presentation.model

enum class FileItemType {
    FILE,
    DIRECTORY,
}

data class FileItem(
    val path: String,
    val type: FileItemType,
    val href: String,
    val size: Long? = null,
)

data class ListResponse(
    val path: String,
    val recursive: Boolean,
    val items: List<FileItem>,
)
