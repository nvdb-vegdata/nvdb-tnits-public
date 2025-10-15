package no.vegvesen.nvdb.tnits.common.utils

import kotlin.time.Instant

fun parseTimestampFromS3Key(s3Key: String): Instant? {
    return try {
        // Expected format: 0105-speedLimits/2025-01-15T10-30-00Z/snapshot.xml.gz
        // Extract timestamp using regex: YYYY-MM-DDTHH-mm-ssZ
        val timestampRegex = Regex("""(\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}Z)""")
        val matchResult = timestampRegex.find(s3Key)
            ?: return null

        val timestampStr = matchResult.groupValues[1]
        // Convert S3-safe format (2025-01-15T10-30-00Z) to ISO format (2025-01-15T10:30:00Z)
        val isoTimestamp = timestampStr.replace(Regex("""T(\d{2})-(\d{2})-(\d{2})Z"""), "T$1:$2:$3Z")

        val parsed = Instant.parse(isoTimestamp)
        parsed
    } catch (e: Exception) {
        null
    }
}
