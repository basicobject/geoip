package geoip

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class LookupService @Inject() (
    dao: GeoIPDao
) {

  def iplookup(ipAddress: String): Future[Option[LookupResponse]] =
    dao.lookupIPAddress(ipAddress)
}
