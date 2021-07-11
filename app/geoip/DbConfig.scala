package geoip

import com.typesafe.config.Config
import play.api.ConfigLoader

case class DbConfig(
    driver: String,
    database: String,
    user: String,
    password: String
)

object DbConfig {
  implicit val configLoader = new ConfigLoader[DbConfig] {
    override def load(config: Config, path: String): DbConfig = {
      DbConfig(
        driver = config.getString("db.driver"),
        database = config.getString("db.database"),
        user = config.getString("db.user"),
        password = config.getString("db.password")
      )
    }
  }
}
