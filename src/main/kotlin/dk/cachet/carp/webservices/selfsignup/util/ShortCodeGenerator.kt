package dk.cachet.carp.webservices.selfsignup.util

import java.security.SecureRandom

/**
 * Generates 5-letter self-signup codes, Kahoot-PIN style: short enough to type from a screen, long enough
 * that a random guess is impractical. `I`, `O`, and `Q` are excluded from the charset - `I`/`O` are
 * visually confusable with `1`/`0` and with each other, and `Q` is easily misread as `O` at small sizes
 * (a printed code or a QR-adjacent label). No digits are used (the requirement is letters only), so
 * digit/letter look-alike pairs don't apply here.
 *
 * 23^5 ~= 6.4 million possible codes - collision probability at realistic study counts is negligible;
 * correctness against collisions comes from the DB unique constraint plus a bounded retry loop in the
 * caller, not from this probability argument.
 */
object ShortCodeGenerator {
    private val CHARSET = ('A'..'Z').filterNot { it in "IOQ" }
    private const val LENGTH = 5
    private val random = SecureRandom()

    fun generate(): String = (1..LENGTH).map { CHARSET[random.nextInt(CHARSET.size)] }.joinToString("")
}
