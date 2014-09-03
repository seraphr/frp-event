package jp.seraphr.event

import scala.collection.mutable.ArrayBuffer

/**
 * イベントを表す何か。
 * 　イベントが発生すると発火し、外に対してT型の値を送出する
 */
trait Event[+T] {
  def map[U](f: T => U): Event[U] = MappedEvent(this, f)
  def flatMap[U](f: T => Event[U]): Event[U] = map(f).flatten
  def foreach(f: T => Unit): Unit = subscribe(f)
  def filter(p: T => Boolean): Event[T] = FilteredEvent(this, p)

  /**
   * Event[Event[T]]を Event[T]に平坦化します。
   * 平坦化したイベントは、最新の内部イベントの値のみが送出されます。
   */
  def flatten[U](implicit ev: T <:< Event[U]): Event[U] = {
    val tNested = this.asInstanceOf[Event[Event[U]]]
    FlattenEvent(tNested)
  }

  def or[U](aEvent: Event[U]): Event[Either[T, U]] = OrEvent(this, aEvent)
  def merge[U >: T](aEvent: Event[U]): Event[U] = MergedEvent(this, aEvent)
  def sliding(aSize: Int, aStep: Int = 1): Event[List[T]] = SlidingEvent(this, aSize, aStep)
  def grouped(aSize: Int): Event[List[T]] = GroupedEvent(this, aSize)
  def take(aSize: Int): Event[T] = {
    require(0 < aSize)

    var tRemain = aSize
    val p: T => Boolean = t => {
      tRemain -= 1
      0 <= tRemain
    }

    takeWhile(p)
  }
  def takeWhile(p: T => Boolean): Event[T] = TakedEvent(this, p)

  def subscribe(f: T => Unit): Observer
}

case class MappedEvent[S, T](underlying: Event[S], f: S => T) extends Event[T] {
  override def subscribe(g: T => Unit): Observer = underlying.subscribe(s => g(f(s)))
}

case class FlattenEvent[T](underlying: Event[Event[T]]) extends Event[T] {
  private[this] var mLastObserver: Option[Observer] = None

  override def subscribe(g: T => Unit): Observer = underlying.subscribe { e =>
    mLastObserver.foreach(_.dispose())
    mLastObserver = Some(e.subscribe(g))
  }
}

case class FilteredEvent[T](underlying: Event[T], p: T => Boolean) extends Event[T] {
  override def subscribe(g: T => Unit): Observer = underlying.subscribe { t =>
    if (p(t)) g(t)
    else ()
  }
}

case class OrEvent[T, U](e1: Event[T], e2: Event[U]) extends Event[Either[T, U]] {
  override def subscribe(g: Either[T, U] => Unit): Observer = {
    val tOb1 = e1.subscribe(l => g(Left(l)))
    val tOb2 = e2.subscribe(r => g(Right(r)))

    new Observer {
      override def isAvailable = tOb1.isAvailable && tOb2.isAvailable
      override def dispose() = {
        tOb1.dispose()
        tOb2.dispose()
      }
    }
  }
}

case class MergedEvent[T](e1: Event[T], e2: Event[T]) extends Event[T] {
  override def subscribe(g: T => Unit): Observer = {
    val tOb1 = e1.subscribe(g)
    val tOb2 = e2.subscribe(g)

    new Observer {
      override def isAvailable = tOb1.isAvailable && tOb2.isAvailable
      override def dispose() = {
        tOb1.dispose()
        tOb2.dispose()
      }
    }
  }
}

case class TakedEvent[T](underlying: Event[T], p: T => Boolean) extends Event[T] {
  @volatile
  private[this] var mIsAvailable = true

  override def subscribe(f: T => Unit): Observer = underlying.subscribe { t =>
    if (mIsAvailable) {
      mIsAvailable = p(t)
      if (mIsAvailable) {
        f(t)
      }
    }
  }
}

case class SlidingEvent[T](underlying: Event[T], aSize: Int, aStep: Int) extends Event[List[T]] {
  require(0 < aSize)
  require(0 < aStep)

  override def subscribe(f: List[T] => Unit): Observer = {
    val tBuffer = ArrayBuffer[T]()
    var tCurrentStep = -aSize
    underlying.subscribe { t =>
      tBuffer += t
      tCurrentStep += 1
      if (0 <= tCurrentStep && tCurrentStep % aStep == 0 && aSize <= tBuffer.size) {
        tBuffer.trimStart(tBuffer.size - aSize)
        f(tBuffer.toList)
        tCurrentStep = 0
      }
    }
  }
}
case class GroupedEvent[T](underlying: Event[T], aSize: Int) extends Event[List[T]] {
  require(0 < aSize)

  override def subscribe(f: List[T] => Unit): Observer = {
    val tBuffer = ArrayBuffer[T]()
    underlying.subscribe { t =>
      tBuffer += t
      if (tBuffer.size == aSize) {
        f(tBuffer.toList)
        tBuffer.clear()
      }
    }
  }
}