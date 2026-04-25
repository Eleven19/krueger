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
            def level: Log.Level = Log.Level.trace

            def trace(msg: => Text)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.trace, msg.toString))

            def trace(msg: => Text, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.trace, msg.toString, Some(t)))

            def debug(msg: => Text)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.debug, msg.toString))

            def debug(msg: => Text, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.debug, msg.toString, Some(t)))

            def info(msg: => Text)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.info, msg.toString))

            def info(msg: => Text, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.info, msg.toString, Some(t)))

            def warn(msg: => Text)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.warn, msg.toString))

            def warn(msg: => Text, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.warn, msg.toString, Some(t)))

            def error(msg: => Text)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.error, msg.toString))

            def error(msg: => Text, t: => Throwable)(using frame: Frame, allow: AllowUnsafe): Unit =
                recorder.append(LogRecord(Log.Level.error, msg.toString, Some(t))))
