package geoip

import cats._
import cats.data._
import cats.implicits._
import doobie.implicits._
import cats.effect.{ContextShift, IO}
import cats.free.Free
import doobie.free.connection
import doobie.postgres.{PFCM, PHC}
import doobie.{ConnectionIO, Fragment, Transactor}
import doobie.util.transactor.Transactor.Aux
import play.api.Configuration

import java.io.{File, FileInputStream}
import java.nio.file.Paths
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class GeoIPDao @Inject() (configuration: Configuration)(implicit
    ec: ExecutionContext
) {

  import GeoIPDao._

  implicit val cs: ContextShift[IO] = IO.contextShift(ec)

  private val dbConfig = configuration.get[DbConfig]("db")

  private val transactor: Aux[IO, Unit] = Transactor.fromDriverManager[IO](
    dbConfig.driver,
    dbConfig.database,
    dbConfig.user,
    dbConfig.password
  )

  def lookupIPAddress(ipAddress: String): Future[Option[LookupResponse]] =
    transact { lookup(ipAddress) }.unsafeToFuture()

  def loadDatabase(dbDir: String): Future[(Long, Long, Long)] = {
    for {
      ipv4Count <- loadCSV(
        copyCityBlocksIPv4,
        dbDir + File.separator + "GeoLite2-City-Blocks-IPv4.csv"
      )
      ipv6Count <- loadCSV(
        copyCityBlocksIPv6,
        dbDir + File.separator + "GeoLite2-City-Blocks-IPv6.csv"
      )
      locationCount <- loadCSV(
        copyGeoLite2Location,
        dbDir + File.separator + "GeoLite2-City-Locations-en.csv"
      )
    } yield (ipv4Count, ipv6Count, locationCount)
  }

  def loadCSV(copyStmt: String, csvFilePath: String): Future[Long] = {
    transact {
      val in = new FileInputStream(csvFilePath)
      copyIn(copyStmt, in)
    }.unsafeToFuture()
  }

  def createStagingTables: Future[Int] = {
    transact {
      (createGeoIPNetworkStagingTable, createGeoIPLocationStagingTable).mapN(
        _ + _
      )
    }.unsafeToFuture()
  }

  def dropStagingTables: Future[Int] = {
    transact {
      (
        dropStagingTable(GeoIPNetworkStagingTable),
        dropStagingTable(GeoIPLocationStagingTable)
      ).mapN(_ + _)
    }.unsafeToFuture()
  }

  private def transact[A](arg: => ConnectionIO[A]): IO[A] =
    arg.transact(transactor)

  private def lookup(ip: String): ConnectionIO[Option[LookupResponse]] =
    Fragment.const(query(ip)).query[LookupResponse].option

  private def copyIn(copyStatement: String, in: FileInputStream) =
    PHC.pgGetCopyAPI(PFCM.copyIn(copyStatement, in))

  private def dropStagingTable(name: String) =
    Fragment.const(s"DROP TABLE IF EXISTS $name").update.run

  private def query(ip: String) =
    s"""
       |select latitude, longitude, accuracy_radius,
       |       location.continent_name as continent,
       |       location.country_name as country,
       |       location.subdivision_1_name as subdivision,
       |       location.city_name as city
       |from geoip2_network net
       |left join geoip2_location location on (
       |  net.geoname_id = location.geoname_id
       |  and location.locale_code = 'en'
       |)
       |where network >> '$ip';
       |
       |""".stripMargin

  private val copyCityBlocksIPv4: String =
    s"""
      |copy $GeoIPNetworkStagingTable(
      |  network, geoname_id, registered_country_geoname_id, represented_country_geoname_id,
      |  is_anonymous_proxy, is_satellite_provider, postal_code, latitude, longitude, accuracy_radius
      |) from STDIN with (format csv, header)
      |""".stripMargin

  private val copyCityBlocksIPv6: String =
    s"""
      |copy $GeoIPNetworkStagingTable(
      |  network, geoname_id, registered_country_geoname_id, represented_country_geoname_id,
      |  is_anonymous_proxy, is_satellite_provider, postal_code, latitude, longitude, accuracy_radius
      |) from STDIN with (format csv, header)
      |""".stripMargin

  private val copyGeoLite2Location: String =
    s"""
      |copy $GeoIPLocationStagingTable(
      |  geoname_id, locale_code, continent_code, continent_name, country_iso_code, country_name,
      |  subdivision_1_iso_code, subdivision_1_name, subdivision_2_iso_code, subdivision_2_name,
      |  city_name, metro_code, time_zone, is_in_european_union
      |) from STDIN with (format csv, header)
      |""".stripMargin

  private val createGeoIPNetworkStagingTable = Fragment.const(s"""
      |create table $GeoIPNetworkStagingTable (
      |  network cidr not null,
      |  geoname_id int,
      |  registered_country_geoname_id int,
      |  represented_country_geoname_id int,
      |  is_anonymous_proxy bool,
      |  is_satellite_provider bool,
      |  postal_code text,
      |  latitude numeric,
      |  longitude numeric,
      |  accuracy_radius int
      |);
      |""".stripMargin).update.run

  private val createGeoIPLocationStagingTable = Fragment.const(s"""
      |create table $GeoIPLocationStagingTable (
      |  geoname_id int not null,
      |  locale_code text not null,
      |  continent_code text not null,
      |  continent_name text not null,
      |  country_iso_code text,
      |  country_name text,
      |  subdivision_1_iso_code text,
      |  subdivision_1_name text,
      |  subdivision_2_iso_code text,
      |  subdivision_2_name text,
      |  city_name text,
      |  metro_code int,
      |  time_zone text,
      |  is_in_european_union bool not null,
      |  primary key (geoname_id, locale_code)
      |);
      |""".stripMargin).update.run

}

object GeoIPDao {
  val GeoIPNetworkStagingTable = "geoip2_network_staging"
  val GeoIPLocationStagingTable = "geoip2_location_staging"
}
