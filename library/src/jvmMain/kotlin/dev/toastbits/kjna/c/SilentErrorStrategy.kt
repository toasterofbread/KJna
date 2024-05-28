package dev.toastbits.kjna.c

import org.antlr.v4.kotlinruntime.DefaultErrorStrategy
import org.antlr.v4.kotlinruntime.Parser
import org.antlr.v4.kotlinruntime.RecognitionException
import org.antlr.v4.kotlinruntime.NoViableAltException
import org.antlr.v4.kotlinruntime.InputMismatchException
import org.antlr.v4.kotlinruntime.FailedPredicateException

internal class SilentErrorStrategy: DefaultErrorStrategy() {
    override fun reportError(recognizer: Parser, e: RecognitionException) {}
    override fun reportMissingToken(recognizer: Parser) {}
    override fun reportNoViableAlternative(recognizer: Parser, e: NoViableAltException) {}
    override fun reportInputMismatch(recognizer: Parser, e: InputMismatchException) {}
    override fun reportFailedPredicate(recognizer: Parser, e: FailedPredicateException) {}
    override fun reportUnwantedToken(recognizer: Parser) {}
}
