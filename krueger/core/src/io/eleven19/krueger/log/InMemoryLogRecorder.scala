package io.eleven19.krueger.log

import kyo.*

import scala.collection.mutable

final case class LogRecord(
    level: Log.Level,
    message: String,
    cause: Option[Throwable] = None
) derives CanEqual

final class InMemoryLogRecorder private (private val buffer: mutable.ArrayBuffer[LogRecord]):
    def snapshot(): Seq[LogRecord] = synchronized(buffer.toList)

    def clear(): Unit = synchronized(buffer.clear())

    private[log] def append(record: LogRecord): Unit = synchronized {
        buffer += record
        ()
    }

object InMemoryLogRecorder:

    def unsafeMake(): InMemoryLogRecorder =
        new InMemoryLogRecorder(mutable.ArrayBuffer.empty)

    def layer(recorder: InMemoryLogRecorder): Log =
        Log(new Log.Unsafe:
            def level: Log.Level                   = Log.Level.trace
            def name: String                       = "krueger.in-memory-log-recorder"
            def withName(name: String): Log.Unsafe = this

            def trace(msg: => String)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.trace, msg))

            def trace(msg: => String, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.trace, msg, Some(t)))

            def debug(msg: => String)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.debug, msg))

            def debug(msg: => String, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.debug, msg, Some(t)))

            def info(msg: => String)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.info, msg))

            def info(msg: => String, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.info, msg, Some(t)))

            def warn(msg: => String)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.warn, msg))

            def warn(msg: => String, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.warn, msg, Some(t)))

            def error(msg: => String)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.error, msg))

            def error(msg: => String, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.error, msg, Some(t))))
