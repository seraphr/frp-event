package jp.seraphr.event

import scala.collection.mutable.ArrayBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

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
    val tNested = this.map(ev)
    FlattenEvent(tNested)
  }

  def or[U](aEvent: Event[U]): Event[Either[T, U]] = OrEvent(this, aEvent)
  def merge[U >: T](aEvent: Event[U]): Event[U] = MergedEvent(this, aEvent)
  def zip[U](aEvent: Event[U]): Event[(T, U)] = ZippedEvent(this, aEvent)
  def sliding(aSize: Int, aStep: Int = 1): Event[List[T]] = SlidingEvent(this, aSize, aStep)
  def grouped(aSize: Int): Event[List[T]] = GroupedEvent(this, aSize)
  def take(aSize: Int): Event[T] = {
    require(0 < aSize)

    val tAtomicRemain = new AtomicInteger(aSize)
    val p: T => Boolean = t => {
      val tRemain = tAtomicRemain.getAndDecrement()
      0 < tRemain
    }

    takeWhile(p)
  }
  def takeWhile(p: T => Boolean): Event[T] = TakedEvent(this, p)

  def subscribe(f: T => Unit, aOnComplete: () => Unit = () => (), aOnFailure: Throwable => Unit = _ => ()): Observer = this.subscribe(new Subscriber[T] {
    def onNext(aObj: T): Unit = f(aObj)
    def onComplete(): Unit = aOnComplete()
    def onFailure(aCause: Throwable): Unit = aOnFailure(aCause)
  })

  def subscribe[U >: T](aSubscriber: Subscriber[U]): Observer
}

//s => g.onNext(f(s))
case class MappedEvent[S, T](underlying: Event[S], f: S => T) extends Event[T] {
  override def subscribe[U >: T](g: Subscriber[U]): Observer = underlying.subscribe(DelegateSubscriber[S](g) {
    s => g.onNext(f(s))
  })
}

case class FlattenEvent[T](underlying: Event[Event[T]]) extends Event[T] {
  private[this] val mLastObserver = new AtomicReference(None: Option[Observer])
  private[this] val mOuterIsComplete = new AtomicBoolean(false)
  private[this] val mInnerIsComplete = new AtomicBoolean(false)

  override def subscribe[U >: T](g: Subscriber[U]): Observer = underlying.subscribe(new Subscriber[Event[T]] {
    def onNext(aObj: Event[T]): Unit = {
      mInnerIsComplete.set(false)
      val tNewObserver = aObj.subscribe(new Subscriber[U] {
        def onNext(a: U) = g.onNext(a)
        def onComplete(): Unit = {
          mInnerIsComplete.set(true)
          if (mInnerIsComplete.get && mOuterIsComplete.get) {
            g.onComplete()
          }
        }
        def onFailure(aCause: Throwable): Unit = g.onFailure(aCause)
      })

      mLastObserver.getAndSet(Some(tNewObserver)).foreach(_.dispose())

    }

    def onComplete(): Unit = {
      mOuterIsComplete.set(true)
      if (mInnerIsComplete.get && mOuterIsComplete.get) {
        g.onComplete()
      }
    }

    def onFailure(aCause: Throwable): Unit = g.onFailure(aCause)
  })
}

case class FilteredEvent[T](underlying: Event[T], p: T => Boolean) extends Event[T] {
  override def subscribe[U >: T](g: Subscriber[U]): Observer = underlying.subscribe(
    DelegateSubscriber[T](g) { t =>
      if (p(t)) g.onNext(t)
      else ()
    })
}

case class OrEvent[T, U](e1: Event[T], e2: Event[U]) extends Event[Either[T, U]] {
  private[this] val mLeftCompleteEvent = new GenericEventSource[Unit]
  private[this] val mRightCompleteEvent = new GenericEventSource[Unit]

  override def subscribe[V >: Either[T, U]](g: Subscriber[V]): Observer = {
    lazy val tObserver: Observer = (mLeftCompleteEvent zip mRightCompleteEvent).subscribe { _ =>
      tObserver.dispose()
      g.onComplete()
    }

    tObserver

    val tOb1 = e1.subscribe(new DelegateSubscriber[T](g) {
      override def onNext(l: T) = g(Left(l))
      override def onComplete() = mLeftCompleteEvent.emit(())
    })

    val tOb2 = e2.subscribe(new DelegateSubscriber[U](g) {
      override def onNext(r: U) = g(Right(r))
      override def onComplete() = mRightCompleteEvent.emit(())
    })

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
  private[this] val mLeftCompleteEvent = new GenericEventSource[Unit]
  private[this] val mRightCompleteEvent = new GenericEventSource[Unit]

  override def subscribe[U >: T](g: Subscriber[U]): Observer = {
    val tOb1 = e1.subscribe(new DelegateSubscriber[U](g) {
      override def onNext(l: U) = g(l)
      override def onComplete() = {
        mLeftCompleteEvent.emit(())
      }
    })
    val tOb2 = e2.subscribe(new DelegateSubscriber[U](g) {
      override def onNext(r: U) = g(r)
      override def onComplete() = {
        mRightCompleteEvent.emit(())
      }
    })

    lazy val tObserver: Observer = (mLeftCompleteEvent zip mRightCompleteEvent).subscribe { _ =>
      tObserver.dispose()
      g.onComplete()
    }

    tObserver

    new Observer {
      override def isAvailable = tOb1.isAvailable && tOb2.isAvailable
      override def dispose() = {
        tOb1.dispose()
        tOb2.dispose()
      }
    }
  }
}

case class ZippedEvent[L, R](left: Event[L], right: Event[R]) extends Event[(L, R)] {
  import java.util.{ Queue => JQueue }
  private[this] val QUEUE_SIZE = 1024
  private[this] val mIsAvailable = new AtomicBoolean(true)
  private[this] val mSync = new AnyRef

  override def subscribe[U >: (L, R)](f: Subscriber[U]): Observer = {
    val tLeftQueue = new ArrayBlockingQueue[L](QUEUE_SIZE)
    val tRightQueue = new ArrayBlockingQueue[R](QUEUE_SIZE)
    val tOnEnqueue = onEnqueue(tLeftQueue, tRightQueue, f)

    left.subscribe(new DelegateSubscriber[L](f) {
      override def onNext(aObj: L) = {
        if (tLeftQueue.offer(aObj)) {
          tOnEnqueue()
        } else {
          if (mIsAvailable.getAndSet(false))
            f.onFailure(new RuntimeException("Left Queue is Full"))
        }
      }

      override def onComplete() = {
        if (mIsAvailable.getAndSet(false)) {
          f.onComplete()
        }
      }
    })

    right.subscribe(new DelegateSubscriber[R](f) {
      override def onNext(aObj: R) = {
        if (tRightQueue.offer(aObj)) {
          tOnEnqueue()
        } else {
          if (mIsAvailable.getAndSet(false))
            f.onFailure(new RuntimeException("Right Queue is Full"))
        }
      }

      override def onComplete() = {
        if (mIsAvailable.getAndSet(false)) {
          f.onComplete()
        }
      }
    })
  }

  private def onEnqueue[U >: (L, R)](aLeftQueue: JQueue[L], aRightQueue: JQueue[R], f: Subscriber[U]) = () => {
    val tLeftHead = Option(aLeftQueue.peek())
    val tRightHead = Option(aRightQueue.peek())
    if (tLeftHead.isDefined && tRightHead.isDefined && mIsAvailable.get) {
      aLeftQueue.poll()
      aRightQueue.poll()

      f.onNext((tLeftHead.get, tRightHead.get))
    }
  }
}

case class TakedEvent[T](underlying: Event[T], p: T => Boolean) extends Event[T] {
  private[this] val mIsAvailable = new AtomicBoolean(true)

  private def setAvailable(aNew: => Boolean): Boolean = mIsAvailable.getAndSet(aNew)

  override def subscribe[U >: T](f: Subscriber[U]): Observer = underlying.subscribe(t => {
    if (mIsAvailable.get) {
      val tNewValue = p(t)
      val tLastIsAvailable = setAvailable(tNewValue)

      if (tLastIsAvailable) {
        if (tNewValue) {
          f(t)
        } else {
          f.onComplete()
        }
      }
    }
  },
    aOnComplete = () => if (setAvailable(false)) { f.onComplete() },
    aOnFailure = f.onFailure)
}

case class SlidingEvent[T](underlying: Event[T], aSize: Int, aStep: Int) extends Event[List[T]] {
  require(0 < aSize)
  require(0 < aStep)

  private[this] val mSync = new AnyRef

  override def subscribe[U >: List[T]](f: Subscriber[U]): Observer = {
    val tBuffer = ArrayBuffer[T]()
    var tCurrentStep = -aSize
    underlying.subscribe(DelegateSubscriber[T](f) { t =>
      val tList = mSync.synchronized {
        tBuffer += t
        tCurrentStep += 1
        if (0 <= tCurrentStep && tCurrentStep % aStep == 0 && aSize <= tBuffer.size) {
          tBuffer.trimStart(tBuffer.size - aSize)
          tCurrentStep = 0
          Some(tBuffer.toList)
        } else {
          None
        }
      }

      tList.foreach(f.onNext)
    })
  }
}
case class GroupedEvent[T](underlying: Event[T], aSize: Int) extends Event[List[T]] {
  require(0 < aSize)

  private[this] val mSync = new AnyRef

  override def subscribe[U >: List[T]](f: Subscriber[U]): Observer = {
    val tBuffer = ArrayBuffer[T]()
    underlying.subscribe(DelegateSubscriber[T](f) { t =>
      val tList = mSync.synchronized {
        tBuffer += t
        if (tBuffer.size == aSize) {
          val tResult = Some(tBuffer.toList)
          tBuffer.clear()
          tResult
        } else {
          None
        }
      }

      tList.foreach(f.onNext)
    })
  }
}