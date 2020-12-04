
fun isIntelliJ() = System.getProperty("idea.paths.selector").orEmpty().startsWith("IntelliJIdea")
