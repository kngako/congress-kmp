package press.mantra.compose.repository

interface MantraRepository {
    fun addArtifact(
        name: String,
        url: String,
        versionLabel: String
    )

    companion object {
        val NO_OP_MANTRA_REPOSITORY = object: MantraRepository{
            override fun addArtifact(
                name: String,
                url: String,
                versionLabel: String
            ) {
                TODO("Not yet implemented")
            }
        }
    }
}