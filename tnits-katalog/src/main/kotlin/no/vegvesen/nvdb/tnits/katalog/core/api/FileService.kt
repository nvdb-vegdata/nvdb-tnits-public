package no.vegvesen.nvdb.tnits.katalog.core.api

import no.vegvesen.nvdb.tnits.common.model.RoadFeatureTypeCode
import no.vegvesen.nvdb.tnits.katalog.core.model.FileDownload
import no.vegvesen.nvdb.tnits.katalog.core.model.FileObject

interface FileService {
    fun getFileObjects(type: RoadFeatureTypeCode, suffix: String): List<FileObject>
    fun downloadFile(objectName: String): FileDownload
}
