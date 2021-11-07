package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.OrderManager
import EShop.lab3.Payment

object TypedCheckout {
  sealed trait Command
  case object StartCheckout                                                                  extends Command
  case class SelectDeliveryMethod(method: String)                                            extends Command
  case object CancelCheckout                                                                 extends Command
  case object ExpireCheckout                                                                 extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ExpirePayment                                                                  extends Command
  case object ConfirmPaymentReceived                                                         extends Command

  sealed trait Event
  case object CheckOutClosed                                    extends Event
  case class PaymentStarted(payment: ActorRef[Payment.Command]) extends Event
  case object CheckoutStarted                                   extends Event
  case object CheckoutCancelled                                 extends Event
  case class DeliveryMethodSelected(method: String)             extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable])
  case object WaitingForStart                           extends State(None)
  case class SelectingDelivery(timer: Cancellable)      extends State(Some(timer))
  case class SelectingPaymentMethod(timer: Cancellable) extends State(Some(timer))
  case object Closed                                    extends State(None)
  case object Cancelled                                 extends State(None)
  case class ProcessingPayment(timer: Cancellable)      extends State(Some(timer))
}

class TypedCheckout(
  cartAdapter: ActorRef[TypedCheckout.Event]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds
  private def startCheckoutTimer(context: ActorContext[TypedCheckout.Command]): Cancellable =
    context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)
  private def startPaymentTimer(context: ActorContext[TypedCheckout.Command]): Cancellable =
    context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case StartCheckout => selectingDelivery(startCheckoutTimer(context))
      case _             => Behaviors.same
    }
  )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case SelectDeliveryMethod(method) => selectingPaymentMethod(timer)
      case ExpireCheckout               => cancelled
      case CancelCheckout               => timer.cancel(); cancelled
      case _                            => Behaviors.same
    }
  )
  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case SelectPayment(payment, orderManagerRef) =>
        timer.cancel();
        val paymentActor = context.spawn(new Payment(payment, orderManagerRef, context.self).start, "paymentActor")
        orderManagerRef ! OrderManager.ConfirmPaymentStarted(paymentActor)
        processingPayment(startPaymentTimer(context))
      case ExpireCheckout => cancelled
      case CancelCheckout => timer.cancel(); cancelled
      case _              => Behaviors.same
    }
  )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case ConfirmPaymentReceived =>
        timer.cancel();
        cartAdapter ! CheckOutClosed
        closed
      case ExpirePayment  => cancelled
      case CancelCheckout => timer.cancel(); cancelled
      case _              => Behaviors.same
    }
  )

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case _ => context.log.info("Checkout Cancelled"); Behaviors.same
    }
  )

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case _ => context.log.info("Checkout Closed"); Behaviors.same
    }
  ) // TODO: stop this actor?
}
