package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.duration._
import EShop.lab3.OrderManager

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)                                             extends Command
  case class RemoveItem(item: Any)                                          extends Command
  case object ExpireCart                                                    extends Command
  case class StartCheckout(orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ConfirmCheckoutCancelled                                      extends Command
  case object ConfirmCheckoutClosed                                         extends Command
  case class GetItems(sender: ActorRef[Cart])                               extends Command
  case class WrappedCheckoutEvent(event: TypedCheckout.Event)               extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
  case class ItemAdded(item: Any)                                          extends Event
  case class ItemRemoved(item: Any)                                        extends Event
  case object CartEmptied                                                  extends Event
  case object CartExpired                                                  extends Event
  case object CheckoutClosed                                               extends Event
  case object CheckoutCancelled                                            extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable]) {
    def cart: Cart
  }
  case object Empty extends State(None) {
    def cart: Cart = Cart.empty
  }
  case class NonEmpty(cart: Cart, timer: Cancellable) extends State(Some(timer))
  case class InCheckout(cart: Cart)                   extends State(None)
}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case AddItem(item) => nonEmpty(Cart.empty.addItem(item), scheduleTimer(context))
      case GetItems(sender) =>
        sender ! Cart.empty
        Behaviors.same
      case _ => Behaviors.same
    }
  )

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] =
    Behaviors.receive((context, msg) =>
      msg match {
        case RemoveItem(item) if cart.contains(item) && cart.size == 1 => timer.cancel(); empty
        case RemoveItem(item) if cart.contains(item) =>
          timer.cancel(); nonEmpty(cart.removeItem(item), scheduleTimer(context))
        case AddItem(item) => timer.cancel(); nonEmpty(cart.addItem(item), scheduleTimer(context))
        case StartCheckout(orderManagerRef) =>
          timer.cancel();
          val messageAdapter: ActorRef[TypedCheckout.Event] =
            context.messageAdapter(event => event match { case TypedCheckout.CheckOutClosed => ConfirmCheckoutClosed })
          val checkoutActor = context.spawn(new TypedCheckout(messageAdapter).start, "checkoutActor")
          checkoutActor ! TypedCheckout.StartCheckout
          orderManagerRef ! OrderManager.ConfirmCheckoutStarted(checkoutActor)
          inCheckout(cart)
        case GetItems(sender) =>
          sender ! cart
          Behaviors.same
        case ExpireCart => empty
        case _          => Behaviors.same
      }
    )

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case ConfirmCheckoutCancelled => nonEmpty(cart, scheduleTimer(context))
      case ConfirmCheckoutClosed => empty
      case _ => Behaviors.same
    }
  )
}
