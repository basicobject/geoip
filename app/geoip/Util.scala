package geoip

import play.api.Logging

import java.io.{File, FileInputStream, FileOutputStream}
import java.util.zip.ZipInputStream
import scala.util.{Failure, Success, Try}

object Util extends Logging {
  def unzip(zipFile: String, outfile: String): Unit = {
    Try {
      val destFile = new File(outfile)
      val zipStream = new ZipInputStream(new FileInputStream(zipFile))
      if (!destFile.exists()) destFile.mkdir()

      var zipEntry = zipStream.getNextEntry
      val buffer = new Array[Byte](1024)

      while (zipEntry != null) {
        val filename = zipEntry.getName
        val newFile = new File(outfile + File.separator + filename)
        logger.info("Created file " + newFile.getAbsoluteFile)
        new File(newFile.getParent).mkdirs()

        val fos = new FileOutputStream(newFile)
        var len = zipStream.read(buffer)
        while (len > 0) {
          fos.write(buffer, 0, len)
          len = zipStream.read(buffer)
        }

        fos.close()
        zipEntry = zipStream.getNextEntry
      }
    } match {
      case Success(_) => logger.info("unzip successful")
      case Failure(e) => logger.info(e.getLocalizedMessage)
    }
  }
}
