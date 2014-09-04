package jp.seraphr.event

trait Subscriber[-T] {
  def apply(aObj: T) = onNext(aObj)
  def onNext(aObj: T): Unit
  def onComplete(): Unit
  def onFailure(aCause: Throwable): Unit
}

abstract class DelegateSubscriber[T](aUnderlying: Subscriber[_]) extends Subscriber[T] {
  def onComplete(): Unit = aUnderlying.onComplete()
  def onFailure(aCause: Throwable): Unit = aUnderlying.onFailure(aCause)
}

object DelegateSubscriber {
  def apply[T](aUnderlying: Subscriber[_])(f: T => Unit) = new DelegateSubscriber[T](aUnderlying) {
    def onNext(aObj: T): Unit = f(aObj)
  }
}