package EShop.lab5

import akka.Done
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern.{schedulerFromActorSystem, Askable}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import java.net.URI
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

trait ProductCatalogJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)
    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _             => throw new RuntimeException("Parsing exception")
      }
  }

  implicit val itemFormat  = jsonFormat5(ProductCatalog.Item)
  implicit val itemsFormat = jsonFormat1(ProductCatalog.Items)
}

object ProductCatalogHttpServerApp extends App {
  ProductCatalogHttpServer.start(9000)
}

case class ProductCatalogApi(queryRef: ActorRef[ProductCatalog.Query])(implicit val scheduler: Scheduler)
  extends ProductCatalogJsonSupport {
  implicit val timeout: Timeout = 3.second

  def routes: Route = {
    path("products") {
      get {
        parameters(Symbol("keywords").as[String].repeated) { (keywords) =>
          parameter(Symbol("brand").as[String].withDefault("gerber")) { (brand) =>
            complete {
              val items =
                queryRef.ask(ref => ProductCatalog.GetItems(brand, keywords.toList, ref)).mapTo[ProductCatalog.Items]

              Future.successful(items)
            }
          }
        }
      }
    }
  }
}

object ProductCatalogHttpServer {

  def apply(port: Int): Behavior[Receptionist.Listing] = {
    Behaviors.setup { context =>
      implicit val ec: ExecutionContextExecutor = context.executionContext
      implicit val system: ActorSystem[Nothing] = context.system
      implicit val timeout: Timeout             = 3.second

      system.receptionist ! Receptionist.subscribe(ProductCatalog.ProductCatalogServiceKey, context.self)
      Behaviors.receiveMessage[Receptionist.Listing] { msg =>
        val listing = msg.serviceInstances(ProductCatalog.ProductCatalogServiceKey)
        if (listing.isEmpty) {
          Behaviors.same
        } else {
          val queryRef = listing.head
          val rest     = ProductCatalogApi(queryRef)
          val binding  = Http().newServerAt("localhost", port).bind(rest.routes)
          val res      = Await.ready(binding, Duration.Inf)

          println(s"Binding is done. $res")
          Behaviors.empty
        }
      }
    }
  }

  def start(port: Int): Future[Done] = {
    val system = ActorSystem[Receptionist.Listing](ProductCatalogHttpServer(port), "ProductCatalog")
    val config = ConfigFactory.load()

    val productCatalogSystem = ActorSystem[Nothing](
      Behaviors.empty,
      "ProductCatalog",
      config.getConfig("productcatalog").withFallback(config)
    )

    productCatalogSystem.systemActorOf(
      ProductCatalog(new SearchService()),
      "productcatalog"
    )

    Await.ready(system.whenTerminated, Duration.Inf)
  }
}
