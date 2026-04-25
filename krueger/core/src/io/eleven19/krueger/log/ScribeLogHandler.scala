package io.eleven19.krueger.log

import kyo.*
import scribe.Logger

final class ScribeLogHandler(logger: Logger):

    val log: Log =
        Log(new Log.Unsafe:
            def level: Log.Level = Log.Level.trace

            def trace(msg: => Text)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.trace(msg.toString)

            def trace(msg: => Text, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.trace(msg.toString, t)

            def debug(msg: => Text)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.debug(msg.toString)

            def debug(msg: => Text, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.debug(msg.toString, t)

            def info(msg: => Text)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.info(msg.toString)

            def info(msg: => Text, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.info(msg.toString, t)

            def warn(msg: => Text)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.warn(msg.toString)

            def warn(msg: => Text, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.warn(msg.toString, t)

            def error(msg: => Text)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.error(msg.toString)

            def error(msg: => Text, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                logger.error(msg.toString, t))
