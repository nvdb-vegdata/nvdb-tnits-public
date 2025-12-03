package no.vegvesen.nvdb.tnits.katalog.core.api

import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.katalog.core.model.FileDownload
import no.vegvesen.nvdb.tnits.katalog.core.model.FileObject

interface FileService {
    fun getFileObjects(type: ExportedFeatureType, suffix: String): List<FileObject>
    fun downloadFile(objectName: String): FileDownload
    fun delete(path: String, recursive: Boolean): List<String>
    fun list(path: String, recursive: Boolean = false): List<String>
}
