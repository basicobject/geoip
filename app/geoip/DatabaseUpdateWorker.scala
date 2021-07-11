package geoip

import akka.actor.ActorSystem

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.Logging
import play.api.inject.ApplicationLifecycle

import scala.concurrent.duration.DurationInt

@Singleton
class DatabaseUpdateWorker @Inject() (
    dao: GeoIPDao,
    lifecycle: ApplicationLifecycle
)(implicit
    as: ActorSystem,
    ec: ExecutionContext
) extends Logging {

  def update(): Unit = {
    logger.info("******** Running update via scheduler ********")
    MaxMind.checkForUpdate match {
      case Right(true -> etag) =>
        MaxMind.downloadDatabase()
        MaxMind.writeNewEtag(etag)
        logger.info("Unzipping downloaded archive")
        Util.unzip("/tmp/geoip.zip", "/tmp/geoip")
        for {
          _ <- dao.dropStagingTables
          _ <- dao.createStagingTables
          a <- dao.loadDatabase("/tmp/geoip/GeoLite2-City-CSV_20210706")
        } yield (a)
      case Right(false -> etag) => logger.info("Updates are not available")
      case Left(message)        => logger.info(message)
    }
  }

  val worker =
    as.scheduler.scheduleWithFixedDelay(1.minute, 2.minute) { () =>
      logger.info("Starting database update worker")
      update()
    }

  lifecycle.addStopHook(() => Future(worker.cancel()))
}
