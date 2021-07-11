package geoip

import play.api.libs.json._

case class LookupResponse(
    latitude: Option[Double],
    longitude: Option[Double],
    accuracy_radius: Option[Int],
    continent: Option[String],
    country: Option[String],
    subdivision: Option[String],
    city: Option[String]
)

object LookupResponse {
  implicit val writes: Writes[LookupResponse] = Json.writes[LookupResponse]
}
