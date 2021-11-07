package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.testkit.typed.scaladsl.TestInbox

class TypedCartTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox = TestInbox[Cart]()

    testKit.run(TypedCartActor.AddItem("first"))
    testKit.run(TypedCartActor.AddItem("second"))
    testKit.run(TypedCartActor.GetItems(inbox.ref))

    inbox.expectMessage(Cart(Seq("first", "second")))
  }

  it should "be empty after adding and removing the same item" in {
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox = TestInbox[Cart]()
    val item = "some item"

    testKit.run(TypedCartActor.AddItem(item))
    testKit.run(TypedCartActor.RemoveItem(item))
    testKit.run(TypedCartActor.GetItems(inbox.ref))

    inbox.expectMessage(Cart.empty)
  }

  it should "start checkout" in {
    val cart = testKit.spawn(new TypedCartActor().start, "cartActor")
    val probe = testKit.createTestProbe[OrderManager.Command]()

    cart ! AddItem("first")
    cart ! StartCheckout(probe.ref)

    val msg = probe.receiveMessage()
    assert(msg.isInstanceOf[OrderManager.ConfirmCheckoutStarted])
  }
}
