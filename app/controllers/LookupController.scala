package controllers

import geoip.{DatabaseUpdateWorker, LookupService}
import org.apache.commons.validator.routines.InetAddressValidator

import javax.inject._
import play.api.libs.json.{JsString, Json}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class LookupController @Inject() (
    inetAddressValidator: InetAddressValidator,
    lookupService: LookupService,
    databaseUpdateService: DatabaseUpdateWorker
)(implicit
    ec: ExecutionContext
) extends InjectedController {

  /**
    * Create an Action to render an HTML page.
    *
    * The configuration in the `routes` file means that this method
    * will be called when the application receives a `GET` request with
    * a path of `/`.
    */
  def index() =
    Action { implicit request: Request[AnyContent] =>
      Ok(views.html.index())
    }

  def lookup(ip: String) =
    Action.async {
      if (inetAddressValidator.isValid(ip))
        lookupService.iplookup(ip).map {
          case Some(response) => Ok(Json.toJson(response))
          case None           => Ok(Json.toJson(JsString("{}")))
        }
      else Future.successful(BadRequest)
    }
}
