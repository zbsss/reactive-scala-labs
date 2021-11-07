package EShop.lab3

import EShop.lab2.TypedCheckout
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Payment {

  sealed trait Command
  case object DoPayment extends Command

  sealed trait Event
  case object PaymentConfirmed extends Event

}

class Payment(
  method: String,
  orderManager: ActorRef[OrderManager.Command],
  checkout: ActorRef[TypedCheckout.Command]
) {

  import Payment._

  def start: Behavior[Payment.Command] = Behaviors.receive { (context, msg) =>
    msg match {
      case DoPayment =>
        orderManager ! OrderManager.ConfirmPaymentReceived
        checkout ! TypedCheckout.ConfirmPaymentReceived
        Behaviors.same // TODO: stop this actor?
      case _ => Behaviors.same
    }
  }
}
