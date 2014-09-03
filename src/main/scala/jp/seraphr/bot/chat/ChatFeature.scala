package jp.seraphr.bot.chat

import jp.seraphr.event.Event

trait ChatFeatureComponent {
  self: ChatSystemComponent =>

  type _Feature <: ChatFeature
  trait ChatFeature
}

trait MessageFeatureComponent extends ChatFeatureComponent {
  self: ChatSystemComponent =>

  override type _Feature <: ChatFeature
  trait ChatFeature extends super.ChatFeature {
    def send[T: CanSendTo](aMessage: Message, aTo: T): Unit = implicitly[CanSendTo[T]].send(aMessage, aTo)
    val receiveMessage: Event[Message]
  }

  /**
   * T型がメッセージの送信先になれることを表す型クラス
   * 実質的に、単純な継承で実現可能な構造なんだけど、良い型名思いつかなかった…
   */
  trait CanSendTo[T] {
    def send(aMessage: Message, aTo: T): Unit
  }

  case class Message(sendar: User, text: String)
}