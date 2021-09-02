package fr.acinq.phoenix.utils

/* Implements "Public Suffix List" spec:
 * https://publicsuffix.org/list/
 */
class PublicSuffixList(
    private val list: String
) {
    private class Rule(
        val labels: List<String>,
        val isExceptionRule: Boolean
    ) {
        companion object {
            val whitespace = "\\s+".toRegex()

            fun parse(line: String): Rule? {
                // Definitions:
                // - Each line is only read up to the first whitespace;
                //   entire lines can also be commented using //.
                // - Each line which is not entirely whitespace or begins with a comment contains a rule.
                // - A rule may begin with a "!" (exclamation mark).
                //   If it does, it is labelled as an "exception rule"
                //   and then treated as if the exclamation mark is not present.
                // - A domain or rule can be split into a list of labels using the separator "." (dot).
                //   The separator is not part of any label.
                //   Empty labels are not permitted, meaning that leading and trailing dots are ignored.

                var suffix = line.split(regex = whitespace, limit = 1).firstOrNull() ?: ""
                if (suffix.isEmpty() || suffix.startsWith("//")) {
                    return null
                }

                var isExceptionRule = false
                if (suffix.startsWith('!')) {
                    isExceptionRule = true
                    suffix = suffix.substring(1)
                }

                val labels = suffix.split('.').filter {
                    it.isNotEmpty()
                }.map {
                    it.toLowerCase()
                }
                if (labels.isEmpty()) {
                    return null
                }
                if (isExceptionRule && labels.size == 1) { // implicitly illegal
                    return null
                }

                return Rule(labels, isExceptionRule)
            }
        }

        fun matches(domain: List<String>): Boolean {
            // A domain is said to match a rule if and only if all the following conditions are met:
            // - When the domain and rule are split into corresponding labels,
            //   that the domain contains as many or more labels than the rule.
            // - Beginning with the right-most labels of both the domain and the rule,
            //   and continuing for all labels in the rule, one finds that for every pair,
            //   either they are identical, or that the label from the rule is "*".

            val dSize = domain.size
            val lSize = labels.size // known to be non-empty

            if (dSize < lSize) {
                return false
            }

            var dIdx = dSize
            var lIdx = lSize

            while (lIdx > 0) {
                val lLabel = labels[lIdx - 1]
                if (lLabel != "*") {
                    if (lLabel != domain[dIdx - 1]) {
                        return false
                    }
                }

                dIdx -= 1
                lIdx -= 1
            }

            return true
        }
    }

    private val rules = mutableListOf<Rule>()

    init {
        for (line in list.lines()) {
            Rule.parse(line)?.let {
                rules.add(it)
            }
        }
    }

    public fun eTldPlusOne(domain: String): String? {

        val whitespace = "\\s+".toRegex()
        val components = domain
            .trimStart()
            .split(whitespace, limit = 1).first() // split always returns non-empty list
            .split('.')
            .filter { it.isNotEmpty() }

        if (components.isEmpty()) {
            return null
        }

        val labels = components.map { it.toLowerCase() }

        // Algorithm:
        // 1. Match domain against all rules and take note of the matching ones.
        // 2. If no rules match, the prevailing rule is "*".
        // 3. If more than one rule matches, the prevailing rule is the one which is an exception rule.
        // 4. If there is no matching exception rule, the prevailing rule is the one with the most labels.
        // 5. If the prevailing rule is an exception rule, modify it by removing the leftmost label.
        // 6. The public suffix is the set of labels from the domain which match the labels
        //    of the prevailing rule, using the matching algorithm above.
        // 7. The registered or registrable domain is the public suffix plus one additional label.

        val matchingRules = mutableListOf<Rule>()
        for (rule in rules) {
            if (rule.matches(labels)) {
                matchingRules.add(rule)
            }
        }

        val prevailingRule =
            if (matchingRules.isEmpty()) {
                Rule(labels = listOf("*"), isExceptionRule = false)
            } else if (matchingRules.size == 1) {
                matchingRules.first()
            } else {
                matchingRules.firstOrNull { it.isExceptionRule }?.let {
                    it
                } ?: kotlin.run {
                    val longestSize = matchingRules.maxOf { it.labels.size }
                    matchingRules.first { it.labels.size == longestSize }
                }
            }

        val countPlusOne = if (prevailingRule.isExceptionRule) {
            prevailingRule.labels.size
        } else {
            prevailingRule.labels.size + 1
        }

        return if (components.size < countPlusOne) {
            null
        } else {
            components.subList(
                fromIndex = components.size - countPlusOne, // inclusive
                toIndex = components.size // exclusive
            ).joinToString(separator = ".")
        }
    }
}