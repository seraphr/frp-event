package jp.seraphr.event

/**
 * [[Event.subscribe]]すると返ってくる『イベントに登録した動作』を表すもの。
 */
trait Observer {
  /**
   * return true if [[dispose]] is not called
   */
  def isAvailable: Boolean
  def dispose(): Unit
}