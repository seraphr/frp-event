package jp.seraphr.event

import org.scalatest.Matchers
import org.scalatest.FlatSpec
import scala.collection.mutable.ArrayBuffer

class EventCombinatorTest extends FlatSpec with Matchers {
  "map" should "create new Event that emit mapped values" in {
    val tSource = new GenericEventSource[Int]
    val tBuffer = new ArrayBuffer[Int]
    val tEvent = tSource.map(_ * 2)
    tEvent.subscribe(tBuffer += _)

    tSource.emit(10)
    tSource.emit(20)

    tBuffer.toList should be(List(20, 40))
  }

  "mapped event" should "be completed when underlying event is completed" in {
    val tSource = new GenericEventSource[Int]
    val tEvent = tSource.map(_ * 2)
    var tCompleted = false
    tEvent.subscribe(_ => (), aOnComplete = () => tCompleted = true)

    tCompleted should be(false)
    tSource.complete()
    tCompleted should be(true)

  }

  "flatten" should "create new Event that enable latest Event" in {
    val tOuter = new GenericEventSource[Event[Int]]
    val tInner1 = new GenericEventSource[Int]
    val tInner2 = new GenericEventSource[Int]

    val tBuffer = new ArrayBuffer[Int]
    val tFlattenEvent = tOuter.flatten
    tFlattenEvent.subscribe(tBuffer += _)

    tOuter.emit(tInner1)
    tInner1.emit(11)
    tInner1.emit(12)
    tInner2.emit(21)
    tInner2.emit(22)

    tOuter.emit(tInner2)
    tInner1.emit(13)
    tInner1.emit(14)
    tInner2.emit(23)
    tInner2.emit(24)

    tBuffer.toList should be(List(11, 12, 23, 24))
  }

  "flatten event" should "be completed when OuterEvent and last InnerEvent are completed" in {
    val tOuter = new GenericEventSource[Event[Int]]
    val tInner1 = new GenericEventSource[Int]
    val tInner2 = new GenericEventSource[Int]

    val tFlattenEvent = tOuter.flatten
    var tCompleted = false
    tFlattenEvent.subscribe(_ => (), () => tCompleted = true)

    tOuter.emit(tInner1)
    tInner1.complete()
    tOuter.emit(tInner2)

    tOuter.complete()
    tCompleted should be(false)
    tInner2.complete()
    tCompleted should be(true)
  }

  "flatten event" should "be completed when last InnerEvent and OuterEvent are completed" in {
    val tOuter = new GenericEventSource[Event[Int]]
    val tInner1 = new GenericEventSource[Int]
    val tInner2 = new GenericEventSource[Int]

    val tFlattenEvent = tOuter.flatten
    var tCompleted = false
    tFlattenEvent.subscribe(_ => (), () => tCompleted = true)

    tOuter.emit(tInner1)
    tInner1.complete()
    tOuter.emit(tInner2)

    tInner2.complete()
    tCompleted should be(false)
    tOuter.complete()
    tCompleted should be(true)
  }

  "flatMap" should "create new Event that emit Mapped and Flatten values" in {
    val tOuter = new GenericEventSource[Int]
    val tInner1 = new GenericEventSource[Int]
    val tInner2 = new GenericEventSource[Int]
    val tInner3 = new GenericEventSource[Int]

    val tBuffer = new ArrayBuffer[Int]
    val tEvent = tOuter.flatMap { t =>
      (t % 3) match {
        case 0 => tInner1
        case 1 => tInner2
        case 2 => tInner3
      }
    }

    tEvent.subscribe(tBuffer += _)

    tOuter.emit(0)
    tInner1.emit(11)
    tInner1.emit(12)
    tInner2.emit(21)
    tInner2.emit(22)

    tOuter.emit(1)
    tInner1.emit(13)
    tInner1.emit(14)
    tInner2.emit(23)
    tInner2.emit(24)

    tOuter.emit(2)
    tInner1.emit(15)
    tInner1.emit(16)
    tInner2.emit(25)
    tInner2.emit(26)

    tBuffer.toList should be(List(11, 12, 23, 24))
  }

  "filter" should "create new Event that emit values that satisfy a predicate" in {
    val tSource = new GenericEventSource[Int]
    val tBuffer = new ArrayBuffer[Int]
    val tEvent = tSource.filter(_ % 2 == 0)
    var tIsCompleted = false
    tEvent.subscribe(tBuffer += _, () => tIsCompleted = true)

    tSource.emit(1)
    tSource.emit(2)
    tSource.emit(3)
    tSource.emit(4)
    tSource.emit(5)
    tSource.emit(6)
    tSource.emit(7)
    tSource.emit(8)

    tBuffer.toList should be(List(2, 4, 6, 8))

    tIsCompleted should be(false)
    tSource.complete()
    tIsCompleted should be(true)
  }

  "or" should "create new Event that emit Either values" in {
    val tSource1 = new GenericEventSource[String]
    val tSource2 = new GenericEventSource[Int]
    val tBuffer = new ArrayBuffer[Either[String, Int]]
    val tEvent = tSource1 or tSource2
    var tIsCompleted = false
    tEvent.subscribe(tBuffer += _, () => tIsCompleted = true)

    tSource1.emit("hoge")
    tSource1.emit("fuga")
    tSource2.emit(10)
    tSource1.emit("piyo")
    tSource2.emit(20)
    tSource2.emit(30)

    val tExpected = List(Left("hoge"), Left("fuga"), Right(10), Left("piyo"), Right(20), Right(30))

    tBuffer.toList should be(tExpected)

    tSource1.complete()
    tIsCompleted should be(false)
    tSource2.complete()
    tIsCompleted should be(true)
  }

  "merge" should "create new Event that emit both Event values" in {
    val tSource1 = new GenericEventSource[Int]
    val tSource2 = new GenericEventSource[Int]
    val tBuffer = new ArrayBuffer[Int]
    val tEvent = tSource1 merge tSource2
    tEvent.subscribe(tBuffer += _)

    tSource1.emit(1)
    tSource1.emit(2)
    tSource2.emit(3)
    tSource1.emit(4)
    tSource2.emit(5)
    tSource2.emit(6)

    val tExpected = List(1, 2, 3, 4, 5, 6)
    tBuffer.toList should be(tExpected)
  }

  "zip" should "create new Event that emit Tupled values" in {
    val tSource1 = new GenericEventSource[Int]
    val tSource2 = new GenericEventSource[String]
    val tBuffer = new ArrayBuffer[(Int, String)]
    val tEvent = tSource1 zip tSource2
    var tCompleteCount = 0
    tEvent.subscribe(tBuffer += _, () => tCompleteCount += 1)

    tSource1.emit(1)
    tSource1.emit(2)
    tSource2.emit("hoge")
    tSource1.emit(4)
    tSource2.emit("fuga")

    val tExpected = List((1, "hoge"), (2, "fuga"))
    tBuffer.toList should be(tExpected)

    tSource1.complete()
    tCompleteCount should be(1)
    tSource2.emit("piyo")
    tBuffer.toList should be(tExpected)
    tSource1.complete()
    tSource2.complete()
    tCompleteCount should be(1)
  }

  "sliding" should """create new Event that emit grouped values in fixed size blocks by passing a "sliding window"""" in {
    val tSource = new GenericEventSource[Int]
    val tBuffer = new ArrayBuffer[List[Int]]
    val tEvent = tSource.sliding(3, 1)
    tEvent.subscribe(tBuffer += _)

    tSource.emit(1)
    tSource.emit(2)
    tSource.emit(3)
    tSource.emit(4)
    tSource.emit(5)
    tSource.emit(6)

    val tExpected = List(1, 2, 3, 4, 5, 6).sliding(3).toList
    tBuffer.toList should be(tExpected)
  }

  it should """slide "sliding window" according to argument "step"""" in {
    val tSource = new GenericEventSource[Int]
    val tBuffer = new ArrayBuffer[List[Int]]
    val tEvent = tSource.sliding(2, 3)
    tEvent.subscribe(tBuffer += _)

    tSource.emit(1)
    tSource.emit(2)
    tSource.emit(3)
    tSource.emit(4)
    tSource.emit(5)
    tSource.emit(6)

    val tExpected = List(1, 2, 3, 4, 5, 6).sliding(2, 3).toList
    tBuffer.toList should be(tExpected)
  }

  "group" should "create new Event that emit Partitioned values" in {
    val tSource = new GenericEventSource[Int]
    val tBuffer = new ArrayBuffer[List[Int]]
    val tEvent = tSource.grouped(2)
    tEvent.subscribe(tBuffer += _)

    tSource.emit(1)
    tSource.emit(2)
    tSource.emit(3)
    tSource.emit(4)
    tSource.emit(5)
    tSource.emit(6)
    tSource.emit(7)
    tSource.emit(8)
    tSource.emit(9)

    val tExpected = List(1, 2, 3, 4, 5, 6, 7, 8, 9).grouped(2).filter(_.size == 2).toList
    tBuffer.toList should be(tExpected)
  }

  "take" should "create new Event that emit first n values" in {
    val tSource = new GenericEventSource[Int]
    val tBuffer = new ArrayBuffer[Int]
    val tEvent = tSource.take(5)
    var tCompleted = false
    tEvent.subscribe(tBuffer += _, () => tCompleted = true)

    tSource.emit(1)
    tSource.emit(2)
    tSource.emit(3)
    tSource.emit(4)
    tSource.emit(5)
    tCompleted should be(false)
    tSource.emit(6)
    tCompleted should be(true)
    tSource.emit(7)
    tSource.emit(8)
    tSource.emit(9)

    val tExpected = List(1, 2, 3, 4, 5)
    tBuffer.toList should be(tExpected)
  }

  "takeWhile" should "create new Event that emit longest prefix values that satisfy a predicate" in {
    val tSource = new GenericEventSource[Int]
    val tBuffer = new ArrayBuffer[Int]
    val tEvent = tSource.takeWhile(_ < 6)
    tEvent.subscribe(tBuffer += _)

    tSource.emit(1)
    tSource.emit(2)
    tSource.emit(3)
    tSource.emit(4)
    tSource.emit(5)
    tSource.emit(6)
    tSource.emit(4)
    tSource.emit(3)
    tSource.emit(2)

    val tExpected = List(1, 2, 3, 4, 5)
    tBuffer.toList should be(tExpected)
  }
}