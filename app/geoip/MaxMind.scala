package geoip

import play.api.Logging

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Paths}
import scala.jdk.OptionConverters.RichOptional

object MaxMind extends Logging {
  // val UserId = 579112
  final val MaxmindLicenceKey = "1gUQkfyOKQLgERTb"
  final val DatabaseUrl =
    s"https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City-CSV&license_key=$MaxmindLicenceKey&suffix=zip"

  val EtagFile = "/tmp/geoip-etag.txt"
  val DatabaseZipFile = "/tmp/geoip.zip"
  val OutputPath = "/tmp/geoip"

  val client = HttpClient.newHttpClient()

  val headRequest = HttpRequest
    .newBuilder()
    .uri(URI.create(DatabaseUrl))
    .method("HEAD", HttpRequest.BodyPublishers.noBody())
    .build()

  val downloadRequest =
    HttpRequest.newBuilder(URI.create(DatabaseUrl)).GET().build()

  def checkForUpdate: Either[String, (Boolean, String)] = {
    logger.info("Checking for database update from MaxMind")
    val response =
      client.send(headRequest, HttpResponse.BodyHandlers.discarding())

    val etag: Option[String] =
      if (Files.exists(Paths.get(EtagFile)))
        Some(Files.readString(Paths.get(EtagFile)).mkString)
      else None

    if (response.statusCode() == 200) {
      val newEtag = response
        .headers()
        .firstValue("etag")
        .toScala
        .map(_.replaceAll("\"", ""))

      Right {
        if (newEtag == etag) false -> newEtag.get
        else true -> newEtag.get
      }
    } else Left("Request failed, status = " + response.statusCode())
  }

  def downloadDatabase() = {
    logger.info("Downloading database file archive from MaxMind")
    client.send(
      downloadRequest,
      HttpResponse.BodyHandlers.ofFile(Paths.get(DatabaseZipFile))
    )
  }

  def writeNewEtag(etag: String) = {
    logger.info("Updating new etag")
    Files.writeString(Paths.get(EtagFile), etag)
  }
}
