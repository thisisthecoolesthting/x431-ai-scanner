package com.caseforge.scanner.planb.body

/** No I/O — empty successful reads until a real reader is wired. */
class StubBodyModuleReader : BodyModuleReader {
    override fun readDtcs(ecuId: String): Result<List<BodyDtc>> = Result.success(emptyList())

    override fun readLiveData(ecuId: String, dids: List<String>): Result<List<BodyLiveDatum>> =
        Result.success(emptyList())
}
