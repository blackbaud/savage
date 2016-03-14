package com.getbootstrap.savage

import java.nio.charset.Charset
import java.nio.file.{FileSystems, FileSystem, Path}
import java.io.{IOException, InputStream}
import java.util.Scanner
import akka.event.LoggingAdapter
import scala.util.Try
import com.typesafe.config.Config

package object util {
  val utf8Name = "UTF-8"
  private val utf8 = Charset.forName(utf8Name)

  implicit class Utf8String(val str: String) extends AnyVal {
    def utf8Bytes: Array[Byte] = str.getBytes(utf8)
  }

  implicit class Utf8ByteArray(val bytes: Array[Byte]) extends AnyVal {
    def utf8String: Try[String] = Try { new String(bytes, utf8) }
  }

  implicit class PrefixedString(val str: String) extends AnyVal {
    def unprefix(prefix: String): Option[String] = {
      if (str.startsWith(prefix)) {
        Some(str.stripPrefix(prefix))
      }
      else {
        None
      }
    }
  }

  private object UnixFileSystemString {
    private lazy val unixFileSystem: FileSystem = {
      // get a Unix-y FileSystem, or fail hard
      val unixFsAbstractClass = Class.forName("sun.nio.fs.UnixFileSystem")
      val systemFs = FileSystems.getDefault
      if (unixFsAbstractClass isInstance systemFs) {
        systemFs
      }
      else {
        throw new SecurityException("The globbing for the editable files whitelist requires a Unix-y java.nio.file.FileSystem, but we could not obtain one.")
      }
    }
  }
  implicit class UnixFileSystemString(val str: String) extends AnyVal {
    def asUnixGlob = UnixFileSystemString.unixFileSystem.getPathMatcher("glob:" + str)
    def asUnixPath = UnixFileSystemString.unixFileSystem.getPath(str)
  }

  implicit class RichInputStream(val stream: InputStream) extends AnyVal {
    def readUntilEofAsSingleUtf8String: String = {
      val scanner = new Scanner(stream, utf8Name).useDelimiter("\\A")
      val string = if (scanner.hasNext) {
        scanner.next()
      }
      else {
        ""
      }
      scanner.close()
      string
    }
  }

  implicit class HexByteArray(val array: Array[Byte]) extends AnyVal {
    import javax.xml.bind.DatatypeConverter
    def asHexBytes: String = DatatypeConverter.printHexBinary(array).toLowerCase
  }

  implicit class RichPath(val path: Path) extends AnyVal {
    @throws[SecurityException]
    def deleteRecursively()(implicit log: LoggingAdapter) {
      try {
        java.nio.file.Files.walkFileTree(path, new DeleterFileVisitor(log))
      }
      catch {
        case nsfe:java.nio.file.NoSuchFileException => {
          // file/directory is already nonexistent
        }
        case exc:IOException => log.error(exc, s"Error while deleting ${path}")
      }
    }
  }

  implicit class RichConfig(val config: Config) extends AnyVal {
    import java.util.concurrent.TimeUnit
    import scala.concurrent.duration.FiniteDuration

    def getFiniteDuration(path: String): FiniteDuration = FiniteDuration(config.getDuration(path, TimeUnit.SECONDS), TimeUnit.SECONDS)
  }
}
