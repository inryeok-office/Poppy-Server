package team.inreok.poppyserver.support

fun validBlockProgram(suffix: String = "fixture"): String =
    """{"schemaVersion":1,"blocks":[{"id":"start-$suffix","type":"START","parameters":{}},{"id":"stop-$suffix","type":"STOP","parameters":{}},{"id":"end-$suffix","type":"END","parameters":{}}]}"""
