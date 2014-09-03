package jp.seraphr.event

import scala.collection.mutable.ListBuffer

trait EventSource[T] {
  def emit(ev: T): Unit
  def event: Event[T]
}

class GenericEventSource[T] extends EventSource[T] with Event[T] {

  private[this] val mSubscribers = new Subscribers[T]

  override def emit(ev: T): Unit = mSubscribers.fire(ev)
  override def event: Event[T] = this

  override def subscribe(aSubscriber: T => Unit): Observer = {
    mSubscribers.add(aSubscriber)

    return new Observer {
      override def isAvailable = mSubscribers.contains(aSubscriber)
      override def dispose(): Unit = mSubscribers.remove(aSubscriber)
    }
  }
}

/**
 * XXX 全てsynchronizedで実装している。　こういう実装をすると、場合により(特にUIスレッドみたいなのが絡むと)デッドロックするので、出来れば避けたい所。
 * 一応sync無しでやる手段はあるけど、めんどくさいのでとりあえずこれで
 */
class Subscribers[T] {
  private[this] val mSubscribers = ListBuffer[T => Unit]()
  private[this] val SYNC = new AnyRef

  def add(f: T => Unit): Unit = SYNC.synchronized {
    mSubscribers += f
  }

  def contains(f: T => Unit): Boolean = SYNC.synchronized {
    mSubscribers.contains(f)
  }

  def remove(f: T => Unit): Unit = SYNC.synchronized {
    mSubscribers -= f
  }

  def fire(aEvent: T): Unit = SYNC.synchronized {
    mSubscribers.foreach(_.apply(aEvent))
  }
}