package jp.seraphr.event

import java.util.Timer
import java.util.TimerTask

class TimerEvent[+T](aPeriodMillis: Long, aValue: T) extends Event[T] {
  private[this] val mSource = new GenericEventSource[T]
  private[this] val mTimer = new Timer(true)

  def start(): this.type = {
    val tTask = new TimerTask {
      override def run() {
        mSource.emit(aValue)
      }
    }
    mTimer.schedule(tTask, 0, aPeriodMillis)
    this
  }

  def stop(): this.type = {
    mTimer.cancel()
    this
  }

  override def subscribe(aSubscriber: T => Unit) = mSource.subscribe(aSubscriber)
}